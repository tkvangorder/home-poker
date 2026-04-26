package org.homepoker.recording;

import org.homepoker.game.GameSettings;
import org.homepoker.game.cash.CashGameManager;
import org.homepoker.game.cash.CashGameService;
import org.homepoker.model.command.PlayerActionCommand;
import org.homepoker.model.command.StartGame;
import org.homepoker.model.game.GameStatus;
import org.homepoker.model.game.GameType;
import org.homepoker.model.game.Player;
import org.homepoker.model.game.PlayerAction;
import org.homepoker.model.game.PlayerStatus;
import org.homepoker.model.game.Seat;
import org.homepoker.model.game.Table;
import org.homepoker.model.game.cash.CashGame;
import org.homepoker.model.user.User;
import org.homepoker.test.BaseIntegrationTest;
import org.homepoker.test.TestDataHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class EventRecorderIntegrationTest extends BaseIntegrationTest {

  @Autowired EventRecorderService eventRecorderService;
  @Autowired EventRecorderRepository eventRecorderRepository;
  @Autowired CashGameService cashGameService;

  @Test
  void capturesEverySingleEventAcrossAGameLoopRun() {
    User owner = createUser(TestDataHelper.user("admin", "password", "Admin"));
    String gameId = "recording-it-" + System.currentTimeMillis();

    // Build a 5-player single-table game in SEATING.
    CashGame game = CashGame.builder()
        .id(gameId)
        .name("Recording IT")
        .type(GameType.TEXAS_HOLDEM)
        .status(GameStatus.SEATING)
        .startTime(Instant.now())
        .maxBuyIn(10000)
        .smallBlind(25)
        .bigBlind(50)
        .owner(owner)
        .build();

    Table table = Table.builder()
        .id("TABLE-0")
        .emptySeats(GameSettings.TEXAS_HOLDEM_SETTINGS.numberOfSeats())
        .status(Table.Status.PAUSED)
        .build();
    table.dealerPosition(5);
    game.tables().put(table.id(), table);

    for (int i = 0; i < 5; i++) {
      String uid = "rec-player-" + i;
      User u = createUser(TestDataHelper.user(uid, "password", "Player " + uid));
      Player p = Player.builder()
          .user(u).status(PlayerStatus.ACTIVE)
          .chipCount(10000).buyInTotal(10000).reBuys(0).addOns(0).build();
      game.addPlayer(p);
      Seat s = table.seats().get(i);
      s.status(Seat.Status.JOINED_WAITING);
      s.player(p);
      p.tableId(table.id());
    }

    cashGameRepository.save(game);

    CashGameManager manager = cashGameService.getGameManger(gameId);
    manager.submitCommand(new StartGame(gameId, owner));
    manager.processGameTick();
    manager.processGameTick();

    // Wait for the async worker to flush — there is no synchronous flush API by design.
    // Scope the wait to this game's events: the TestContainers Mongo is shared across
    // BaseIntegrationTest subclasses, so other tests' rows may also be in the collection.
    await().atMost(Duration.ofSeconds(5)).until(() -> {
      long writtenForGame = eventRecorderRepository.findAll().stream()
          .filter(r -> gameId.equals(r.gameId()))
          .count();
      return writtenForGame > 0
          && eventRecorderService.writtenEventCount() >= eventRecorderRepository.count();
    });

    List<RecordedEvent> all = eventRecorderRepository.findAll().stream()
        .filter(r -> gameId.equals(r.gameId()))
        .toList();
    assertThat(all).isNotEmpty();

    // Game-level events: tableId == null AND handNumber == null
    assertThat(all)
        .filteredOn(r -> "game-status-changed".equals(r.eventType()))
        .allSatisfy(r -> {
          assertThat(r.tableId()).isNull();
          assertThat(r.handNumber()).isNull();
          assertThat(r.gameId()).isEqualTo(gameId);
        });

    // HandStarted events: tag with their own handNumber
    assertThat(all)
        .filteredOn(r -> "hand-started".equals(r.eventType()))
        .allSatisfy(r -> {
          assertThat(r.tableId()).isEqualTo("TABLE-0");
          assertThat(r.handNumber()).isNotNull();
        });

    // HoleCardsDealt: every player's private deal is captured (5 players → 5 events)
    assertThat(all)
        .filteredOn(r -> "hole-cards-dealt".equals(r.eventType()))
        .hasSize(5)
        .allSatisfy(r -> {
          assertThat(r.userId()).isNotNull();
          assertThat(r.tableId()).isEqualTo("TABLE-0");
          assertThat(r.handNumber()).isNotNull();
        });

    // sequenceNumber is non-zero for stamped broadcast events (HandStarted) and zero for UserEvents.
    assertThat(all)
        .filteredOn(r -> "hand-started".equals(r.eventType()))
        .allSatisfy(r -> assertThat(r.sequenceNumber()).isPositive());
    assertThat(all)
        .filteredOn(r -> "hole-cards-dealt".equals(r.eventType()))
        .allSatisfy(r -> assertThat(r.sequenceNumber()).isZero());
  }

  /**
   * Simulates a server restart while a hand is in progress: drive a game to mid-hand, persist
   * its state, drop the cached {@code CashGameManager}, then re-acquire it. The reconstructed
   * manager's {@code EventRecorder} runs {@code seedHandTracker()} during construction. After
   * the restart, the next event captured for that table must carry {@code handNumber = 1} —
   * proving the seed correctly recovered the open hand from Mongo.
   */
  @Test
  void afterRestartCurrentHandByTableIsSeededFromRecording() {
    User owner = createUser(TestDataHelper.user("admin", "password", "Admin"));
    String gameId = "recording-restart-it-" + System.nanoTime();

    CashGame game = CashGame.builder()
        .id(gameId)
        .name("Recording Restart IT")
        .type(GameType.TEXAS_HOLDEM)
        .status(GameStatus.SEATING)
        .startTime(Instant.now())
        .maxBuyIn(10000)
        .smallBlind(25)
        .bigBlind(50)
        .owner(owner)
        .build();

    Table table = Table.builder()
        .id("TABLE-0")
        .emptySeats(GameSettings.TEXAS_HOLDEM_SETTINGS.numberOfSeats())
        .status(Table.Status.PAUSED)
        .build();
    table.dealerPosition(5);
    game.tables().put(table.id(), table);

    for (int i = 0; i < 5; i++) {
      String uid = "rec-restart-player-" + i;
      User u = createUser(TestDataHelper.user(uid, "password", "Player " + uid));
      Player p = Player.builder()
          .user(u).status(PlayerStatus.ACTIVE)
          .chipCount(10000).buyInTotal(10000).reBuys(0).addOns(0).build();
      game.addPlayer(p);
      Seat s = table.seats().get(i);
      s.status(Seat.Status.JOINED_WAITING);
      s.player(p);
      p.tableId(table.id());
    }

    cashGameRepository.save(game);

    // Phase 1: drive into a hand. After tick 2, table is PRE_FLOP_BETTING with HandStarted(1).
    CashGameManager manager = cashGameService.getGameManger(gameId);
    manager.submitCommand(new StartGame(gameId, owner));
    manager.processGameTick();
    manager.processGameTick();

    // Persist the post-tick state explicitly so seedHandTracker() observes Table.Status.PLAYING
    // when the simulated restart occurs.
    cashGameRepository.save(manager.getGameForTest());

    // Wait for phase-1 writes to flush. Specifically, wait for HandStarted(handNumber=1) on TABLE-0.
    await().atMost(Duration.ofSeconds(5)).until(() ->
        eventRecorderRepository.findAll().stream().anyMatch(r ->
            gameId.equals(r.gameId())
                && "TABLE-0".equals(r.tableId())
                && r.handNumber() != null && r.handNumber() == 1
                && "hand-started".equals(r.eventType())));

    // Snapshot the recorded event count for this table BEFORE the restart so we can detect
    // a post-restart write below.
    long phase1TableEventCount = eventRecorderRepository.findAll().stream()
        .filter(r -> gameId.equals(r.gameId()) && "TABLE-0".equals(r.tableId()))
        .count();

    // Phase 2: simulate restart by dropping the cached manager and re-acquiring it. The
    // reconstructed manager's constructor runs seedHandTracker() before any tick.
    cashGameService.invalidateGameManagerForTest(gameId);
    CashGameManager fresh = cashGameService.getGameManger(gameId);

    // Drive one more TableEvent: submit a Fold for the seat currently on the clock.
    Table freshTable = fresh.getGameForTest().tables().get("TABLE-0");
    int actionPos = freshTable.actionPosition();
    Seat actionSeat = freshTable.seatAt(actionPos);
    User actor = actionSeat.player().user();
    fresh.submitCommand(new PlayerActionCommand(gameId, "TABLE-0", actor, new PlayerAction.Fold()));
    fresh.processGameTick();

    // Wait until the post-restart events have flushed (count strictly grew).
    await().atMost(Duration.ofSeconds(5)).until(() ->
        eventRecorderRepository.findAll().stream()
            .filter(r -> gameId.equals(r.gameId()) && "TABLE-0".equals(r.tableId()))
            .count() > phase1TableEventCount);

    // Collect all events for this game/table written AFTER the restart (i.e., new entries
    // beyond the snapshot). With the seed working, every TableEvent emitted in this hand
    // (e.g., PlayerActed) must be tagged with handNumber=1 — NOT null.
    List<RecordedEvent> phase2Events = eventRecorderRepository.findAll().stream()
        .filter(r -> gameId.equals(r.gameId()) && "TABLE-0".equals(r.tableId()))
        .sorted((a, b) -> a.recordedAt().compareTo(b.recordedAt()))
        .skip(phase1TableEventCount)
        .toList();

    assertThat(phase2Events)
        .as("post-restart events for TABLE-0 must carry handNumber=1 (seed recovered the open hand)")
        .isNotEmpty()
        .allSatisfy(r -> assertThat(r.handNumber())
            .as("event %s should be tagged with handNumber=1", r.eventType())
            .isEqualTo(1));
  }
}
