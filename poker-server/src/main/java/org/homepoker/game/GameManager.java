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


  private final TableManager<T> tableManager;

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
    this.tableManager = new TexasHoldemTableManager<>(gameSettings);
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
    return gameSettings;
  }

  protected abstract T persistGameState(T game);

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
      case PAUSED -> transitionFromPaused(game, gameContext);
      case COMPLETED -> { /* Terminal state, no transitions */ }
    }
  }

  private void transitionFromScheduled(T game, GameContext gameContext) {
    if (game.startTime().minusSeconds(gameSettings().seatingTimeSeconds()).isBefore(Instant.now())) {
      GameStatus oldStatus = game.status();
      game.status(GameStatus.SEATING);

      // Use GameStateTransitions to create tables and distribute players
      GameStateTransitions.resetSeating(game, gameContext);

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
    for (Table table : game.tables().values()) {
      tableManager.transitionTable(game, table, gameContext);
    }

    // Table balancing
    balanceTables(game, gameContext);

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
    }
  }

  private void transitionFromPaused(T game, GameContext gameContext) {
    // Paused is a stable state. Transitions out of PAUSED are handled by commands (ResumeGame, EndGame).
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

    if (game.status() == GameStatus.ACTIVE) {
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
    if (game.status() != GameStatus.ACTIVE) {
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

    int currentChips = player.chipCount() != null ? player.chipCount() : 0;
    int newChipCount = currentChips + gameCommand.amount();
    player.chipCount(newChipCount);

    int currentBuyInTotal = player.buyInTotal() != null ? player.buyInTotal() : 0;
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
      return cashGame.maxBuyIn() != null ? cashGame.maxBuyIn() : Integer.MAX_VALUE;
    }
    return Integer.MAX_VALUE;
  }

  /**
   * Balance tables when the game is ACTIVE. Moves players between tables to keep them
   * within 1 player of each other.
   */
  private void balanceTables(T game, GameContext gameContext) {
    if (game.tables().size() < 2) {
      // Check if we need to create a new table
      checkForNewTable(game);
      return;
    }

    boolean moved = true;
    while (moved) {
      moved = false;

      // Find largest and smallest tables
      Table largest = null;
      Table smallest = null;
      int maxPlayers = Integer.MIN_VALUE;
      int minPlayers = Integer.MAX_VALUE;

      for (Table table : game.tables().values()) {
        int count = effectivePlayerCount(table);
        if (count > maxPlayers) {
          maxPlayers = count;
          largest = table;
        }
        if (count < minPlayers) {
          minPlayers = count;
          smallest = table;
        }
      }

      if (largest == null || smallest == null || maxPlayers - minPlayers < 2) {
        break;
      }

      // Select player to move from largest table
      Player playerToMove = selectPlayerToMove(largest);
      if (playerToMove == null) {
        break;
      }

      // Find the seat of the player
      int seatPosition = -1;
      Seat playerSeat = null;
      for (int i = 0; i < largest.seats().size(); i++) {
        Seat seat = largest.seats().get(i);
        if (seat.player() != null && seat.player().userId().equals(playerToMove.userId())) {
          seatPosition = i;
          playerSeat = seat;
          break;
        }
      }

      if (playerSeat == null) {
        break;
      }

      if (playerSeat.status() == Seat.Status.ACTIVE) {
        // Player is in an active hand, defer the move
        largest.pendingMoves().add(new Table.PendingMove(seatPosition, smallest.id()));
      } else {
        // Execute move immediately
        executePlayerMove(playerToMove, playerSeat, largest, smallest, game, gameContext);
      }
      moved = true;
    }

    checkForNewTable(game);
    checkForTableMerge(game, gameContext);
  }

  /**
   * Returns the effective player count for a table, accounting for pending outbound moves.
   */
  private int effectivePlayerCount(Table table) {
    return table.numberOfPlayers() - table.pendingMoves().size();
  }

  /**
   * Select a player to move from the given table, prioritizing JOINED_WAITING players.
   */
  private Player selectPlayerToMove(Table table) {
    // Priority 1: JOINED_WAITING players (not yet in a hand)
    for (Seat seat : table.seats()) {
      if (seat.status() == Seat.Status.JOINED_WAITING && seat.player() != null) {
        return seat.player();
      }
    }
    // Priority 2: Any non-empty player (prefer those not in active hand)
    for (Seat seat : table.seats()) {
      if (seat.player() != null && seat.status() != Seat.Status.EMPTY) {
        return seat.player();
      }
    }
    return null;
  }

  /**
   * Execute the immediate move of a player from one table to another.
   */
  private void executePlayerMove(Player player, Seat sourceSeat, Table sourceTable, Table targetTable, T game, GameContext gameContext) {
    String fromTableId = sourceTable.id();

    // Remove from source table
    sourceSeat.status(Seat.Status.EMPTY);
    sourceSeat.player(null);

    // Assign to target table
    TableUtils.assignPlayerToRandomSeat(player, targetTable);

    gameContext.queueEvent(new PlayerMoved(Instant.now(), game.id(), player.userId(), fromTableId, targetTable.id()));
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
        Table newTable = tableManager.createTable(newTableId);
        if (game.status() == GameStatus.ACTIVE) {
          newTable.status(Table.Status.PLAYING);
        }
        game.tables().put(newTableId, newTable);
      }
    }
  }

  /**
   * Check if tables can be merged (excess tables after players leave).
   */
  private void checkForTableMerge(T game, GameContext gameContext) {
    // Remove empty tables immediately
    game.tables().entrySet().removeIf(entry -> entry.getValue().numberOfPlayers() == 0);

    if (game.tables().size() <= 1) {
      clearTableExcessSince(game);
      return;
    }

    int totalPlayers = 0;
    for (Table table : game.tables().values()) {
      totalPlayers += table.numberOfPlayers();
    }

    int minTables = (int) Math.ceil((double) totalPlayers / gameSettings.numberOfSeats());
    if (minTables < 1) {
      minTables = 1;
    }

    if (game.tables().size() > minTables) {
      Instant excessSince = getTableExcessSince(game);
      if (excessSince == null) {
        setTableExcessSince(game, Instant.now());
      } else if (excessSince.plusSeconds(gameSettings.tableMergeGraceSeconds()).isBefore(Instant.now())) {
        // Grace period elapsed, merge the smallest table
        mergeSmallestTable(game, gameContext);
        clearTableExcessSince(game);
      }
    } else {
      clearTableExcessSince(game);
    }
  }

  /**
   * Merge the smallest table into other tables.
   */
  private void mergeSmallestTable(T game, GameContext gameContext) {
    Table smallest = null;
    int minPlayers = Integer.MAX_VALUE;
    for (Table table : game.tables().values()) {
      if (table.numberOfPlayers() < minPlayers) {
        minPlayers = table.numberOfPlayers();
        smallest = table;
      }
    }
    if (smallest == null) {
      return;
    }

    // Move all players from the smallest table to other tables
    for (Seat seat : smallest.seats()) {
      if (seat.player() != null && seat.status() != Seat.Status.EMPTY) {
        Player player = seat.player();

        if (seat.status() == Seat.Status.ACTIVE) {
          // Player is in active hand, defer
          // Find a target table
          Table target = findTableWithFewestPlayers(game, smallest.id());
          if (target != null) {
            int seatPos = smallest.seats().indexOf(seat);
            smallest.pendingMoves().add(new Table.PendingMove(seatPos, target.id()));
          }
        } else {
          // Execute move immediately
          Table target = findTableWithFewestPlayers(game, smallest.id());
          if (target != null) {
            executePlayerMove(player, seat, smallest, target, game, gameContext);
          }
        }
      }
    }

    // If all players moved, remove the table
    if (smallest.numberOfPlayers() == 0) {
      game.tables().remove(smallest.id());
    }
  }

  /**
   * Find the table with the fewest players, excluding the specified table.
   */
  private Table findTableWithFewestPlayers(T game, String excludeTableId) {
    Table target = null;
    int minPlayers = Integer.MAX_VALUE;
    for (Map.Entry<String, Table> entry : game.tables().entrySet()) {
      if (!entry.getKey().equals(excludeTableId)) {
        int count = entry.getValue().numberOfPlayers();
        if (count < minPlayers) {
          minPlayers = count;
          target = entry.getValue();
        }
      }
    }
    return target;
  }

  // Table excess tracking helpers (uses CashGame-specific field when available)
  private Instant getTableExcessSince(T game) {
    if (game instanceof CashGame cashGame) {
      return cashGame.tableExcessSince();
    }
    return null;
  }

  private void setTableExcessSince(T game, Instant instant) {
    if (game instanceof CashGame cashGame) {
      cashGame.tableExcessSince(instant);
    }
  }

  private void clearTableExcessSince(T game) {
    if (game instanceof CashGame cashGame) {
      cashGame.tableExcessSince(null);
    }
  }
}
