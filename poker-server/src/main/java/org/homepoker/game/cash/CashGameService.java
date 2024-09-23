package org.homepoker.game.cash;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.homepoker.lib.exception.ValidationException;
import org.homepoker.game.*;
import org.homepoker.model.game.*;
import org.homepoker.security.SecurityUtilities;
import org.homepoker.threading.VirtualThreadManager;
import org.homepoker.user.UserManager;
import org.homepoker.utils.DateTimeUtils;
import org.jctools.maps.NonBlockingHashMap;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.springframework.data.mongodb.core.query.Query.query;

@Service
@Slf4j
public class CashGameService {

  private final CashGameRepository gameRepository;
  private final UserManager userManager;
  private final SecurityUtilities securityUtilities;
  private final MongoOperations mongoOperations;
  private final VirtualThreadManager threadManager;
  private final ScheduledFuture<?> gamesScheduler;

  /**
   * This is an atomic boolean that is used to ensure that only one thread is processing the game loop at a time.
   */
  private final AtomicBoolean processLock = new AtomicBoolean(false);

  //I am using a non-blocking variant of ConcurrentHashMap so I can use an atomic computeIfAbsent
  //without blocking the event loop.
  private final Map<String, GameManager<CashGame>> gameManagerMap = new NonBlockingHashMap<>();

  private Instant lastGameCheck = Instant.now().minusSeconds(1);

  public CashGameService(CashGameRepository gameRepository, UserManager userManager, SecurityUtilities securityUtilities,
                         MongoOperations mongoOperations, VirtualThreadManager threadManager) {
    this.gameRepository = gameRepository;
    this.userManager = userManager;
    this.mongoOperations = mongoOperations;
    this.securityUtilities = securityUtilities;
    this.threadManager = threadManager;

    // Set up a scheduled task to run every minute on the "real" clock time.
    ZonedDateTime now = ZonedDateTime.now();
    long initialDelay = Duration.between(now, now.plusSeconds(2).withNano(0)).toMillis();
    gamesScheduler = threadManager.getScheduler().scheduleAtFixedRate(
        this::processGames, initialDelay, 1000, TimeUnit.MILLISECONDS);
  }

  /**
   * Currently we have a top-level scheduler that runs every second. This method will farm out additional threads
   * to process each game to check if new games should be loaded/started.
   */
  void processGames() {

    if (!processLock.compareAndSet(false, true)) {
      // Any additional virtual threads should simply return if there is already a processing thread in progress.
      log.warn("Game processing thread is already running, is it taking too long to process games?");
      return;
    }

    try {
      log.info("Processing Games " + Instant.now());

      if (Instant.now().isAfter(lastGameCheck)) {
        // Spin up a new thread to load any new games that are not yet in memory (we do this once a minute)
        threadManager.getExecutor().submit(this::loadNewGames);
      }

      for (GameManager<CashGame> gameManager : List.copyOf(gameManagerMap.values())) {
        if (gameManager.gameStatus() == GameStatus.COMPLETED) {
          // Remove the game manager from the map if the game is completed.
          gameManagerMap.remove(gameManager.gameId());
        }
        // Spin up a new thread to process each game
        threadManager.getExecutor().submit(gameManager::processGameTick);
      }
    } catch (Exception e) {
      log.error("Error processing games", e);
    } finally {
      // Release the lock
      processLock.set(false);
    }
  }

  protected void loadNewGames() {
    try {
      lastGameCheck = DateTimeUtils.computeNextWallMinute();

      Instant startOfDay = DateTimeUtils.getStartOfDayInCurrentZone();
      Instant endOfDay = startOfDay.plus(Duration.ofDays(1));

      GameCriteria criteria = GameCriteria.builder()
          .statuses(List.of(GameStatus.SCHEDULED, GameStatus.ACTIVE, GameStatus.PAUSED))
          .startTime(startOfDay)
          .endTime(endOfDay)
          .build();
      List<CashGameDetails> games = findGames(criteria);
      log.info("Found {} games scheduled for today.", games.size());
      for (CashGameDetails game : games) {
        getGameManger(game.id());
      }
    } catch (Exception e) {
      log.error("Error loading new games", e);
    }
  }

  @PreDestroy
  public void shutdown() {
    gamesScheduler.cancel(true);
  }

  /**
   * Find cash games that have been persisted.
   *
   * @param criteria The search criteria
   * @return A list of games that match the criteria.
   */
  public List<CashGameDetails> findGames(GameCriteria criteria) {

    if (criteria.statuses() == null && criteria.startTime() == null && criteria.endTime() == null) {
      //No criteria provided, return all games.
      return gameRepository.findAll().stream().map(CashGameService::gameToGameDetails).toList();
    }

    Criteria mongoCriteria = new Criteria();

    if (criteria.statuses() != null) {
      mongoCriteria.and("status").in(criteria.statuses());
    }
    if (criteria.startTime() != null && criteria.endTime() != null) {
      mongoCriteria.and("startTime").gte(criteria.startTime()).lte(criteria.endTime());
    } else if (criteria.startTime() != null) {
      mongoCriteria.and("startTime").gte(criteria.startTime());
    } else if (criteria.endTime() != null) {
      mongoCriteria.and("startTime").lte(criteria.endTime());
    }

    return mongoOperations.query(CashGame.class)
        .matching(query(mongoCriteria))
        .all().stream()
        .map(CashGameService::gameToGameDetails).toList();
  }

  /**
   * Get the game manager for a given gameId.
   *
   * @param gameId The game Id
   * @return A game manager for the game or an error if the game does not exist.
   */
  public GameManager<CashGame> getGameManger(String gameId) {

    return gameManagerMap.computeIfAbsent(gameId,
        (id) -> {
          //If the game manager is not yet in memory, we retrieve the game from
          //the database and materialize the game manager
          CashGame game = gameRepository.findById(gameId).orElseThrow(
              () -> new ValidationException("The cash game [" + gameId + "] does not exist.")
          );
          return new CashGameManager(game, this,  userManager, securityUtilities);
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
    return CashGameService.gameToGameDetails(
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
    return CashGameService.gameToGameDetails(gameRepository.save(applyDetailsToGame(game, details)));
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
    Assert.notNull(gameDetails.type(), "The game type is required when creating a game.");
    Assert.hasText(gameDetails.name(), "The name is required when creating a game.");
    Assert.notNull(gameDetails.maxBuyIn(), "The max buy-in amount is required when creating a game.");
    Assert.notNull(gameDetails.owner(), "The game owner is required when creating a game.");
    Assert.notNull(gameDetails.smallBlind(), "The small blind must be defined for a cash game.");

    //If the start date is not specified or is before the current date, we just default to
    //"now" and immediately transition game to a "paused" state. The owner can then choose when they want to
    //"un-pause" game.
    Instant now = Instant.now();
    Instant startTime = gameDetails.startTime();

    GameStatus status = GameStatus.SCHEDULED;
    if (startTime == null || now.isAfter(startTime)) {
      startTime = now;
      status = GameStatus.PAUSED;
    }

    //Default game type to Texas Hold'em.
    GameType gameType = gameDetails.type();

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
    game = game.withStartTime(startTime);
    game = game.withMaxBuyIn(gameDetails.maxBuyIn());
    game = game.withSmallBlind(gameDetails.smallBlind());
    game = game.withBigBlind(bigBlind);
    game = game.withOwner(gameDetails.owner());

    if (!game.players().containsKey(game.owner().loginId())) {
      Player player = Player.builder().user(game.owner()).confirmed(true).status(PlayerStatus.AWAY).build();
      game = game.withPlayer(player);
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
        .status(game.status())
        .startTime(game.startTime())
        .maxBuyIn(game.maxBuyIn())
        .owner(game.owner())
        .smallBlind(game.smallBlind())
        .bigBlind(game.bigBlind())
        .players(List.copyOf(game.players().values()))
        .build();
  }

  public CashGame saveGame(CashGame game) {
    return gameRepository.save(game);
  }
}
