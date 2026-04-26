package org.homepoker.game;

import lombok.extern.slf4j.Slf4j;
import org.homepoker.game.table.TableManager;
import org.homepoker.game.table.TableUtils;
import org.homepoker.game.table.TexasHoldemTableManager;
import org.homepoker.model.event.GameEvent;
import org.homepoker.model.event.PokerEvent;
import org.homepoker.model.event.SystemError;
import org.homepoker.model.event.TableEvent;
import org.homepoker.model.event.UserEvent;
import org.homepoker.model.event.game.*;
import org.homepoker.model.event.table.TableStatusChanged;
import org.homepoker.model.event.user.GameSnapshot;
import org.homepoker.model.event.user.UserMessage;
import org.homepoker.lib.exception.ValidationException;
import org.homepoker.model.MessageSeverity;
import org.homepoker.model.command.*;
import org.homepoker.model.game.*;
import org.homepoker.model.game.cash.CashGame;
import org.homepoker.security.SecurityUtilities;
import org.homepoker.user.UserManager;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpscLinkedQueue;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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

  /**
   * Active-listener ref count keyed by userId. Mutated only on the game-loop thread,
   * via {@link PlayerConnectedCommand} / {@link PlayerDisconnectedCommand} processing.
   * The transition from absent/0 to 1 emits {@code PlayerReconnected} (when a Player
   * record exists); the transition from 1 to 0 emits {@code PlayerDisconnected}.
   */
  private final Map<String, Integer> activeListenerCounts = new HashMap<>();

  /**
   * Per-table manager instances. Each table has its own manager that owns the Table reference and transient state (e.g. Deck).
   */
  private final NavigableMap<String, TableManager<T>> tableManagers = new TreeMap<>();

  /**
   * The current game state that is being managed by this game manager. Changes to the game state are completely
   * encapsulated within the processGameTick method. An atomic boolean is used to ensure that only one game tick is
   * processed at a time.
   */
  private T game;

  private final GameSettings gameSettings;
  private final UserManager userManager;
  private final SecurityUtilities securityUtilities;

  /**
   * This is an atomic boolean that is used to ensure that only one game tick is processed at a time.
   */
  private final AtomicBoolean tickLock = new AtomicBoolean(false);

  /**
   * Game-stream sequence counter. Stamps non-Table {@link GameEvent}s at fan-out so the
   * client can detect gaps in the game-level stream. Per-table events use the table's own
   * counter on {@link TableManager}. {@link UserEvent}s are excluded from numbering.
   */
  private final AtomicLong gameStreamSeq = new AtomicLong(0);

  /**
   * Flag set by the StartGame command, consumed by transitionGame to trigger SEATING->ACTIVE.
   */
  private boolean startGameRequested;

  /**
   * Flag set by the EndGame command when game is ACTIVE, triggers two-phase shutdown
   * (PAUSE_AFTER_HAND on all tables, then COMPLETED when all tables are PAUSED).
   */
  private boolean endGameRequested;

  /**
   * Flag set by the PauseGame command, triggers two-phase pause
   * (PAUSE_AFTER_HAND on all tables, then PAUSED when all tables are PAUSED).
   */
  private boolean pauseGameRequested;

  public GameManager(T game, UserManager userManager, SecurityUtilities securityUtilities) {
    this.game = game;
    this.userManager = userManager;
    this.securityUtilities = securityUtilities;
    // TODO, as we add other game types, we can switch on game.type() to determine which table manager to use.
    this.gameSettings = GameSettings.TEXAS_HOLDEM_SETTINGS;

    // Create table managers for any existing tables (handles persistence reload + deck recovery)
    for (Table table : game.tables().values()) {
      tableManagers.put(table.id(), createTableManagerForExistingTable(table));
    }
  }

  /**
   * Register a listener for game events. Each invocation appends a separate listener
   * instance — multi-socket-per-user is supported by ref-counting connections rather than
   * collapsing them. The first active listener for a user that already has a Player record
   * triggers a {@code PlayerReconnected} event; subsequent listeners for the same user do
   * not emit additional events.
   */
  public void addGameListener(GameListener listener) {
    gameListeners.add(listener);
    submitCommand(new PlayerConnectedCommand(game.id(), listener.userId()));
  }

  /**
   * Remove a single listener instance. If this was the last active listener for the user,
   * a {@code PlayerDisconnected} event will be emitted on the next tick.
   */
  public void removeGameListener(GameListener listener) {
    if (gameListeners.remove(listener)) {
      submitCommand(new PlayerDisconnectedCommand(game.id(), listener.userId()));
    }
  }

  /**
   * Remove every listener associated with the given user id. One
   * {@code PlayerDisconnectedCommand} is queued per removed listener so the ref-count
   * decrements track the actual number of removals, and the eventual transition to 0
   * fires exactly one {@code PlayerDisconnected} event.
   */
  public void removeGameListenersByUserId(String userId) {
    // Defense in depth: never evict the EventRecorder's synthetic identity. A real user with
    // this id cannot exist (registration enforces email-format on the id), but if some
    // future code path passes the reserved id here, we refuse silently.
    if (org.homepoker.recording.SystemUsers.EVENT_RECORDER_ID.equals(userId)) {
      return;
    }

    int removed = 0;
    for (Iterator<GameListener> it = gameListeners.iterator(); it.hasNext(); ) {
      if (userId.equals(it.next().userId())) {
        it.remove();
        removed++;
      }
    }
    for (int i = 0; i < removed; i++) {
      submitCommand(new PlayerDisconnectedCommand(game.id(), userId));
    }
  }

  /**
   * Current game-stream seq (the most recent value assigned). {@code 0} means none assigned yet.
   */
  public long currentGameStreamSeq() {
    return gameStreamSeq.get();
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
        log.debug("Processing command: [{}]", command);
        try {
          applyCommand(command, game, gameContext);
        } catch (ValidationException e) {
          gameContext.queueEvent(UserMessage.builder()
              .timestamp(Instant.now())
              .userId(command.user().id())
              .severity(MessageSeverity.ERROR)
              .message(e.getMessage())
              .build());
        } catch (RuntimeException e) {
          log.error("An error occurred while while processing command [{}].\n{}", command, e.getMessage(), e);
          SystemError.SystemErrorBuilder builder = SystemError.builder()
              .timestamp(Instant.now())
              .gameId(command.gameId())
              .userId(command.user().id())
              .exception(e);
          if (command instanceof TableCommand tableCommand) {
            builder.tableId(tableCommand.tableId());
          }
          gameContext.queueEvent(builder.build());
        }
      }

      transitionGame(game, gameContext);

      if (game.status() == GameStatus.ACTIVE || game.status() == GameStatus.BALANCING || game.status() == GameStatus.PAUSED) {
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

      // Stamp every accumulated event in deterministic order, then publish.
      // Stamping happens at fan-out (rather than at construction) so all listeners observe
      // the same sequence number for the same event. We stamp regardless of whether listeners
      // are present so the seq advances consistently — that keeps client recovery correct
      // even when a single listener disconnects mid-stream and a snapshot is taken later.
      List<PokerEvent> stamped = new ArrayList<>(gameContext.events().size());
      for (PokerEvent event : gameContext.events()) {
        stamped.add(stampEvent(event));
      }

      if (!gameListeners.isEmpty()) {
        for (PokerEvent event : stamped) {
          log.debug("Sending event: [{}]", event);
          for (GameListener listener : gameListeners) {
            if (listener.acceptsEvent(event)) {
              listener.onEvent(event);
            }
          }
        }
      }
    } finally {
      // Release the lock
      tickLock.set(false);
    }
  }

  /**
   * Stamps an event with the appropriate sequence number (game stream, the table's own
   * stream, or {@code 0} for {@link UserEvent}). Returns the stamped copy. Game-loop thread only.
   * <p>
   * Type-precedence matters: {@link UserEvent} is checked first because {@code HoleCardsDealt}
   * is both a {@code TableEvent} and a {@code UserEvent} — it must not be stamped (the
   * client gap-detection logic ignores {@code UserEvent}s).
   */
  private PokerEvent stampEvent(PokerEvent event) {
    if (event instanceof UserEvent) {
      // UserEvents (including HoleCardsDealt) are not part of gap detection.
      return event;
    }
    if (event instanceof TableEvent tableEvent) {
      TableManager<T> tm = tableManagers.get(tableEvent.tableId());
      if (tm == null) {
        // Defensive: should not happen for an emitted table event, but don't crash.
        return event;
      }
      long seq = tm.nextStreamSeq();
      return tableEvent.withSequenceNumber(seq);
    }
    if (event instanceof GameEvent gameEvent) {
      long seq = gameStreamSeq.incrementAndGet();
      return gameEvent.withSequenceNumber(seq);
    }
    // Plain PokerEvent (e.g., SystemError) is filtered per-user, no seq.
    return event;
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
    return gameSettings;
  }

  protected NavigableMap<String, TableManager<T>> tableManagers() {
    return tableManagers;
  }

  protected abstract T persistGameState(T game);

  /**
   * Creates a new table manager (and its underlying Table) for a brand-new table.
   */
  protected TableManager<T> createTableManager(String tableId) {
    return TexasHoldemTableManager.forNewTable(tableId, gameSettings);
  }

  /**
   * Creates a table manager for an existing (persisted) table, recovering transient state if mid-hand.
   */
  protected TableManager<T> createTableManagerForExistingTable(Table table) {
    return TexasHoldemTableManager.forExistingTable(table, gameSettings);
  }

  /**
   * This method handles state transitions (at the game level)
   *
   * @param gameContext The current game context
   */
  protected final void transitionGame(T game, GameContext gameContext) {

    switch (game.status()) {
      case SCHEDULED -> transitionFromScheduled(game, gameContext);
      case SEATING -> transitionFromSeating(game, gameContext);
      case ACTIVE -> transitionFromActive(game, gameContext);
      case BALANCING -> transitionFromBalancing(game, gameContext);
      case PAUSED -> transitionFromPaused(game, gameContext);
      case COMPLETED -> { /* Terminal state, no transitions */ }
    }
  }

  private void transitionFromScheduled(T game, GameContext gameContext) {
    if (game.startTime().minusSeconds(gameSettings().seatingTimeSeconds()).isBefore(Instant.now())) {
      GameStatus oldStatus = game.status();
      game.status(GameStatus.SEATING);

      // Use GameStateTransitions to create tables and distribute players
      GameStateTransitions.resetSeating(game, gameContext, tableManagers, this::createTableManager);

      // Set all table statuses to PAUSED (tables don't start playing until ACTIVE)
      for (Table table : game.tables().values()) {
        table.status(Table.Status.PAUSED);
      }

      gameContext.queueEvent(new GameStatusChanged(Instant.now(), 0L, game.id(), oldStatus, GameStatus.SEATING));
      gameContext.queueEvent(new GameMessage(Instant.now(), 0L, game.id(), "Seating is now open."));
      gameContext.forceUpdate(true);
    }
  }

  private void transitionFromSeating(T game, GameContext gameContext) {
    if (startGameRequested && countOfSeatedPlayers(game) >= 2 && !game.startTime().isAfter(Instant.now())) {
      startGameRequested = false;
      GameStatus oldStatus = game.status();
      game.status(GameStatus.ACTIVE);

      // Set all tables to PLAYING
      for (Table table : game.tables().values()) {
        Table.Status oldTableStatus = table.status();
        table.status(Table.Status.PLAYING);
        gameContext.queueEvent(new TableStatusChanged(Instant.now(), 0L, game.id(), table.id(), oldTableStatus, Table.Status.PLAYING));
      }

      gameContext.queueEvent(new GameStatusChanged(Instant.now(), 0L, game.id(), oldStatus, GameStatus.ACTIVE));
      gameContext.queueEvent(new GameMessage(Instant.now(), 0L, game.id(), "Game is now active."));
      gameContext.forceUpdate(true);
    }
  }

  private void transitionFromActive(T game, GameContext gameContext) {
    // Transition each table
    for (TableManager<T> tm : tableManagers.values()) {
      tm.transitionTable(game, gameContext);
    }

    // Check if all tables are paused (two-phase pause/end detection)
    if (allTablesPaused(game)) {
      if (endGameRequested) {
        endGameRequested = false;
        pauseGameRequested = false;
        GameStatus oldStatus = game.status();
        game.status(GameStatus.COMPLETED);
        gameContext.queueEvent(new GameStatusChanged(Instant.now(), 0L, game.id(), oldStatus, GameStatus.COMPLETED));
        gameContext.queueEvent(new GameMessage(Instant.now(), 0L, game.id(), "Game has ended."));
        gameContext.forceUpdate(true);
      } else if (pauseGameRequested) {
        pauseGameRequested = false;
        GameStatus oldStatus = game.status();
        game.status(GameStatus.PAUSED);
        gameContext.queueEvent(new GameStatusChanged(Instant.now(), 0L, game.id(), oldStatus, GameStatus.PAUSED));
        gameContext.queueEvent(new GameMessage(Instant.now(), 0L, game.id(), "Game is now paused."));
        gameContext.forceUpdate(true);
      }
      return;
    }

    // Check if we need to create a new table for overflow
    checkForNewTable(game);

    // Check if tables need rebalancing (only when no pause/end is pending)
    if (!pauseGameRequested && !endGameRequested && tablesNeedBalancing(game)) {
      GameStatus oldStatus = game.status();
      game.status(GameStatus.BALANCING);

      // Signal all tables to finish their current hand
      for (Table table : game.tables().values()) {
        if (table.status() == Table.Status.PLAYING) {
          table.status(Table.Status.PAUSE_AFTER_HAND);
          gameContext.queueEvent(new TableStatusChanged(Instant.now(), 0L, game.id(), table.id(), Table.Status.PLAYING, Table.Status.PAUSE_AFTER_HAND));
        }
      }

      gameContext.queueEvent(new GameStatusChanged(Instant.now(), 0L, game.id(), oldStatus, GameStatus.BALANCING));
      gameContext.queueEvent(new GameMessage(Instant.now(), 0L, game.id(), "Balancing tables after current hands complete."));
      gameContext.forceUpdate(true);
    }
  }

  private void transitionFromBalancing(T game, GameContext gameContext) {
    // Continue transitioning tables so hands can finish
    for (TableManager<T> tm : tableManagers.values()) {
      tm.transitionTable(game, gameContext);
    }

    if (!allTablesPaused(game)) {
      return;
    }

    // All tables are paused — check if pause/end was requested during balancing
    if (endGameRequested) {
      endGameRequested = false;
      pauseGameRequested = false;
      GameStatus oldStatus = game.status();
      game.status(GameStatus.COMPLETED);
      gameContext.queueEvent(new GameStatusChanged(Instant.now(), 0L, game.id(), oldStatus, GameStatus.COMPLETED));
      gameContext.queueEvent(new GameMessage(Instant.now(), 0L, game.id(), "Game has ended."));
      gameContext.forceUpdate(true);
      return;
    }
    if (pauseGameRequested) {
      pauseGameRequested = false;
      GameStatus oldStatus = game.status();
      game.status(GameStatus.PAUSED);
      gameContext.queueEvent(new GameStatusChanged(Instant.now(), 0L, game.id(), oldStatus, GameStatus.PAUSED));
      gameContext.queueEvent(new GameMessage(Instant.now(), 0L, game.id(), "Game is now paused."));
      gameContext.forceUpdate(true);
      return;
    }

    // Redistribute players across tables
    redistributePlayers(game, gameContext);

    // Transition back to ACTIVE
    GameStatus oldStatus = game.status();
    game.status(GameStatus.ACTIVE);

    for (Table table : game.tables().values()) {
      Table.Status oldTableStatus = table.status();
      table.status(Table.Status.PLAYING);
      gameContext.queueEvent(new TableStatusChanged(Instant.now(), 0L, game.id(), table.id(), oldTableStatus, Table.Status.PLAYING));
    }

    gameContext.queueEvent(new GameStatusChanged(Instant.now(), 0L, game.id(), oldStatus, GameStatus.ACTIVE));
    gameContext.queueEvent(new GameMessage(Instant.now(), 0L, game.id(), "Tables balanced. Game resumed."));
    gameContext.forceUpdate(true);
  }

  private void transitionFromPaused(T game, GameContext gameContext) {
    // Paused is a stable state. Transitions out of PAUSED are handled by commands (ResumeGame, EndGame).
  }

  protected final void applyCommand(GameCommand command, T game, GameContext gameContext) {

    if (command instanceof TableCommand tableCommand) {
      TableManager<T> tm = tableManagers.get(tableCommand.tableId());
      if (tm == null) {
        throw new ValidationException("Table not found: " + tableCommand.tableId());
      }
      tm.applyCommand(command, game, gameContext);
      return;
    }
    switch (command) {
      case EndGame gameCommand -> endGame(gameCommand, game, gameContext);
      case StartGame gameCommand -> startGame(gameCommand, game, gameContext);
      case PauseGame gameCommand -> pauseGame(gameCommand, game, gameContext);
      case ResumeGame gameCommand -> resumeGame(gameCommand, game, gameContext);
      case BuyIn gameCommand -> buyIn(gameCommand, game, gameContext);
      case LeaveGame gameCommand -> leaveGame(gameCommand, game, gameContext);
      case GetGameState gameCommand -> getGameState(gameCommand, game, gameContext);
      case PlayerConnectedCommand cmd -> handlePlayerConnected(cmd, gameContext);
      case PlayerDisconnectedCommand cmd -> handlePlayerDisconnected(cmd, gameContext);
      default ->
        // Allow the subclass to handle any commands that are specific to child game manager.
          applyGameSpecificCommand(command, game, gameContext);
    }
  }

  /**
   * Increment the active-listener ref count for the user. The transition from
   * absent (or 0) to 1 emits {@link PlayerReconnected} when a Player record already
   * exists for the user. If no Player record exists yet (e.g., admin observer or a
   * brand-new connection that has not yet submitted JoinGame) no event is emitted —
   * the {@code JoinGame} path is responsible for {@code PlayerJoined}.
   */
  private void handlePlayerConnected(PlayerConnectedCommand cmd, GameContext gameContext) {
    String userId = cmd.connectedUserId();
    int newCount = activeListenerCounts.merge(userId, 1, Integer::sum);
    if (newCount == 1 && game.players().containsKey(userId)) {
      gameContext.queueEvent(new PlayerReconnected(
          Instant.now(), 0L, game.id(), userId));
    }
  }

  /**
   * Decrement the active-listener ref count for the user. The transition from 1 to 0
   * emits {@link PlayerDisconnected}. Stale decrements (no entry, or non-positive count)
   * are ignored defensively.
   */
  private void handlePlayerDisconnected(PlayerDisconnectedCommand cmd, GameContext gameContext) {
    String userId = cmd.disconnectedUserId();
    Integer current = activeListenerCounts.get(userId);
    if (current == null || current <= 0) {
      return;
    }
    int newCount = current - 1;
    if (newCount == 0) {
      activeListenerCounts.remove(userId);
      gameContext.queueEvent(new PlayerDisconnected(
          Instant.now(), 0L, game.id(), userId));
    } else {
      activeListenerCounts.put(userId, newCount);
    }
  }

  private void endGame(EndGame gameCommand, T game, GameContext gameContext) {
    if (game.status() == GameStatus.COMPLETED) {
      throw new ValidationException("This game has already completed.");
    }
    if (!SecurityUtilities.userIsAdmin(gameCommand.user())) {
      throw new ValidationException("Only an admin can end a game.");
    }

    if (game.status() == GameStatus.ACTIVE || game.status() == GameStatus.BALANCING) {
      // Two-phase shutdown: signal tables to pause after current hand
      endGameRequested = true;
      for (Table table : game.tables().values()) {
        if (table.status() == Table.Status.PLAYING) {
          table.status(Table.Status.PAUSE_AFTER_HAND);
          gameContext.queueEvent(new TableStatusChanged(Instant.now(), 0L, game.id(), table.id(), Table.Status.PLAYING, Table.Status.PAUSE_AFTER_HAND));
        }
      }
      gameContext.queueEvent(new GameMessage(Instant.now(), 0L, game.id(), "Game ending after current hands complete."));
    } else {
      // For SCHEDULED, SEATING, PAUSED: transition immediately to COMPLETED
      GameStatus oldStatus = game.status();
      game.status(GameStatus.COMPLETED);
      gameContext.queueEvent(new GameStatusChanged(Instant.now(), 0L, game.id(), oldStatus, GameStatus.COMPLETED));
      gameContext.queueEvent(new GameMessage(Instant.now(), 0L, game.id(), "Game has ended."));
    }
    gameContext.forceUpdate(true);
  }

  private void startGame(StartGame gameCommand, T game, GameContext gameContext) {
    if (game.status() != GameStatus.SEATING) {
      throw new ValidationException("The game can only be started when in SEATING state.");
    }
    if (!SecurityUtilities.userIsAdmin(gameCommand.user())) {
      throw new ValidationException("Only an admin can start a game.");
    }
    if (countOfSeatedPlayers(game) < 2) {
      throw new ValidationException("At least 2 seated players are required to start the game.");
    }
    startGameRequested = true;
  }

  private void pauseGame(PauseGame gameCommand, T game, GameContext gameContext) {
    if (game.status() != GameStatus.ACTIVE && game.status() != GameStatus.BALANCING) {
      throw new ValidationException("The game can only be paused when it is active.");
    }
    if (!SecurityUtilities.userIsAdmin(gameCommand.user())) {
      throw new ValidationException("Only an admin can pause a game.");
    }

    pauseGameRequested = true;
    for (Table table : game.tables().values()) {
      if (table.status() == Table.Status.PLAYING) {
        table.status(Table.Status.PAUSE_AFTER_HAND);
        gameContext.queueEvent(new TableStatusChanged(Instant.now(), 0L, game.id(), table.id(), Table.Status.PLAYING, Table.Status.PAUSE_AFTER_HAND));
      }
    }
    gameContext.queueEvent(new GameMessage(Instant.now(), 0L, game.id(), "Game pausing after current hands complete."));
  }

  private void resumeGame(ResumeGame gameCommand, T game, GameContext gameContext) {
    if (game.status() != GameStatus.PAUSED) {
      throw new ValidationException("The game can only be resumed when it is paused.");
    }
    if (!SecurityUtilities.userIsAdmin(gameCommand.user())) {
      throw new ValidationException("Only an admin can resume a game.");
    }

    GameStatus oldStatus = game.status();
    game.status(GameStatus.ACTIVE);

    for (Table table : game.tables().values()) {
      Table.Status oldTableStatus = table.status();
      table.status(Table.Status.PLAYING);
      gameContext.queueEvent(new TableStatusChanged(Instant.now(), 0L, game.id(), table.id(), oldTableStatus, Table.Status.PLAYING));
    }

    gameContext.queueEvent(new GameStatusChanged(Instant.now(), 0L, game.id(), oldStatus, GameStatus.ACTIVE));
    gameContext.queueEvent(new GameMessage(Instant.now(), 0L, game.id(), "Game resumed."));
    gameContext.forceUpdate(true);
  }

  private void buyIn(BuyIn gameCommand, T game, GameContext gameContext) {
    GameStatus status = game.status();
    if (status != GameStatus.SEATING && status != GameStatus.ACTIVE && status != GameStatus.PAUSED) {
      throw new ValidationException("Buy-ins are only allowed during SEATING, ACTIVE, or PAUSED states.");
    }

    Player player = game.players().get(gameCommand.user().id());
    if (player == null) {
      throw new ValidationException("You have not joined this game.");
    }

    int maxBuyIn = getMaxBuyIn(game);
    if (gameCommand.amount() <= 0) {
      throw new ValidationException("Buy-in amount must be positive.");
    }

    int currentChips = player.chipCount();
    int newChipCount = currentChips + gameCommand.amount();
    if (newChipCount > maxBuyIn) {
      throw new ValidationException("Buy-in would bring chip count to " + newChipCount + ", which exceeds the maximum of " + maxBuyIn + ".");
    }
    player.chipCount(newChipCount);

    int currentBuyInTotal = player.buyInTotal();
    player.buyInTotal(currentBuyInTotal + gameCommand.amount());

    if (player.status() == PlayerStatus.AWAY || player.status() == PlayerStatus.BUYING_IN) {
      player.status(PlayerStatus.ACTIVE);
    }

    // If the player is not yet seated at a table, seat them now that they have chips
    if (player.tableId() == null) {
      String tableId = GameUtils.assignPlayerToTableWithFewestPlayers(player, game, gameSettings().numberOfSeats());
      if (tableId != null) {
        gameContext.queueEvent(new PlayerSeated(Instant.now(), 0L, game.id(), player.userId(), tableId));
      }
    }

    gameContext.queueEvent(new PlayerBuyIn(Instant.now(), 0L, game.id(), player.userId(), gameCommand.amount(), newChipCount));
    gameContext.forceUpdate(true);
  }

  private void leaveGame(LeaveGame gameCommand, T game, GameContext gameContext) {
    GameStatus status = game.status();
    if (status == GameStatus.COMPLETED) {
      throw new ValidationException("This game has already completed.");
    }

    Player player = game.players().get(gameCommand.user().id());
    if (player == null) {
      throw new ValidationException("You have not joined this game.");
    }

    // In SCHEDULED state, mark the player as OUT (keep record for auditing)
    if (status == GameStatus.SCHEDULED) {
      player.status(PlayerStatus.OUT);
      gameContext.queueEvent(new GameMessage(Instant.now(), 0L, game.id(), player.user().alias() + " has left the game."));
      gameContext.forceUpdate(true);
      return;
    }

    // If the player is seated, check if they are in an active hand
    if (player.tableId() != null) {
      Table table = game.tables().get(player.tableId());
      if (table != null) {
        // Find the player's seat
        for (Seat seat : table.seats()) {
          if (seat.player() != null && seat.player().userId().equals(player.userId())) {
            if (seat.status() == Seat.Status.ACTIVE || seat.status() == Seat.Status.FOLDED) {
              // Player is in an active hand (playing or folded), mark them for removal after the hand
              player.status(PlayerStatus.OUT);
              gameContext.queueEvent(new GameMessage(Instant.now(), 0L, game.id(),
                  player.user().alias() + " will leave after the current hand."));
              gameContext.forceUpdate(true);
              return;
            }
            // Not in an active hand, remove from seat immediately
            seat.status(Seat.Status.EMPTY);
            seat.player(null);
            break;
          }
        }
      }
      player.tableId(null);
    }

    player.status(PlayerStatus.OUT);
    gameContext.queueEvent(new GameMessage(Instant.now(), 0L, game.id(), player.user().alias() + " has left the game."));
    gameContext.forceUpdate(true);
  }

  private void getGameState(GetGameState gameCommand, T game, GameContext gameContext) {
    Map<String, Long> tableSeqs = new HashMap<>();
    for (Map.Entry<String, TableManager<T>> entry : tableManagers.entrySet()) {
      tableSeqs.put(entry.getKey(), entry.getValue().currentStreamSeq());
    }
    gameContext.queueEvent(new GameSnapshot(
        Instant.now(),
        gameCommand.user().id(),
        game.id(),
        game.name(),
        game.status(),
        game.startTime(),
        game.smallBlind(),
        game.bigBlind(),
        List.copyOf(game.players().values()),
        List.copyOf(game.tables().keySet()),
        gameStreamSeq.get(),
        Map.copyOf(tableSeqs)
    ));
  }

  protected void applyGameSpecificCommand(GameCommand command, T game, GameContext gameContext) {
    throw new ValidationException("Unknown command: " + command.commandId());
  }

  // --- Helper methods ---

  /**
   * Count the number of players who are seated at tables (non-EMPTY seats).
   */
  private int countOfSeatedPlayers(T game) {
    int count = 0;
    for (Table table : game.tables().values()) {
      count += table.numberOfPlayers();
    }
    return count;
  }

  /**
   * Check if all tables in the game have PAUSED status.
   */
  private boolean allTablesPaused(T game) {
    if (game.tables().isEmpty()) {
      return true;
    }
    for (Table table : game.tables().values()) {
      if (table.status() != Table.Status.PAUSED) {
        return false;
      }
    }
    return true;
  }

  /**
   * Get the maximum buy-in for the game. For cash games this is on the CashGame model.
   */
  private int getMaxBuyIn(T game) {
    if (game instanceof CashGame cashGame) {
      return cashGame.maxBuyIn();
    }
    return Integer.MAX_VALUE;
  }

  /**
   * Check if tables need rebalancing. Returns true if there is an imbalance of 2+ players
   * between the largest and smallest tables, or if there are more tables than needed.
   */
  private boolean tablesNeedBalancing(T game) {
    // Remove empty tables first (from both game.tables() and tableManagers), but always keep at least one table
    List<String> emptyTableIds = new ArrayList<>();
    for (Map.Entry<String, Table> entry : game.tables().entrySet()) {
      if (entry.getValue().numberOfPlayers() == 0) {
        emptyTableIds.add(entry.getKey());
      }
    }
    // Always keep at least one table — skip the first empty table if removing all would leave zero
    if (emptyTableIds.size() == game.tables().size() && !emptyTableIds.isEmpty()) {
      emptyTableIds.removeFirst();
    }
    for (String id : emptyTableIds) {
      game.tables().remove(id);
      tableManagers.remove(id);
    }

    if (game.tables().size() < 2) {
      return false;
    }

    int maxPlayers = Integer.MIN_VALUE;
    int minPlayers = Integer.MAX_VALUE;
    int totalPlayers = 0;

    for (Table table : game.tables().values()) {
      int count = table.numberOfPlayers();
      maxPlayers = Math.max(maxPlayers, count);
      minPlayers = Math.min(minPlayers, count);
      totalPlayers += count;
    }

    // Check for player count imbalance
    if (maxPlayers - minPlayers >= 2) {
      return true;
    }

    // Check for excess tables (more tables than needed)
    int minTables = (int) Math.ceil((double) totalPlayers / gameSettings.numberOfSeats());
    return game.tables().size() > Math.max(minTables, 1);
  }

  /**
   * Redistribute all seated players evenly across the optimal number of tables.
   * Called during the BALANCING state when all tables are paused.
   */
  private void redistributePlayers(T game, GameContext gameContext) {
    // Gather all seated players and remember their previous table assignments
    Map<String, String> previousTableIds = new HashMap<>();
    List<Player> seatedPlayers = new ArrayList<>();
    for (Table table : game.tables().values()) {
      for (Seat seat : table.seats()) {
        if (seat.player() != null && seat.status() != Seat.Status.EMPTY) {
          seatedPlayers.add(seat.player());
          previousTableIds.put(seat.player().userId(), table.id());
          seat.status(Seat.Status.EMPTY);
          seat.player(null);
        }
      }
    }

    if (seatedPlayers.isEmpty()) {
      return;
    }

    // Determine optimal number of tables
    int optimalTableCount = (int) Math.ceil((double) seatedPlayers.size() / gameSettings.numberOfSeats());
    optimalTableCount = Math.max(optimalTableCount, 1);

    // Remove excess tables or create new ones as needed
    while (game.tables().size() > optimalTableCount) {
      String lastKey = game.tables().lastKey();
      game.tables().remove(lastKey);
      tableManagers.remove(lastKey);
    }
    while (game.tables().size() < optimalTableCount) {
      String newTableId = "TABLE-" + game.tables().size();
      TableManager<T> tm = createTableManager(newTableId);
      tableManagers.put(newTableId, tm);
      game.tables().put(newTableId, tm.table());
    }

    // Distribute players round-robin across tables
    String[] tableIds = game.tables().keySet().toArray(new String[0]);
    int tableIndex = 0;
    for (Player player : seatedPlayers) {
      Table table = game.tables().get(tableIds[tableIndex]);
      TableUtils.assignPlayerToRandomSeat(player, table);
      String fromTableId = previousTableIds.get(player.userId());
      if (!table.id().equals(fromTableId)) {
        gameContext.queueEvent(new PlayerMovedTables(Instant.now(), 0L, game.id(), player.userId(), fromTableId, table.id()));
      }
      tableIndex = (tableIndex + 1) % tableIds.length;
    }
  }

  /**
   * Check if a new table is needed (all tables are full and players are waiting).
   */
  private void checkForNewTable(T game) {
    boolean allFull = true;
    for (Table table : game.tables().values()) {
      if (table.numberOfPlayers() < gameSettings.numberOfSeats()) {
        allFull = false;
        break;
      }
    }

    if (allFull) {
      // Check if there are unassigned players with chips (players without chips must buy in first)
      boolean hasUnassigned = false;
      for (Player player : game.players().values()) {
        if (player.tableId() == null && player.status() != PlayerStatus.OUT && player.chipCount() > 0) {
          hasUnassigned = true;
          break;
        }
      }

      if (hasUnassigned) {
        String newTableId = "TABLE-" + game.tables().size();
        TableManager<T> tm = createTableManager(newTableId);
        if (game.status() == GameStatus.ACTIVE) {
          tm.table().status(Table.Status.PLAYING);
        }
        tableManagers.put(newTableId, tm);
        game.tables().put(newTableId, tm.table());
      }
    }
  }
}
