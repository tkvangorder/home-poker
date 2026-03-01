package org.homepoker.game;

import lombok.extern.slf4j.Slf4j;
import org.homepoker.game.table.TableManager;
import org.homepoker.game.table.TableUtils;
import org.homepoker.game.table.TexasHoldemTableManager;
import org.homepoker.model.event.SystemError;
import org.homepoker.model.event.game.GameMessage;
import org.homepoker.model.event.game.GameStatusChanged;
import org.homepoker.model.event.game.PlayerBuyIn;
import org.homepoker.model.event.game.PlayerMoved;
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

  public void addGameListener(GameListener listener) {
    gameListeners.add(listener);
  }
  public void removeGameListener(GameListener listener) {
    gameListeners.remove(listener);
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

      gameContext.queueEvent(new GameStatusChanged(Instant.now(), game.id(), oldStatus, GameStatus.SEATING));
      gameContext.queueEvent(new GameMessage(Instant.now(), game.id(), "Seating is now open."));
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
        table.status(Table.Status.PLAYING);
      }

      gameContext.queueEvent(new GameStatusChanged(Instant.now(), game.id(), oldStatus, GameStatus.ACTIVE));
      gameContext.queueEvent(new GameMessage(Instant.now(), game.id(), "Game is now active."));
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
        gameContext.queueEvent(new GameStatusChanged(Instant.now(), game.id(), oldStatus, GameStatus.COMPLETED));
        gameContext.queueEvent(new GameMessage(Instant.now(), game.id(), "Game has ended."));
        gameContext.forceUpdate(true);
      } else if (pauseGameRequested) {
        pauseGameRequested = false;
        GameStatus oldStatus = game.status();
        game.status(GameStatus.PAUSED);
        gameContext.queueEvent(new GameStatusChanged(Instant.now(), game.id(), oldStatus, GameStatus.PAUSED));
        gameContext.queueEvent(new GameMessage(Instant.now(), game.id(), "Game is now paused."));
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
        }
      }

      gameContext.queueEvent(new GameStatusChanged(Instant.now(), game.id(), oldStatus, GameStatus.BALANCING));
      gameContext.queueEvent(new GameMessage(Instant.now(), game.id(), "Balancing tables after current hands complete."));
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
      gameContext.queueEvent(new GameStatusChanged(Instant.now(), game.id(), oldStatus, GameStatus.COMPLETED));
      gameContext.queueEvent(new GameMessage(Instant.now(), game.id(), "Game has ended."));
      gameContext.forceUpdate(true);
      return;
    }
    if (pauseGameRequested) {
      pauseGameRequested = false;
      GameStatus oldStatus = game.status();
      game.status(GameStatus.PAUSED);
      gameContext.queueEvent(new GameStatusChanged(Instant.now(), game.id(), oldStatus, GameStatus.PAUSED));
      gameContext.queueEvent(new GameMessage(Instant.now(), game.id(), "Game is now paused."));
      gameContext.forceUpdate(true);
      return;
    }

    // Redistribute players across tables
    redistributePlayers(game, gameContext);

    // Transition back to ACTIVE
    GameStatus oldStatus = game.status();
    game.status(GameStatus.ACTIVE);

    for (Table table : game.tables().values()) {
      table.status(Table.Status.PLAYING);
    }

    gameContext.queueEvent(new GameStatusChanged(Instant.now(), game.id(), oldStatus, GameStatus.ACTIVE));
    gameContext.queueEvent(new GameMessage(Instant.now(), game.id(), "Tables balanced. Game resumed."));
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
      case RegisterForGame gameCommand -> registerForGame(gameCommand, game, gameContext);
      case UnregisterFromGame gameCommand -> unregisterFromGame(gameCommand, game, gameContext);
      case EndGame gameCommand -> endGame(gameCommand, game, gameContext);
      case StartGame gameCommand -> startGame(gameCommand, game, gameContext);
      case PauseGame gameCommand -> pauseGame(gameCommand, game, gameContext);
      case ResumeGame gameCommand -> resumeGame(gameCommand, game, gameContext);
      case BuyIn gameCommand -> buyIn(gameCommand, game, gameContext);
      case LeaveGame gameCommand -> leaveGame(gameCommand, game, gameContext);
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
    Player player = Player.builder().user(registerForGame.user()).status(PlayerStatus.REGISTERED).build();
    game.addPlayer(player);

    // During SEATING or ACTIVE, assign the player to a seat on the table with the fewest players
    if (game.status() == GameStatus.SEATING || game.status() == GameStatus.ACTIVE) {
      assignPlayerToTableWithFewestPlayers(player, game);
    }

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
    if (!SecurityUtilities.userIsAdmin(gameCommand.user())) {
      throw new ValidationException("Only an admin can end a game.");
    }

    if (game.status() == GameStatus.ACTIVE || game.status() == GameStatus.BALANCING) {
      // Two-phase shutdown: signal tables to pause after current hand
      endGameRequested = true;
      for (Table table : game.tables().values()) {
        if (table.status() == Table.Status.PLAYING) {
          table.status(Table.Status.PAUSE_AFTER_HAND);
        }
      }
      gameContext.queueEvent(new GameMessage(Instant.now(), game.id(), "Game ending after current hands complete."));
    } else {
      // For SCHEDULED, SEATING, PAUSED: transition immediately to COMPLETED
      GameStatus oldStatus = game.status();
      game.status(GameStatus.COMPLETED);
      gameContext.queueEvent(new GameStatusChanged(Instant.now(), game.id(), oldStatus, GameStatus.COMPLETED));
      gameContext.queueEvent(new GameMessage(Instant.now(), game.id(), "Game has ended."));
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
      }
    }
    gameContext.queueEvent(new GameMessage(Instant.now(), game.id(), "Game pausing after current hands complete."));
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
      table.status(Table.Status.PLAYING);
    }

    gameContext.queueEvent(new GameStatusChanged(Instant.now(), game.id(), oldStatus, GameStatus.ACTIVE));
    gameContext.queueEvent(new GameMessage(Instant.now(), game.id(), "Game resumed."));
    gameContext.forceUpdate(true);
  }

  private void buyIn(BuyIn gameCommand, T game, GameContext gameContext) {
    GameStatus status = game.status();
    if (status != GameStatus.SEATING && status != GameStatus.ACTIVE && status != GameStatus.PAUSED) {
      throw new ValidationException("Buy-ins are only allowed during SEATING, ACTIVE, or PAUSED states.");
    }

    Player player = game.players().get(gameCommand.user().id());
    if (player == null) {
      throw new ValidationException("You are not registered for this game.");
    }

    int maxBuyIn = getMaxBuyIn(game);
    if (gameCommand.amount() <= 0) {
      throw new ValidationException("Buy-in amount must be positive.");
    }
    if (gameCommand.amount() > maxBuyIn) {
      throw new ValidationException("Buy-in amount exceeds the maximum of " + maxBuyIn + ".");
    }

    int currentChips = player.chipCount();
    int newChipCount = currentChips + gameCommand.amount();
    player.chipCount(newChipCount);

    int currentBuyInTotal = player.buyInTotal();
    player.buyInTotal(currentBuyInTotal + gameCommand.amount());

    if (player.status() == PlayerStatus.REGISTERED || player.status() == PlayerStatus.BUYING_IN) {
      player.status(PlayerStatus.ACTIVE);
    }

    gameContext.queueEvent(new PlayerBuyIn(Instant.now(), game.id(), player.userId(), gameCommand.amount(), newChipCount));
    gameContext.forceUpdate(true);
  }

  private void leaveGame(LeaveGame gameCommand, T game, GameContext gameContext) {
    GameStatus status = game.status();
    if (status != GameStatus.SEATING && status != GameStatus.ACTIVE && status != GameStatus.PAUSED) {
      throw new ValidationException("You can only leave during SEATING, ACTIVE, or PAUSED states.");
    }

    Player player = game.players().get(gameCommand.user().id());
    if (player == null) {
      throw new ValidationException("You are not registered for this game.");
    }

    // If the player is seated, check if they are in an active hand
    if (player.tableId() != null) {
      Table table = game.tables().get(player.tableId());
      if (table != null) {
        // Find the player's seat
        for (Seat seat : table.seats()) {
          if (seat.player() != null && seat.player().userId().equals(player.userId())) {
            if (seat.status() == Seat.Status.ACTIVE) {
              // Player is in an active hand, mark them for removal after the hand
              player.status(PlayerStatus.OUT);
              gameContext.queueEvent(new GameMessage(Instant.now(), game.id(),
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
    gameContext.queueEvent(new GameMessage(Instant.now(), game.id(), player.user().alias() + " has left the game."));
    gameContext.forceUpdate(true);
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
   * Assign a player to a random seat on the table with the fewest players.
   */
  private void assignPlayerToTableWithFewestPlayers(Player player, T game) {
    if (game.tables().isEmpty()) {
      return;
    }
    Table targetTable = null;
    int minPlayers = Integer.MAX_VALUE;
    for (Table table : game.tables().values()) {
      int playerCount = table.numberOfPlayers();
      if (playerCount < minPlayers) {
        minPlayers = playerCount;
        targetTable = table;
      }
    }
    if (targetTable != null && minPlayers < gameSettings.numberOfSeats()) {
      TableUtils.assignPlayerToRandomSeat(player, targetTable);
    }
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
    // Remove empty tables first (from both game.tables() and tableManagers)
    List<String> emptyTableIds = new ArrayList<>();
    game.tables().entrySet().removeIf(entry -> {
      if (entry.getValue().numberOfPlayers() == 0) {
        emptyTableIds.add(entry.getKey());
        return true;
      }
      return false;
    });
    for (String id : emptyTableIds) {
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
    // Gather all seated players
    List<Player> seatedPlayers = new ArrayList<>();
    for (Table table : game.tables().values()) {
      for (Seat seat : table.seats()) {
        if (seat.player() != null && seat.status() != Seat.Status.EMPTY) {
          seatedPlayers.add(seat.player());
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
      gameContext.queueEvent(new PlayerMoved(Instant.now(), game.id(), player.userId(), null, table.id()));
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
      // Check if there are unassigned players
      boolean hasUnassigned = false;
      for (Player player : game.players().values()) {
        if (player.tableId() == null && player.status() != PlayerStatus.OUT) {
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
