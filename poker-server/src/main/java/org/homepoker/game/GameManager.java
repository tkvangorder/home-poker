package org.homepoker.game;

import lombok.extern.slf4j.Slf4j;
import org.homepoker.game.table.TableManager;
import org.homepoker.game.table.TexasHoldemTableManager;
import org.homepoker.model.event.SystemError;
import org.homepoker.model.event.user.UserMessage;
import org.homepoker.lib.exception.ValidationException;
import org.homepoker.model.MessageSeverity;
import org.homepoker.model.command.*;
import org.homepoker.model.game.*;
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
  private final MessagePassingQueue<GameCommand> pendingCommands = new MpscLinkedQueue<>();

  /**
   * For now, using an immutable list of listeners. If there are concerns about performance or a need to find listeners
   * pinned to a specific user, we can use a map for faster lookups.
   */
  private final List<GameListener> gameListeners = new ArrayList<>();


  private final TableManager<T> tableManager;

  /**
   * The current game state that is being managed by this game manager. Changes to the game state are completely
   * encapsulated within the processGameTick method. An atomic boolean is used to ensure that only one game tick is
   * processed at a time.
   */
  private T game;

  private final UserManager userManager;
  private final SecurityUtilities securityUtilities;

  /**
   * This is an atomic boolean that is used to ensure that only one game tick is processed at a time.
   */
  private final AtomicBoolean tickLock = new AtomicBoolean(false);

  public GameManager(T game, UserManager userManager, SecurityUtilities securityUtilities) {
    this.game = game;
    this.userManager = userManager;
    this.securityUtilities = securityUtilities;
    // TODO, as we add other game types, we can switch on game.type() to determine which table manager to use.
    this.tableManager = new TexasHoldemTableManager<>();
  }

  public void addGameListener(GameListener listener) {
  }
  public void removeGameListener(GameListener listener) {
    // TODO Auto-generated method stub
  }

  protected final T game() {
    return game;
  }

  public String gameId() {
    return game.id();
  }

  public GameStatus gameStatus() {
    return game.status();
  }

  protected UserManager userManager() {
    return userManager;
  }

  protected SecurityUtilities securityUtilities() {
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
      GameContext gameContext = new GameContext(gameSettings());

      // Process queued Commands
      List<GameCommand> commands = new ArrayList<>();
      pendingCommands.drain(commands::add);

      for (GameCommand command : commands) {
        try {
          applyCommand(command, game, gameContext);
        } catch (ValidationException e) {
          gameContext.queueEvent(UserMessage.builder()
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
          gameContext.queueEvent(builder.build());
        }
      }

      transitionGame(game, gameContext);

      if (game.status() == GameStatus.ACTIVE || game.status() == GameStatus.PAUSED) {
        // If the game is active or paused, there are active threads firing for each "tick", we want to periodically
        // save the in-memory state of the game to the database.

        //noinspection DataFlowIssue
        if (gameContext.forceUpdate() || game.lastModified() == null ||
            game.lastModified().plusSeconds(gameSettings().saveIntervalSeconds()).isBefore(Instant.now())) {
          game = saveGame();
        }
      } else {
        // If the game is not active or paused, we can save the game state to the database immediately.
        game = saveGame();
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
    return persistGameState(game);
  }

  protected GameSettings gameSettings() {
    return GameSettings.DEFAULT;
  }
  protected abstract T persistGameState(T game);

  /**
   * This method handles state transitions (at the game level)
   *
   * @param gameContext The current game context
   */
  protected final void transitionGame(T game, GameContext gameContext) {
    if (game.status() == GameStatus.SCHEDULED && game.startTime().minusSeconds(
        gameSettings().seatingTimeSeconds()).isBefore(Instant.now())) {
      game.status(GameStatus.SEATING);

      int tableCount = (game.players().size() / 9) + 1;
      if (tableCount > game.tables().size()) {
        for (int i = game.tables().size(); i < tableCount; i++) {
          game.tables().put("Table-" + i, Table.builder().id("Table-" + i).build());
        }
      }
    } else
    if (game.status() == GameStatus.ACTIVE || game.status() == GameStatus.PAUSED) {
      // If the game is active or paused, we need to see if the state of the game/tables has changed based on either
      // the commands that were processed or time passing.

      // TODO ProcessEachTable
      for (Table table : game.tables().values()) {
        tableManager.transitionTable(game, table, gameContext);
      }

      // TODO Top Level Game Processing
      transitionGame(game, gameContext);
    }

  }

  protected final void applyCommand(GameCommand command, T game, GameContext gameContext) {

    if (command instanceof TableCommand tableCommand) {
      Table table = game.tables().get(tableCommand.tableId());
      if (table == null) {
        throw new ValidationException("Table not found: " + tableCommand.tableId());
      }
      tableManager.applyCommand(command, game, table, gameContext);
      return;
    }
    switch (command) {
      case RegisterForGame gameCommand -> registerForGame(gameCommand, game, gameContext);
      case UnregisterFromGame gameCommand -> unregisterFromGame(gameCommand, game, gameContext);
      case EndGame gameCommand -> endGame(gameCommand, game, gameContext);
      default ->
        // Allow the subclass to handle any commands that are specific to child game manager.
          applyGameSpecificCommand(command, game, gameContext);
    }
  }

  private void registerForGame(RegisterForGame registerForGame, T game, GameContext gameContext) {

    if (game.status() == GameStatus.COMPLETED) {
      throw new ValidationException("This game has already completed.");
    }

    if (game.players().containsKey(registerForGame.user().loginId())) {
      throw new ValidationException("You are already registered for this game.");
    }
    game.addPlayer(
        Player.builder().user(registerForGame.user()).status(PlayerStatus.REGISTERED).build()
    );
    gameContext.forceUpdate(true);
  }

  private void unregisterFromGame(UnregisterFromGame registerForGame, T game, GameContext gameContext) {

    if (game.status() != GameStatus.SCHEDULED) {
      throw new ValidationException("You can only unregister from the game prior to it starting.");
    }

    if (!game.players().containsKey(registerForGame.user().loginId())) {
      throw new ValidationException("You are not registered for this game.");
    }

    game.players().remove(registerForGame.user().loginId());
    gameContext.forceUpdate(true);
  }

  private void endGame(EndGame gameCommand, T game, GameContext gameContext) {
    if (game.status() == GameStatus.COMPLETED) {
      throw new ValidationException("This game has already completed.");
    }
    if (SecurityUtilities.userIsAdmin(gameCommand.user())) {
      game.status(GameStatus.COMPLETED);
    } else {
      throw new ValidationException("Only an admin can end a game.");
    }
  }

  protected void applyGameSpecificCommand(GameCommand command, T game, GameContext gameContext) {
    throw new ValidationException("Unknown command: " + command.commandId());
  }

}
