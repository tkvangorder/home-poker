package org.homepoker.test;

import org.homepoker.game.GameListener;
import org.homepoker.game.GameManager;
import org.homepoker.game.GameSettings;
import org.homepoker.model.command.PlayerActionCommand;
import org.homepoker.model.command.StartGame;
import org.homepoker.model.event.PokerEvent;
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

  /** All events captured by the test listener since the fixture was built. */
  public List<PokerEvent> savedEvents() {
    return manager.savedEvents();
  }

  /** Underlying manager — exposed for tests that need to drive additional ticks. */
  public TestableGameManager manager() {
    return manager;
  }

  // ------------------------------------------------------------------
  // Construction
  // ------------------------------------------------------------------

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
  }
}
