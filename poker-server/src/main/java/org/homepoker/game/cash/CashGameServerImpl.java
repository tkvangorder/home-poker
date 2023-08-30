package org.homepoker.game.cash;

import org.homepoker.common.ValidationException;
import org.homepoker.game.*;
import org.homepoker.user.UserManager;
import org.jctools.maps.NonBlockingHashMap;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.data.mongodb.core.query.Query.query;

@Service
public class CashGameServerImpl implements CashGameServer {

  private final CashGameRepository gameRepository;
  private final UserManager userManager;
  private final MongoOperations mongoOperations;

  //I am using a non-blocking variant of ConcurrentHashMap so I can use an atomic computeIfAbsent
  //without blocking the event loop.
  private final Map<String, GameManager<CashGame>> gameManagerMap = new NonBlockingHashMap<>();

  public CashGameServerImpl(CashGameRepository gameRepository, UserManager userManager, MongoOperations mongoOperations) {
    this.gameRepository = gameRepository;
    this.userManager = userManager;
    this.mongoOperations = mongoOperations;
  }

  @Override
  public List<CashGameDetails> findGames(GameCriteria criteria) {

    if (criteria == null ||
        (criteria.status() == null && criteria.startDate() == null && criteria.endDate() == null)) {
      //No criteria provided, return all games.
      return gameRepository.findAll().stream().map(CashGameServerImpl::gameToGameDetails).toList();
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
      mongoCriteria.and("endTimestamp").lte(criteria.endDate().plusDays(1));
    }

    return mongoOperations.query(CashGame.class)
        .matching(query(mongoCriteria))
        .all().stream()
        .map(CashGameServerImpl::gameToGameDetails).toList();
  }

  @Override
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

  @Override
  public CashGameDetails createGame(CashGameDetails gameDetails) {
    return CashGameServerImpl.gameToGameDetails(
        gameRepository.save(applyDetailsToGame(CashGame.builder().build(), gameDetails))
    );
  }

  @Override
  public CashGameDetails updateGameDetails(final CashGameDetails details) {

    CashGame game = gameRepository.findById(details.getId()).orElseThrow(
        () -> new ValidationException("The cash game [" + details.getId() + "] does not exist.")
    );

    //Find the game by ID
    return CashGameServerImpl.gameToGameDetails(gameRepository.save(applyDetailsToGame(game, details)));
  }

  @Override
  public CashGameDetails getGameDetails(String gameId) {
    return gameToGameDetails(
        gameRepository.findById(gameId).orElseThrow(
            () -> new ValidationException("The cash game [" + gameId + "] does not exist.")
        )
    );
  }

  @Override
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

    if (game.getStatus() != GameStatus.SCHEDULED) {
      throw new ValidationException("You can only update the details of the game prior to it starting");
    }
    Assert.notNull(gameDetails.getName(), "The name is required when creating a game.");
    Assert.notNull(gameDetails.getBuyInChips(), "The buy-in chip stack size is required when creating a game.");
    Assert.notNull(gameDetails.getBuyInAmount(), "The buy-in amount is required when creating a game.");
    Assert.notNull(gameDetails.getOwnerLoginId(), "The game owner is required when creating a game.");
    Assert.notNull(gameDetails.getSmallBlind(), "The small blind must be defined for a cash game.");

    //If the a start date is not specified or is before the current date, we just default to
    //"now" and immediately transition game to a "paused" state. The owner can then choose when they want to
    //"un-pause" game.
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime startTimestamp = gameDetails.getStartTimestamp();

    GameStatus status = GameStatus.SCHEDULED;
    if (startTimestamp == null || now.isAfter(startTimestamp)) {
      startTimestamp = now;
      status = GameStatus.PAUSED;
    }

    //Default game type to Texas Hold'em.
    GameType gameType = gameDetails.getGameType();
    if (gameDetails.getGameType() == null) {
      gameType = GameType.TEXAS_HOLDEM;
    }

    //If big blind is not explicitly passed in, we just double the small blind.
    int bigBlind = gameDetails.getSmallBlind() * 2;
    if (gameDetails.getBigBlind() != null) {
      bigBlind = gameDetails.getBigBlind();
      if (bigBlind <= gameDetails.getSmallBlind()) {
        throw new ValidationException("The big blind must be larger then the small blind. Typically it should be double the small blind.");
      }
    }

    game.setName(gameDetails.getName());
    game.setGameType(gameType);
    game.setStatus(status);
    game.setStartTimestamp(startTimestamp);
    game.setBuyInChips(gameDetails.getBuyInChips());
    game.setBuyInAmount(gameDetails.getBuyInAmount());
    game.setSmallBlind(gameDetails.getSmallBlind());
    game.setBigBlind(bigBlind);
    game.setOwner(userManager.getUser(gameDetails.getOwnerLoginId()));

    if (game.getPlayers() == null) {
      game.setPlayers(new HashMap<>());
    }
    if (!game.getPlayers().containsKey(game.getOwner().getLoginId())) {
      Player player = Player.builder().user(game.getOwner()).confirmed(true).status(PlayerStatus.AWAY).build();
      game.getPlayers().put(game.getOwner().getLoginId(), player);
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
        .id(game.getId())
        .name(game.getName())
        .gameType(game.getGameType())
        .startTimestamp(game.getStartTimestamp())
        .buyInChips(game.getBuyInChips())
        .buyInAmount(game.getBuyInAmount())
        .ownerLoginId(game.getOwner().getLoginId())
        .smallBlind(game.getSmallBlind())
        .bigBlind(game.getBigBlind())
        .numberOfPlayers(game.getPlayers() == null ? 0 : game.getPlayers().size())
        .build();
  }

}
