package org.homepoker.game.tournament;

import org.homepoker.lib.exception.ValidationException;
import org.homepoker.model.game.GameCriteria;
import org.homepoker.game.GameManager;
import org.homepoker.model.game.GameStatus;
import org.homepoker.model.game.GameType;
import org.homepoker.user.UserManager;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.util.Assert;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;

import static org.springframework.data.mongodb.core.query.Query.query;

public class TournamentGameServer {

  private final TournamentGameRepository gameRepository;
  private final UserManager userManager;
  private final MongoOperations mongoOperations;

  public TournamentGameServer(TournamentGameRepository gameRepository, UserManager userManager, MongoOperations mongoOperations) {
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
  public List<TournamentGameDetails> findGames(GameCriteria criteria) {
    if (criteria.statuses() == null && criteria.startTime() == null && criteria.endTime() == null) {
      //No criteria provided, return all games.
      return gameRepository.findAll().stream().map(TournamentGameServer::gameToGameDetails).toList();
    }

    Criteria mongoCriteria = new Criteria();

    if (criteria.statuses() != null) {
      mongoCriteria.and("status").in(criteria.statuses());
    }
    if (criteria.startTime() != null) {
      mongoCriteria.and("startTime").gte(criteria.startTime());
    }
    if (criteria.endTime() != null) {
      //The end date is intended to include any timestamp in that day, we just add one to the
      //day to insure we get all games on the end date.
      mongoCriteria.and("endTime").lte(criteria.endTime().plus(1, ChronoUnit.DAYS));
    }

    return mongoOperations.query(TournamentGame.class)
        .matching(query(mongoCriteria)).all()
        .stream().map(TournamentGameServer::gameToGameDetails).toList();
  }

  /**
   * Get the game manager for a given gameId.
   *
   * @param gameId The game ID
   * @return The game manager
   */
  public GameManager<TournamentGame> getGameManger(String gameId) {
    // TODO Auto-generated method stub
    return null;
  }

  /**
   * Create/schedule a new game.
   * <p>
   * The game state passed into this method will be validated.
   *
   * @param gameDetails The game details to be used to persist a new game.
   * @return The fully persisted game state. If the game's start date is past the current time-stamp, it will be marked as "PAUSED"
   * @throws ValidationException If a validation error occurs
   */
  public TournamentGameDetails createGame(TournamentGameDetails gameDetails) {

    //Create a new tournament game and create a pipeline to apply the game details.
    TournamentGame game = gameRepository.save(applyDetailsToGame(TournamentGame.builder().build(), gameDetails));
    return TournamentGameServer.gameToGameDetails(game);
  }

  /**
   * Update an existing game.
   *
   * @param details The game details to be applied to the game.
   * @return The updated game state or an error if the game does not exist or there is a validation error.
   * @throws ValidationException If the game does not exist or a validation error occurs
   */
  public TournamentGameDetails updateGame(final TournamentGameDetails details) {

    TournamentGame game = gameRepository.findById(details.getId()).orElseThrow(
        () -> new ValidationException("The game [" + details.getId() + "] does not exist.")
    );
    return TournamentGameServer.gameToGameDetails(gameRepository.save(applyDetailsToGame(game, details)));
  }


  /**
   * Retrieve the game details for an existing game.
   *
   * @param gameId The Id of the game.
   * @return The game details or an error if the game does not exist.
   * @throws ValidationException If the game does not exist or a validation error occurs
   */
  public TournamentGameDetails getGame(String gameId) {
    TournamentGame game = gameRepository.findById(gameId).orElseThrow(
        () -> new ValidationException("The game [" + gameId + "] does not exist.")
    );
    return TournamentGameServer.gameToGameDetails(game);
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
  private TournamentGame applyDetailsToGame(TournamentGame game, TournamentGameDetails gameDetails) {
    Assert.notNull(gameDetails, "The game configuration is required.");
    Assert.notNull(gameDetails.getName(), "The name is required when creating a game.");
    Assert.notNull(gameDetails.getGameType(), "The game type is required when creating a game.");
    Assert.notNull(gameDetails.getBuyInChips(), "The buy-in chip stack size is required when creating a game.");
    Assert.notNull(gameDetails.getBuyInAmount(), "The buy-in amount is required when creating a game.");
    Assert.notNull(gameDetails.getOwnerLoginId(), "The game owner is required when creating a game.");

    //If the a start date is not specified or is before the current date, we just default to
    //"now" and immediately transition game to a "paused" state. The owner can then choose when they want to
    //"un-pause" game.
    Instant now = Instant.now();
    Instant startTimestamp = gameDetails.getStartTimestamp();

    GameStatus status = GameStatus.SCHEDULED;
    if (startTimestamp.isAfter(now)) {
      startTimestamp = now;
      status = GameStatus.PAUSED;
    }

    //Default game type to Texas Hold'em.
    GameType gameType = gameDetails.getGameType();

    //If blind intervals is not explicitly set, default to 15 minutes.
    int blindIntervalMinutes = 15;
    if (gameDetails.getBlindIntervalMinutes() != null) {
      blindIntervalMinutes = gameDetails.getBlindIntervalMinutes();
    }

    //Re-buys are "enabled" if number of re-buys is greater then 0.
    //If a re-buy chip amount is not provided, we default it to the buy-in chip amount.
    //If a re-buy amount is not provided, we default it to the buy amount.
    int numberOfRebuys = 0;
    Integer rebuyChips = null;
    Integer rebuyAmount = null;

    if (gameDetails.getNumberOfRebuys() != null) {
      numberOfRebuys = gameDetails.getNumberOfRebuys();
      if (numberOfRebuys > 0) {
        rebuyChips = gameDetails.getRebuyChips();
        if (rebuyChips == null) {
          rebuyChips = gameDetails.getBuyInChips();
        }
        rebuyAmount = gameDetails.getRebuyAmount();
        if (rebuyAmount == null) {
          rebuyAmount = gameDetails.getBuyInAmount();
        }
      }
    }

    //If add-ons are "enabled":
    //  If a add-on chip amount is not provided, we default it to the buy-in chip amount.
    //  If a add-on amount is not provided, we default it to the buy amount.
    boolean addOnsAllowed = gameDetails.isAddOnAllowed();
    Integer addOnChips = null;
    Integer addOnAmount = null;

    if (addOnsAllowed) {
      addOnChips = gameDetails.getAddOnChips();
      if (addOnChips == null) {
        addOnChips = gameDetails.getBuyInChips();
      }
      addOnAmount = gameDetails.getAddOnAmount();
      if (addOnAmount == null) {
        addOnAmount = gameDetails.getBuyInAmount();
      }
    }

    //We need a cliff where the re-buys are no longer allowed and also the point where
    //add-ons are applied. If the game has re-buys OR add-ons and a cliff has not been defined,
    //we default to 4.
    //So if the blind interval is 15 minutes: the cliff is applied on the 4th blind level (1 hour into the tournament)
    int cliffLevel = 0;
    if (numberOfRebuys > 0 || addOnsAllowed) {
      cliffLevel = gameDetails.getCliffLevel() != null ? gameDetails.getCliffLevel() : 4;
    }

    game
        .name(gameDetails.getName())
        .type(gameType)
        .status(status)
        .startTime(startTimestamp)
        .buyInChips(gameDetails.getBuyInChips())
        .buyInAmount(gameDetails.getBuyInAmount())
        .blindIntervalMinutes(blindIntervalMinutes)
        .numberOfRebuys(numberOfRebuys)
        .rebuyChips(rebuyChips)
        .rebuyAmount(rebuyAmount)
        .addOnAllowed(addOnsAllowed)
        .addOnChips(addOnChips)
        .addOnAmount(addOnAmount)
        .cliffLevel(cliffLevel)
        .owner(userManager.getUser(gameDetails.getOwnerLoginId()));
    if (game.players() == null) {
      game.players(new HashMap<>());
    }
    return game;
  }

  private static TournamentGameDetails gameToGameDetails(TournamentGame game) {
    return TournamentGameDetails.builder()
        .id(game.id())
        .name(game.name())
        .gameType(game.type())
        .startTimestamp(game.startTime())
        .ownerLoginId(game.owner().loginId())
        .buyInChips(game.buyInChips())
        .buyInAmount(game.buyInAmount())
        .estimatedTournamentLengthHours(game.estimatedTournamentLengthHours())
        .blindIntervalMinutes(game.blindIntervalMinutes())
        .numberOfRebuys(game.numberOfRebuys())
        .rebuyChips(game.rebuyChips())
        .rebuyAmount(game.rebuyAmount())
        .addOnAllowed(game.addOnAllowed())
        .addOnChips(game.addOnChips())
        .addOnAmount(game.addOnAmount())
        .cliffLevel(game.cliffLevel())
        .numberOfPlayers(game.players() == null ? 0 : game.players().size())
        .build();
  }

}
