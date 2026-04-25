package org.homepoker.test;

import org.homepoker.game.GameListener;
import org.homepoker.game.GameManager;
import org.homepoker.game.GameSettings;
import org.homepoker.game.table.TableManager;
import org.homepoker.model.command.GameCommand;
import org.homepoker.model.command.PlayerActionCommand;
import org.homepoker.model.command.StartGame;
import org.homepoker.model.event.PokerEvent;
import org.homepoker.model.event.TableEvent;
import org.homepoker.model.game.GameStatus;
import org.homepoker.model.game.GameType;
import org.homepoker.model.game.HandPhase;
import org.homepoker.model.game.Player;
import org.homepoker.model.game.PlayerAction;
import org.homepoker.model.game.PlayerStatus;
import org.homepoker.model.game.Seat;
import org.homepoker.model.game.Table;
import org.homepoker.model.game.cash.CashGame;
import org.homepoker.model.user.User;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Test fixture that builds a deterministic, in-memory {@link GameManager} for unit tests.
 * <p>
 * Provides convenience scenarios that drive the game loop synchronously (no Spring, no DB,
 * no executors) so tests can submit a command, tick, and assert state/events without sleeps.
 * <p>
 * Use {@link #twoTablesWithHandPlayed()} for multi-table sequence-stream coverage. The game
 * is configured with two 9-seat tables seated 5-and-5 (10 players total) so the
 * {@code tablesNeedBalancing} check does not fire (ceil(10/9)=2 minimum tables, 2 current,
 * counts equal). One hand is then played to completion at each table by folding everyone
 * except the big blind.
 * <p>
 * The fixture installs a single capturing {@link GameListener} that records every event
 * emitted from {@link GameManager#processGameTick()} (after the fan-out stamping). Access
 * the captured events via {@link #savedEvents()}.
 */
public final class GameManagerTestFixture {

  private final TestableGameManager manager;

  private GameManagerTestFixture(TestableGameManager manager) {
    this.manager = manager;
  }

  /**
   * Convenience scenario: two tables, 5 players each, one hand played to completion on
   * each table. Players fold around to the big blind. Balancing is suppressed by sizing
   * the tables so the optimal table count equals the actual table count.
   */
  public static GameManagerTestFixture twoTablesWithHandPlayed() {
    GameManagerTestFixture fixture = new GameManagerTestFixture(buildTwoTableManager());
    fixture.playFoldAroundHandOnEachTable();
    return fixture;
  }

  /**
   * Convenience scenario: a single table with 5 players, advanced to mid-hand
   * (PRE_FLOP_BETTING with action on UTG). Several TableEvents have already been
   * stamped on the table's stream (HandPhaseChanged, HandStarted, ActionOnPlayer, etc.),
   * so {@link #lastTableSeq(String)} for the single table will be {@code > 0}.
   */
  public static GameManagerTestFixture singleTableMidHand() {
    return new GameManagerTestFixture(buildSingleTableMidHandManager());
  }

  /** All events captured by the test listener since the fixture was built. */
  public List<PokerEvent> savedEvents() {
    return manager.savedEvents();
  }

  /** Underlying manager — exposed for tests that need to drive additional ticks. */
  public TestableGameManager manager() {
    return manager;
  }

  /** ID of the game under test. */
  public String gameId() {
    return manager.getGame().id();
  }

  /**
   * ID of the (first) table in the game. Useful for the single-table scenarios where
   * tests don't care about the specific identifier.
   */
  public String tableId() {
    return manager.getGame().tables().firstKey();
  }

  /**
   * The user for the player seated at seat 1 of the first table — typed for tests that
   * just need any non-admin player identity to submit player-scoped commands like
   * {@code GetTableState}.
   */
  public User player1() {
    Table table = manager.getGame().tables().firstEntry().getValue();
    Seat seat = table.seatAt(1);
    if (seat == null || seat.player() == null) {
      throw new IllegalStateException("Seat 1 has no player");
    }
    return seat.player().user();
  }

  /** Submit a command to the underlying manager. */
  public void submitCommand(GameCommand command) {
    manager.submitCommand(command);
  }

  /** Process one game tick on the underlying manager. */
  public void tick() {
    manager.processGameTick();
  }

  /**
   * The current (most-recently-assigned) stream-seq for the named table. {@code 0} means
   * no TableEvent has been stamped on this table yet.
   */
  public long lastTableSeq(String tableId) {
    TableManager<CashGame> tm = manager.tableManagerFor(tableId);
    if (tm == null) {
      throw new IllegalArgumentException("No such table: " + tableId);
    }
    return tm.currentStreamSeq();
  }

  /** The most-recently-saved event captured by the listener. */
  public PokerEvent lastSavedEvent() {
    List<PokerEvent> events = manager.savedEvents();
    if (events.isEmpty()) {
      throw new IllegalStateException("No events captured yet");
    }
    return events.getLast();
  }

  /**
   * Drive the game forward by exactly one TableEvent on the named table, leaving that
   * event as the very last entry in {@link #savedEvents()}.
   * <p>
   * Implementation: submits a Fold for the current action seat at the table and ticks
   * the game loop. A single tick can emit several TableEvents (e.g. {@code PlayerActed}
   * followed by {@code ActionOnPlayer}) plus possible UserEvents — this method then
   * truncates {@code savedEvents} so the last entry is the FIRST new TableEvent for
   * the requested table. That gives tests a deterministic "next event" anchor against
   * which to assert sequence numbers.
   */
  public void driveOneMoreTableEvent(String tableId) {
    Table table = manager.getGame().tables().get(tableId);
    if (table == null) {
      throw new IllegalArgumentException("No such table: " + tableId);
    }
    if (table.actionPosition() == null) {
      throw new IllegalStateException(
          "Table " + tableId + " has no action position; cannot drive a player action");
    }
    Seat actionSeat = table.seatAt(table.actionPosition());
    if (actionSeat == null || actionSeat.player() == null) {
      throw new IllegalStateException(
          "Action position seat has no player; cannot drive a player action");
    }

    int sizeBefore = manager.savedEvents().size();
    manager.submitCommand(new PlayerActionCommand(
        manager.getGame().id(), tableId, actionSeat.player().user(), new PlayerAction.Fold()));
    manager.processGameTick();

    // Find the first new TableEvent for the requested table and trim to it.
    List<PokerEvent> events = manager.savedEvents();
    int firstNewTableEventIndex = -1;
    for (int i = sizeBefore; i < events.size(); i++) {
      PokerEvent event = events.get(i);
      if (event instanceof TableEvent te && tableId.equals(te.tableId())) {
        firstNewTableEventIndex = i;
        break;
      }
    }
    if (firstNewTableEventIndex < 0) {
      throw new AssertionError(
          "Expected at least one TableEvent for table " + tableId + " after driving one tick, got none");
    }
    // Drop everything after the first new TableEvent so lastSavedEvent() returns it.
    while (events.size() > firstNewTableEventIndex + 1) {
      events.removeLast();
    }
  }

  // ------------------------------------------------------------------
  // Construction
  // ------------------------------------------------------------------

  private static TestableGameManager buildSingleTableMidHandManager() {
    User owner = TestDataHelper.adminUser();
    CashGame game = CashGame.builder()
        .id("test-game")
        .name("Single Table Mid-Hand Test Game")
        .type(GameType.TEXAS_HOLDEM)
        .status(GameStatus.SEATING)
        .startTime(Instant.now())
        .maxBuyIn(10000)
        .smallBlind(25)
        .bigBlind(50)
        .owner(owner)
        .build();

    seatTable(game, "TABLE-0", 5);

    TestableGameManager manager = new TestableGameManager(game);
    // Start the game.
    manager.submitCommand(new StartGame(game.id(), owner));
    // Tick 1: drains StartGame, transitions SEATING -> ACTIVE.
    manager.processGameTick();
    // Tick 2: transitionFromActive deals the first hand. Several TableEvents are stamped
    // (HandPhaseChanged, HandStarted, ActionOnPlayer, etc.), leaving the table in
    // PRE_FLOP_BETTING with action on UTG.
    manager.processGameTick();
    return manager;
  }

  private static TestableGameManager buildTwoTableManager() {
    User owner = TestDataHelper.adminUser();
    // Start in SEATING so that submitting StartGame produces a game-level GameStatusChanged
    // (and GameMessage) — that exercises the game-stream sequence counter independently of
    // the per-table streams.
    CashGame game = CashGame.builder()
        .id("test-game")
        .name("Two Table Test Game")
        .type(GameType.TEXAS_HOLDEM)
        .status(GameStatus.SEATING)
        .startTime(Instant.now())
        .maxBuyIn(10000)
        .smallBlind(25)
        .bigBlind(50)
        .owner(owner)
        .build();

    seatTable(game, "TABLE-0", 5);
    seatTable(game, "TABLE-1", 5);

    TestableGameManager manager = new TestableGameManager(game);
    // Start the game — this fires GameStatusChanged(SEATING → ACTIVE), TableStatusChanged
    // for each table (PAUSED → PLAYING), and a GameMessage.
    manager.submitCommand(new StartGame(game.id(), owner));
    return manager;
  }

  private static void seatTable(CashGame game, String tableId, int playerCount) {
    Table table = Table.builder()
        .id(tableId)
        .emptySeats(GameSettings.TEXAS_HOLDEM_SETTINGS.numberOfSeats())
        .status(Table.Status.PAUSED)
        .build();

    // Pre-set dealer so the rotation lands deterministically on seat 1 after the
    // first hand (matches what TexasHoldemTableManagerTest does).
    table.dealerPosition(playerCount);

    game.tables().put(table.id(), table);

    for (int i = 0; i < playerCount; i++) {
      String uniqueId = tableId + "-player-" + i;
      User user = TestDataHelper.user(uniqueId, "password", "Player " + uniqueId);
      Player player = Player.builder()
          .user(user)
          .status(PlayerStatus.ACTIVE)
          .chipCount(10000)
          .buyInTotal(10000)
          .reBuys(0)
          .addOns(0)
          .build();
      game.addPlayer(player);

      Seat seat = table.seats().get(i);
      seat.status(Seat.Status.JOINED_WAITING);
      seat.player(player);
      player.tableId(tableId);
    }
  }

  // ------------------------------------------------------------------
  // Hand driver
  // ------------------------------------------------------------------

  /**
   * Drives one hand to completion on every table by folding the action seat each tick
   * until the hand is over. With 5 active players per table, this is at most ~10 ticks.
   * <p>
   * The first tick processes the queued {@code StartGame} command (SEATING → ACTIVE) but
   * does not deal a hand (the transition switch enters {@code SEATING} and does not fall
   * through to {@code ACTIVE} in the same tick). The second tick then triggers
   * {@code transitionFromActive}, which deals on every table.
   */
  private void playFoldAroundHandOnEachTable() {
    // Tick 1: drains StartGame, transitions SEATING → ACTIVE, sets tables to PLAYING.
    manager.processGameTick();
    // Tick 2: transitionFromActive deals the first hand on every table simultaneously.
    manager.processGameTick();

    int safety = 100;
    while (safety-- > 0) {
      boolean anyTableInBetting = false;
      for (Table table : manager.getGame().tables().values()) {
        if (isBettingPhase(table.handPhase()) && table.actionPosition() != null) {
          anyTableInBetting = true;
          Seat seat = table.seatAt(table.actionPosition());
          if (seat.player() != null && seat.status() == Seat.Status.ACTIVE) {
            manager.submitCommand(new PlayerActionCommand(
                manager.getGame().id(), table.id(), seat.player().user(), new PlayerAction.Fold()));
          }
        }
      }
      if (!anyTableInBetting) {
        // Every table has either completed its hand or is between hands; one tick to
        // settle (e.g. emit HandComplete) and we're done.
        manager.processGameTick();
        return;
      }
      manager.processGameTick();
    }
    throw new AssertionError("Hand did not complete on both tables within safety budget");
  }

  private static boolean isBettingPhase(HandPhase phase) {
    return phase == HandPhase.PRE_FLOP_BETTING
        || phase == HandPhase.FLOP_BETTING
        || phase == HandPhase.TURN_BETTING
        || phase == HandPhase.RIVER_BETTING;
  }

  // ------------------------------------------------------------------
  // TestableGameManager
  // ------------------------------------------------------------------

  /**
   * In-memory {@link GameManager} that does not require Spring or a database. Persistence
   * is a no-op and a built-in listener captures every event for assertion.
   */
  public static final class TestableGameManager extends GameManager<CashGame> {

    private final List<PokerEvent> savedEvents = new ArrayList<>();

    public TestableGameManager(CashGame game) {
      super(game, null, null);
      addGameListener(new GameListener() {
        @Override
        public String userId() {
          return "test-listener";
        }

        @Override
        public boolean acceptsEvent(PokerEvent event) {
          return true;
        }

        @Override
        public void onEvent(PokerEvent event) {
          savedEvents.add(event);
        }
      });
    }

    @Override
    protected CashGame persistGameState(CashGame game) {
      return game;
    }

    public List<PokerEvent> savedEvents() {
      return savedEvents;
    }

    public CashGame getGame() {
      return game();
    }

    /** Look up the table manager for the given tableId (test-only accessor). */
    public TableManager<CashGame> tableManagerFor(String tableId) {
      return tableManagers().get(tableId);
    }
  }
}
