package org.homepoker.game.cash;

import org.homepoker.lib.exception.ValidationException;
import org.homepoker.game.*;
import org.homepoker.model.game.*;
import org.homepoker.user.UserManager;
import org.jctools.maps.NonBlockingHashMap;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.data.mongodb.core.query.Query.query;

@Service
public class CashGameServer {

  private final CashGameRepository gameRepository;
  private final UserManager userManager;
  private final MongoOperations mongoOperations;

  //I am using a non-blocking variant of ConcurrentHashMap so I can use an atomic computeIfAbsent
  //without blocking the event loop.
  private final Map<String, GameManager<CashGame>> gameManagerMap = new NonBlockingHashMap<>();

  public CashGameServer(CashGameRepository gameRepository, UserManager userManager, MongoOperations mongoOperations) {
    this.gameRepository = gameRepository;
    this.userManager = userManager;
    this.mongoOperations = mongoOperations;
  }

  /**
   * Find cash games that have been persisted.
   *
   * @param criteria The search criteria
   * @return A list of games that match the criteria.
   */
  public List<CashGameDetails> findGames(GameCriteria criteria) {

    if (criteria == null ||
        (criteria.status() == null && criteria.startDate() == null && criteria.endDate() == null)) {
      //No criteria provided, return all games.
      return gameRepository.findAll().stream().map(CashGameServer::gameToGameDetails).toList();
    }

    Criteria mongoCriteria = new Criteria();

    if (criteria.status() != null) {
      mongoCriteria.and("status").is(criteria.status());
    }
    if (criteria.startDate() != null) {
      mongoCriteria.and("startTimestamp").gte(criteria.startDate());
    }
    if (criteria.endDate() != null) {
      //The end date is intended to include any timestamp in that day, we just add one to the
      //day to insure we get all games on the end date.
      mongoCriteria.and("endTimestamp").lte(criteria.endDate().plus(1, ChronoUnit.DAYS));
    }

    return mongoOperations.query(CashGame.class)
        .matching(query(mongoCriteria))
        .all().stream()
        .map(CashGameServer::gameToGameDetails).toList();
  }

  /**
   * Get the game manager for a given gameId.
   *
   * @param gameId The game Id
   * @return A game manager for the game or an error if the game does not exist.
   */
  public GameManager<CashGame> getGameManger(String gameId) {
    //There is a map of eagerly fetched Mono<GameManager> instances. If the game manager is
    //present we pass a new mono to the subscriber (via the defer)

    return gameManagerMap.computeIfAbsent(gameId,
        (id) -> {
          //If the game manager is not yet in memory, we retrieve the game from
          //the database and materialize the game manager
          return new CashGameManager(gameRepository.findById(gameId)
              .orElseThrow(() -> new ValidationException("The cash game [" + gameId + "] does not exist.")));
        });
  }

  /**
   * Create/schedule a new game.
   * <p>
   * The game state passed into this method will be validated.
   *
   * @param gameDetails The passed in game state is used to persist a new game.
   * @return The fully persisted game state. If the game's start date is past the current time-stamp, it will be marked as "PAUSED"
   * @throws ValidationException If a validation error occurs
   */
  public CashGameDetails createGame(CashGameDetails gameDetails) {
    return CashGameServer.gameToGameDetails(
        gameRepository.save(applyDetailsToGame(CashGame.builder().build(), gameDetails))
    );
  }

  /**
   * Update an existing game.
   *
   * @param details The game details to be applied to the game.
   * @return The updated game state.
   * @throws ValidationException If the game does not exist or a validation error occurs
   */
  public CashGameDetails updateGameDetails(final CashGameDetails details) {

    CashGame game = gameRepository.findById(details.id()).orElseThrow(
        () -> new ValidationException("The cash game [" + details.id() + "] does not exist.")
    );

    //Find the game by ID
    return CashGameServer.gameToGameDetails(gameRepository.save(applyDetailsToGame(game, details)));
  }

  /**
   * Retrieve the game details for an existing game.
   *
   * @param gameId The Id of the game.
   * @return The game details or an error if the game does not exist.
   * @throws ValidationException If the game does not exist or a validation error occurs
   */
  public CashGameDetails getGameDetails(String gameId) {
    return gameToGameDetails(
        gameRepository.findById(gameId).orElseThrow(
            () -> new ValidationException("The cash game [" + gameId + "] does not exist.")
        )
    );
  }

  /**
   * Delete an existing game.
   *
   * @param gameId The ID of the game
   * @throws ValidationException If the game cannot be deleted.
   */
  public void deleteGame(String gameId) {
    gameRepository.deleteById(gameId);
  }

  /**
   * This method will apply the game details to the game and return a mono for the cash game.
   *
   * @param game        The game that will have the details applied to it.
   * @param gameDetails The game details.
   * @return A mono of the CashGame
   */
  private CashGame applyDetailsToGame(CashGame game, CashGameDetails gameDetails) {

    if (game.status() != GameStatus.SCHEDULED) {
      throw new ValidationException("You can only update the details of the game prior to it starting");
    }
    Assert.notNull(gameDetails.name(), "The name is required when creating a game.");
    Assert.notNull(gameDetails.maxBuyIn(), "The max buy-in amount is required when creating a game.");
    Assert.notNull(gameDetails.owner(), "The game owner is required when creating a game.");
    Assert.notNull(gameDetails.smallBlind(), "The small blind must be defined for a cash game.");

    //If the start date is not specified or is before the current date, we just default to
    //"now" and immediately transition game to a "paused" state. The owner can then choose when they want to
    //"un-pause" game.
    Instant now = Instant.now();
    Instant startTimestamp = gameDetails.startTimestamp();

    GameStatus status = GameStatus.SCHEDULED;
    if (startTimestamp == null || now.isAfter(startTimestamp)) {
      startTimestamp = now;
      status = GameStatus.PAUSED;
    }

    //Default game type to Texas Hold'em.
    GameType gameType = gameDetails.type();
    if (gameDetails.type() == null) {
      gameType = GameType.TEXAS_HOLDEM;
    }

    //If big blind is not explicitly passed in, we just double the small blind.
    int bigBlind = gameDetails.smallBlind() * 2;
    if (gameDetails.bigBlind() != null) {
      bigBlind = gameDetails.bigBlind();
      if (bigBlind <= gameDetails.smallBlind()) {
        throw new ValidationException("The big blind must be larger then the small blind. Typically it should be double the small blind.");
      }
    }

    game = game.withName(gameDetails.name());
    game = game.withType(gameType);
    game = game.withStatus(status);
    game = game.withStartTimestamp(startTimestamp);
    game = game.withMaxBuyIn(gameDetails.maxBuyIn());
    game = game.withSmallBlind(gameDetails.smallBlind());
    game = game.withBigBlind(bigBlind);
    game = game.withOwner(gameDetails.owner());

    if (game.players() == null) {
      game = game.withPlayers(new HashMap<>());
    }
    if (!game.players().containsKey(game.owner().loginId())) {
      Player player = Player.builder().user(game.owner()).confirmed(true).status(PlayerStatus.AWAY).build();
      game.players().put(game.owner().loginId(), player);
    }
    return game;
  }

  /**
   * Method to convert a cash game into a cash game details.
   *
   * @param game The cash game
   * @return The details for the cash game.
   */
  private static CashGameDetails gameToGameDetails(CashGame game) {
    return CashGameDetails.builder()
        .id(game.id())
        .name(game.name())
        .type(game.type())
        .startTimestamp(game.startTimestamp())
        .maxBuyIn(game.maxBuyIn())
        .owner(game.owner())
        .smallBlind(game.smallBlind())
        .bigBlind(game.bigBlind())
        .numberOfPlayers(game.players() == null ? 0 : game.players().size())
        .build();
  }

}
