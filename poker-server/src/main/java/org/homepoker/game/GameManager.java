package org.homepoker.game;

import lombok.extern.slf4j.Slf4j;
import org.homepoker.event.UserMessage;
import org.homepoker.lib.exception.ValidationException;
import org.homepoker.model.MessageSeverity;
import org.homepoker.model.command.GameCommand;
import org.homepoker.model.command.RegisterForGame;
import org.homepoker.model.command.UnregisterFromGame;
import org.homepoker.model.game.GameStatus;
import org.homepoker.model.game.Player;
import org.homepoker.model.game.PlayerStatus;
import org.homepoker.model.user.User;
import org.jctools.maps.NonBlockingHashMap;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpscLinkedQueue;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public abstract class GameManager<T extends Game> {

  // The interval at which an active game's in-memory state is saved to the underlying database.
  private static final int SAVE_INTERVAL_SECONDS = 5;

  /**
   * This is a non-blocking queue that allows multiple threads to add commands to the queue and a single thread (the game loop thread) that will drain those commands.
   * The contract guarantee (multi-producers, one consumer) is defined by the implementation MpscLinkedQueue and the capacity is unbounded.
   * <p>
   * The game loop thread is the only thread that will drain the commands and manipulate the game state.
   */
  MessagePassingQueue<GameCommand> pendingCommands = new MpscLinkedQueue<>();

  /**
   * This is a map of user ID -> game listener registered for that user.
   */
  private final Map<String, GameListener> gameListeners = new NonBlockingHashMap<>();

  /**
   * The current game state that is being managed by this game manager. Changes to the game state are completely
   * encapsulated within the processGameTick method. An atomic boolean is used to ensure that only one game tick is
   * processed at a time.
   */
  private T gameState;

  /**
   * This is an atomic boolean that is used to ensure that only one game tick is processed at a time.
   */
  private final AtomicBoolean tickLock = new AtomicBoolean(false);

  public GameManager(T gameState) {
    this.gameState = gameState;
  }

  public Optional<GameListener> getGameListener(User user) {
    return Optional.ofNullable(gameListeners.get(user.loginId()));
  }

  public void addGameListener(GameListener listener) {
  }

  public void removeGameListener(GameListener listener) {
    // TODO Auto-generated method stub
  }

  public void submitCommand(GameCommand command) {
    pendingCommands.offer(command);
  }

  public void processGameTick() {

    if (tickLock.compareAndSet(false, true)) {
      // Any additional virtual threads should simply return if there is already a game tick in progress.
      return;
    }

    GameContext<T> gameContext = new GameContext<>(gameState);

    // Process queued Commands
    List<GameCommand> commands = new ArrayList<>();
    pendingCommands.drain(commands::add);

    for (GameCommand command : commands) {
      try {
        gameContext = applyCommand(command, gameContext);
      } catch (ValidationException e) {
        if (gameContext.gameStatus() == GameStatus.SCHEDULED) {
          // This can happen if a user is attempting to register/unregister from a game that is not active. The
          // processGameTick is called on the same thread as the RestController and we want to bubble that to the
          // client.
          throw e;
        }
        gameContext = gameContext.queueEvent(UserMessage.builder()
            .userId(command.user().loginId())
            .severity(MessageSeverity.ERROR)
            .message(e.getMessage())
            .build());
      } catch (Exception e) {
        log.error("An error occurred while while processing command [" + command + "].\n" + e.getMessage(), e);
        // TODO It might be useful to emit an event here that can be broadcast to listeners that are interested in such things.
      }
    }

    if (gameContext.gameStatus() == GameStatus.ACTIVE || gameContext.gameStatus() == GameStatus.PAUSED) {
      // If the game is active or paused, we need to see if the state of the game/tables has changed based on either
      // the commands that were processed or time passing.

      // TODO Top Level Game Processing
      // TODO ProcessEachTable
    }

    // TODO Process Events
    this.gameState = gameContext.game();

    if (gameContext.gameStatus() == GameStatus.ACTIVE || gameContext.gameStatus() == GameStatus.PAUSED) {
      // If the game is active or paused, there are active threads firing for each "tick", we want to periodically
      // save the in-memory state of the game to the database.

      if (gameState.lastModified() == null || gameState.lastModified().plusSeconds(SAVE_INTERVAL_SECONDS).isBefore(Instant.now())) {
        saveGame();
      }
    } else {
      // If the game is not active or paused, we can save the game state to the database immediately.
      saveGame();
    }

    // Release the lock
    tickLock.set(false);
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

  protected abstract T persistGameState(T game);

  protected final GameContext<T> applyCommand(GameCommand command, GameContext<T> gameContext) {

    GameContext<T> context = gameContext;
    switch (command) {
      case RegisterForGame gameCommand:
        context = registerForGame(gameCommand, gameContext);
        break;
      case UnregisterFromGame gameCommand:
        context = unregisterFromGame(gameCommand, gameContext);
        break;
      default:
        // Allow the subclass to handle any commands that are specific to child game manager.
        return applyGameSpecificCommand(command, gameContext);
    }
    return gameContext;
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
        .confirmed(false)
        .status(PlayerStatus.REGISTERED)
        .build());

    return gameContext.withGame(game.withPlayers(players));
  }

  private GameContext<T> unregisterFromGame(UnregisterFromGame registerForGame, GameContext<T> gameContext) {

    if (gameContext.gameStatus() != GameStatus.SCHEDULED) {
      throw new ValidationException("You can only unregister from the game prior to it starting.");
    }

    return gameContext;

  }

  protected GameContext<T> applyGameSpecificCommand(GameCommand command, GameContext<T> gameContext) {
    throw new ValidationException("Unknown command: " + command.commandId());
  }

}
