package org.homepoker.game;

import lombok.extern.slf4j.Slf4j;
import org.homepoker.model.event.SystemError;
import org.homepoker.model.event.user.UserMessage;
import org.homepoker.lib.exception.ValidationException;
import org.homepoker.model.MessageSeverity;
import org.homepoker.model.command.*;
import org.homepoker.model.game.Game;
import org.homepoker.model.game.GameStatus;
import org.homepoker.model.game.Player;
import org.homepoker.model.game.PlayerStatus;
import org.homepoker.security.SecurityUtilities;
import org.homepoker.user.UserManager;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpscLinkedQueue;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public abstract class GameManager<T extends Game<T>> {

  /**
   * This is a non-blocking queue that allows multiple threads to add commands to the queue and a single thread (the game loop thread) that will drain those commands.
   * The contract guarantee (multi-producers, one consumer) is defined by the implementation MpscLinkedQueue and the capacity is unbounded.
   * <p>
   * The game loop thread is the only thread that will drain the commands and manipulate the game state.
   */
  MessagePassingQueue<GameCommand> pendingCommands = new MpscLinkedQueue<>();

  /**
   * For now, using an immutable list of listeners. If there are concerns about performance or a need to find listeners
   * pinned to a specific user, we can use a map for faster lookups.
   */
  private final List<GameListener> gameListeners = List.of();


  /**
   * The current game state that is being managed by this game manager. Changes to the game state are completely
   * encapsulated within the processGameTick method. An atomic boolean is used to ensure that only one game tick is
   * processed at a time.
   */
  private T gameState;

  private final UserManager userManager;
  private final SecurityUtilities securityUtilities;

  /**
   * This is an atomic boolean that is used to ensure that only one game tick is processed at a time.
   */
  private final AtomicBoolean tickLock = new AtomicBoolean(false);

  public GameManager(T gameState, UserManager userManager, SecurityUtilities securityUtilities) {
    this.gameState = gameState;
    this.userManager = userManager;
    this.securityUtilities = securityUtilities;
  }

  @SuppressWarnings("CopyConstructorMissesField")
  protected GameManager(GameManager<T> copy) {
    this.gameState = copy.gameState;
    this.userManager = copy.userManager;
    this.securityUtilities = copy.securityUtilities;
  }

  public void addGameListener(GameListener listener) {
  }
  public void removeGameListener(GameListener listener) {
    // TODO Auto-generated method stub
  }

  public String gameId() {
    return gameState.id();
  }

  public GameStatus gameStatus() {
    return gameState.status();
  }


  protected UserManager getUserManager() {
    return userManager;
  }

  protected SecurityUtilities getSecurityUtilities() {
    return securityUtilities;
  }

  public void submitCommand(GameCommand command) {
    pendingCommands.offer(command);
  }

  public void processGameTick() {

    if (!tickLock.compareAndSet(false, true)) {
      // Any additional virtual threads should simply return if there is already a game tick in progress.
      return;
    }
    try {
      GameContext<T> gameContext = new GameContext<>(gameState, gameSettings());

      // Process queued Commands
      List<GameCommand> commands = new ArrayList<>();
      pendingCommands.drain(commands::add);

      for (GameCommand command : commands) {
        try {
          gameContext = applyCommand(command, gameContext);
        } catch (ValidationException e) {
          gameContext = gameContext.queueEvent(UserMessage.builder()
              .timestamp(Instant.now())
              .userId(command.user().loginId())
              .severity(MessageSeverity.ERROR)
              .message(e.getMessage())
              .build());
        } catch (RuntimeException e) {
          log.error("An error occurred while while processing command [{}].\n{}", command, e.getMessage(), e);
          SystemError.SystemErrorBuilder builder = SystemError.builder()
              .timestamp(Instant.now())
              .gameId(command.gameId())
              .userId(command.user().loginId())
              .exception(e);
          if (command instanceof TableCommand tableCommand) {
            builder.tableId(tableCommand.tableId());
          }
          gameContext = gameContext.queueEvent(builder.build());
        }
      }

      if (gameContext.gameStatus() == GameStatus.ACTIVE || gameContext.gameStatus() == GameStatus.PAUSED) {
        // If the game is active or paused, we need to see if the state of the game/tables has changed based on either
        // the commands that were processed or time passing.

        // TODO Top Level Game Processing
        // TODO ProcessEachTable
      }

      // Update the game state
      this.gameState = gameContext.game();

      if (gameContext.gameStatus() == GameStatus.ACTIVE || gameContext.gameStatus() == GameStatus.PAUSED) {
        // If the game is active or paused, there are active threads firing for each "tick", we want to periodically
        // save the in-memory state of the game to the database.

        if (gameContext.forceUpdate() || gameState.lastModified() == null ||
            gameState.lastModified().plusSeconds(gameSettings().saveIntervalSeconds).isBefore(Instant.now())) {
          gameState = saveGame();
        }
      } else {
        // If the game is not active or paused, we can save the game state to the database immediately.
        gameState = saveGame();
      }

      // Publish events.
      if (!gameListeners.isEmpty()) {
        gameContext.events().forEach(event -> {
          for (GameListener listener : gameListeners) {
            if (listener.acceptsEvent(event)) {
              listener.onEvent(event);
            }
          }
        });
      }
    } finally {
      // Release the lock
      tickLock.set(false);
    }
  }

  /**
   * Save the current in-memory game state to the underlying database. This delegates to each subclass as the
   * persistence mechanism is different for each game type.
   *
   * @return The game state that was saved.
   */
  public final T saveGame() {
    return persistGameState(gameState);
  }

  protected GameSettings gameSettings() {
    return GameSettings.DEFAULT;
  }
  protected abstract T persistGameState(T game);

  protected final GameContext<T> applyCommand(GameCommand command, GameContext<T> gameContext) {

    return switch (command) {
      case RegisterForGame gameCommand -> registerForGame(gameCommand, gameContext);
      case UnregisterFromGame gameCommand -> unregisterFromGame(gameCommand, gameContext);
      case EndGame gameCommand -> endGame(gameCommand, gameContext);
      default ->
        // Allow the subclass to handle any commands that are specific to child game manager.
          applyGameSpecificCommand(command, gameContext);
    };
  }

  private GameContext<T> registerForGame(RegisterForGame registerForGame, GameContext<T> gameContext) {

    if (gameContext.gameStatus() == GameStatus.COMPLETED) {
      throw new ValidationException("This game has already completed.");
    }

    T game = gameContext.game();
    if (game.players().containsKey(registerForGame.user().loginId())) {
      throw new ValidationException("You are already registered for this game.");
    }
    Map<String, Player> players = new HashMap<>(game.players());
    players.put(registerForGame.user().loginId(), Player.builder()
        .user(registerForGame.user())
        .status(PlayerStatus.REGISTERED)
        .build());

    return gameContext.withGame(game.withPlayers(players)).withForceUpdate(true);
  }

  private GameContext<T> unregisterFromGame(UnregisterFromGame registerForGame, GameContext<T> gameContext) {

    if (gameContext.gameStatus() != GameStatus.SCHEDULED) {
      throw new ValidationException("You can only unregister from the game prior to it starting.");
    }

    T game = gameContext.game();

    if (!game.players().containsKey(registerForGame.user().loginId())) {
      throw new ValidationException("You are not registered for this game.");
    }

    Map<String, Player> players = new HashMap<>(game.players());
    players.remove(registerForGame.user().loginId());
    return gameContext.withGame(game.withPlayers(players)).withForceUpdate(true);

  }

  private GameContext<T> endGame(EndGame gameCommand, GameContext<T> gameContext) {
    if (gameContext.gameStatus() == GameStatus.COMPLETED) {
      throw new ValidationException("This game has already completed.");
    }
    if (SecurityUtilities.userIsAdmin(gameCommand.user())) {
      return gameContext.withGame(gameContext.game().withStatus(GameStatus.COMPLETED));
    } else {
      throw new ValidationException("Only an admin can end a game.");
    }
  }

  protected GameContext<T> applyGameSpecificCommand(GameCommand command, GameContext<T> gameContext) {
    throw new ValidationException("Unknown command: " + command.commandId());
  }

}
