package org.homepoker.game.tournament;

import org.homepoker.common.ValidationException;
import org.homepoker.game.GameCriteria;
import org.homepoker.game.GameManager;
import org.homepoker.game.GameStatus;
import org.homepoker.game.GameType;
import org.homepoker.user.UserManager;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

import static org.springframework.data.mongodb.core.query.Query.query;

@Service
public class TournamentGameServerImpl implements TournamentGameServer {

  private final TournamentGameRepository gameRepository;
  private final UserManager userManager;
  private final MongoOperations mongoOperations;

  public TournamentGameServerImpl(TournamentGameRepository gameRepository, UserManager userManager, MongoOperations mongoOperations) {
    this.gameRepository = gameRepository;
    this.userManager = userManager;
    this.mongoOperations = mongoOperations;
  }

  @Override
  public List<TournamentGameDetails> findGames(GameCriteria criteria) {
    if (criteria == null ||
        (criteria.status() == null && criteria.startDate() == null && criteria.endDate() == null)) {
      //No criteria provided, return all games.
      return gameRepository.findAll().stream().map(TournamentGameServerImpl::gameToGameDetails).toList();
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

    return mongoOperations.query(TournamentGame.class)
        .matching(query(mongoCriteria)).all()
        .stream().map(TournamentGameServerImpl::gameToGameDetails).toList();
  }

  @Override
  public GameManager getGameManger(String gameId) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public TournamentGameDetails createGame(TournamentGameDetails gameDetails) {

    //Create a new tournament game and create a pipeline to apply the game details.
    TournamentGame game = gameRepository.save(applyDetailsToGame(TournamentGame.builder().build(), gameDetails));
    return TournamentGameServerImpl.gameToGameDetails(game);
  }

  @Override
  public TournamentGameDetails updateGame(final TournamentGameDetails details) {

    TournamentGame game = gameRepository.findById(details.getId()).orElseThrow(
        () -> new ValidationException("The game [" + details.getId() + "] does not exist.")
    );
    return TournamentGameServerImpl.gameToGameDetails(gameRepository.save(applyDetailsToGame(game, details)));
  }

  @Override
  public TournamentGameDetails getGame(String gameId) {
    TournamentGame game = gameRepository.findById(gameId).orElseThrow(
        () -> new ValidationException("The game [" + gameId + "] does not exist.")
    );
    return TournamentGameServerImpl.gameToGameDetails(game);
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
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime startTimestamp = gameDetails.getStartTimestamp();

    GameStatus status = GameStatus.SCHEDULED;
    if (startTimestamp == null || startTimestamp.isAfter(now)) {
      startTimestamp = now;
      status = GameStatus.PAUSED;
    }

    //Default game type to Texas Hold'em.
    GameType gameType = gameDetails.getGameType();
    if (gameDetails.getGameType() == null) {
      gameType = GameType.TEXAS_HOLDEM;
    }

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
    BigDecimal rebuyAmount = null;

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
    BigDecimal addOnAmount = null;

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

    game.setName(gameDetails.getName());
    game.setGameType(gameType);
    game.setStatus(status);
    game.setStartTimestamp(startTimestamp);
    game.setBuyInChips(gameDetails.getBuyInChips());
    game.setBuyInAmount(gameDetails.getBuyInAmount());
    game.setBlindIntervalMinutes(blindIntervalMinutes);
    game.setNumberOfRebuys(numberOfRebuys);
    game.setRebuyChips(rebuyChips);
    game.setRebuyAmount(rebuyAmount);
    game.setAddOnAllowed(addOnsAllowed);
    game.setAddOnChips(addOnChips);
    game.setAddOnAmount(addOnAmount);
    game.setCliffLevel(cliffLevel);
    game.setOwner(userManager.getUser(gameDetails.getOwnerLoginId()));
    if (game.getPlayers() == null) {
      game.setPlayers(new HashMap<>());
    }
    return game;
  }

  private static TournamentGameDetails gameToGameDetails(TournamentGame game) {
    return TournamentGameDetails.builder()
        .id(game.getId())
        .name(game.getName())
        .gameType(game.getGameType())
        .startTimestamp(game.getStartTimestamp())
        .ownerLoginId(game.getOwner().getLoginId())
        .buyInChips(game.getBuyInChips())
        .buyInAmount(game.getBuyInAmount())
        .estimatedTournamentLengthHours(game.getEstimatedTournamentLengthHours())
        .blindIntervalMinutes(game.getBlindIntervalMinutes())
        .numberOfRebuys(game.getNumberOfRebuys())
        .rebuyChips(game.getRebuyChips())
        .rebuyAmount(game.getRebuyAmount())
        .addOnAllowed(game.isAddOnAllowed())
        .addOnChips(game.getAddOnChips())
        .addOnAmount(game.getAddOnAmount())
        .cliffLevel(game.getCliffLevel())
        .numberOfPlayers(game.getPlayers() == null ? 0 : game.getPlayers().size())
        .build();
  }
}
