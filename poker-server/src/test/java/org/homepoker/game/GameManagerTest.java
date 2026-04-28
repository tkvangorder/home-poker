package org.homepoker.game;

import org.homepoker.game.cash.CashGameManager;
import org.homepoker.game.cash.CashGameService;
import org.homepoker.model.command.*;
import org.homepoker.model.event.game.PlayerJoined;
import org.homepoker.model.event.PokerEvent;
import org.homepoker.model.event.game.GameMessage;
import org.homepoker.model.event.game.GameStatusChanged;
import org.homepoker.model.event.game.PlayerBuyIn;
import org.homepoker.model.game.*;
import org.homepoker.model.game.cash.CashGame;
import org.homepoker.model.user.User;
import org.homepoker.security.SecurityUtilities;
import org.homepoker.test.TestDataHelper;
import org.homepoker.user.UserManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the game-level state machine in GameManager.
 * Uses a TestableGameManager that does not require Spring or a database.
 */
@ExtendWith(MockitoExtension.class)
public class GameManagerTest {

  @Mock
  private CashGameService cashGameService;

  @Mock
  private UserManager userManager;

  @Mock
  private SecurityUtilities securityUtilities;

  private User adminUser;
  private User regularUser;

  @BeforeEach
  void setUp() {
    adminUser = TestDataHelper.adminUser();
    regularUser = TestDataHelper.user("user1", "password", "User One");
  }

  // --- SCHEDULED -> SEATING ---

  @Test
  void scheduledToSeating_whenStartTimeApproaches() {
    // Game starts "now", so seatingTimeSeconds (60) ago is in the past
    CashGame game = buildGame(GameStatus.SCHEDULED, Instant.now(), 3);

    TestableGameManager manager = createManager(game);
    manager.processGameTick();

    assertThat(manager.getGame().status()).isEqualTo(GameStatus.SEATING);
    assertThat(manager.getGame().tables()).isNotEmpty();
    // All tables should be PAUSED in SEATING state
    manager.getGame().tables().values().forEach(table ->
        assertThat(table.status()).isEqualTo(Table.Status.PAUSED)
    );
    assertThat(manager.savedEvents()).anyMatch(e -> e instanceof GameStatusChanged gsc &&
        gsc.oldStatus() == GameStatus.SCHEDULED && gsc.newStatus() == GameStatus.SEATING);
  }

  @Test
  void scheduledToSeating_doesNotTransitionIfStartTimeIsFarInFuture() {
    CashGame game = buildGame(GameStatus.SCHEDULED, Instant.now().plus(1, ChronoUnit.HOURS), 3);

    TestableGameManager manager = createManager(game);
    manager.processGameTick();

    assertThat(manager.getGame().status()).isEqualTo(GameStatus.SCHEDULED);
  }

  @Test
  void scheduledToSeating_playersAreDistributedToSeats() {
    CashGame game = buildGame(GameStatus.SCHEDULED, Instant.now(), 5);

    TestableGameManager manager = createManager(game);
    manager.processGameTick();

    assertThat(manager.getGame().status()).isEqualTo(GameStatus.SEATING);

    // Verify all players have been assigned to tables
    for (Player player : manager.getGame().players().values()) {
      assertThat(player.tableId()).isNotNull();
    }
  }

  // --- SEATING -> ACTIVE ---

  @Test
  void seatingToActive_whenStartGameCommandAndPlayersReady() {
    CashGame game = buildGameInSeating(3);

    TestableGameManager manager = createManager(game);
    manager.submitCommand(new StartGame(game.id(), adminUser));
    manager.processGameTick();

    assertThat(manager.getGame().status()).isEqualTo(GameStatus.ACTIVE);
    manager.getGame().tables().values().forEach(table ->
        assertThat(table.status()).isEqualTo(Table.Status.PLAYING)
    );
    assertThat(manager.savedEvents()).anyMatch(e -> e instanceof GameStatusChanged gsc &&
        gsc.oldStatus() == GameStatus.SEATING && gsc.newStatus() == GameStatus.ACTIVE);
  }

  @Test
  void seatingToActive_failsWithoutAdmin() {
    CashGame game = buildGameInSeating(3);

    TestableGameManager manager = createManager(game);
    manager.submitCommand(new StartGame(game.id(), regularUser));
    manager.processGameTick();

    // Should remain in SEATING (validation error sent as UserMessage)
    assertThat(manager.getGame().status()).isEqualTo(GameStatus.SEATING);
  }

  @Test
  void seatingToActive_failsWithInsufficientPlayers() {
    CashGame game = buildGameInSeating(1);

    TestableGameManager manager = createManager(game);
    manager.submitCommand(new StartGame(game.id(), adminUser));
    manager.processGameTick();

    // Should remain in SEATING
    assertThat(manager.getGame().status()).isEqualTo(GameStatus.SEATING);
  }

  @Test
  void seatingToActive_failsWhenStartTimeNotReached() {
    CashGame game = buildGameInSeating(3);
    // Set start time far in the future
    game.startTime(Instant.now().plus(1, ChronoUnit.HOURS));

    TestableGameManager manager = createManager(game);
    manager.submitCommand(new StartGame(game.id(), adminUser));
    manager.processGameTick();

    // Should remain in SEATING because startTime not reached
    assertThat(manager.getGame().status()).isEqualTo(GameStatus.SEATING);
  }

  // --- ACTIVE -> PAUSED (two-phase) ---

  @Test
  void activeToPaused_immediateWhenNoHandInProgress() {
    // When no hand is in progress (players haven't bought in), pause happens immediately
    CashGame game = buildGameInActive(3);

    TestableGameManager manager = createManager(game);

    // Issue PauseGame command
    manager.submitCommand(new PauseGame(game.id(), adminUser));
    manager.processGameTick();

    // With no hand in progress, tables transition PAUSE_AFTER_HAND -> PAUSED immediately
    // which triggers the game to transition to PAUSED in the same tick
    assertThat(manager.getGame().status()).isEqualTo(GameStatus.PAUSED);
    manager.getGame().tables().values().forEach(table ->
        assertThat(table.status()).isEqualTo(Table.Status.PAUSED)
    );
    assertThat(manager.savedEvents()).anyMatch(e -> e instanceof GameStatusChanged gsc &&
        gsc.oldStatus() == GameStatus.ACTIVE && gsc.newStatus() == GameStatus.PAUSED);
  }

  @Test
  void pauseGame_failsForNonAdmin() {
    CashGame game = buildGameInActive(3);

    TestableGameManager manager = createManager(game);
    manager.submitCommand(new PauseGame(game.id(), regularUser));
    manager.processGameTick();

    assertThat(manager.getGame().status()).isEqualTo(GameStatus.ACTIVE);
  }

  @Test
  void pauseGame_failsWhenNotActive() {
    CashGame game = buildGameInSeating(3);

    TestableGameManager manager = createManager(game);
    manager.submitCommand(new PauseGame(game.id(), adminUser));
    manager.processGameTick();

    assertThat(manager.getGame().status()).isEqualTo(GameStatus.SEATING);
  }

  // --- PAUSED -> ACTIVE (ResumeGame) ---

  @Test
  void pausedToActive_whenResumeGameCommand() {
    CashGame game = buildGameInPaused(3);

    TestableGameManager manager = createManager(game);
    manager.submitCommand(new ResumeGame(game.id(), adminUser));
    manager.processGameTick();

    assertThat(manager.getGame().status()).isEqualTo(GameStatus.ACTIVE);
    manager.getGame().tables().values().forEach(table ->
        assertThat(table.status()).isEqualTo(Table.Status.PLAYING)
    );
    assertThat(manager.savedEvents()).anyMatch(e -> e instanceof GameStatusChanged gsc &&
        gsc.oldStatus() == GameStatus.PAUSED && gsc.newStatus() == GameStatus.ACTIVE);
  }

  @Test
  void resumeGame_failsForNonAdmin() {
    CashGame game = buildGameInPaused(3);

    TestableGameManager manager = createManager(game);
    manager.submitCommand(new ResumeGame(game.id(), regularUser));
    manager.processGameTick();

    assertThat(manager.getGame().status()).isEqualTo(GameStatus.PAUSED);
  }

  @Test
  void resumeGame_failsWhenNotPaused() {
    CashGame game = buildGameInActive(3);

    TestableGameManager manager = createManager(game);
    manager.submitCommand(new ResumeGame(game.id(), adminUser));
    manager.processGameTick();

    // Should remain ACTIVE
    assertThat(manager.getGame().status()).isEqualTo(GameStatus.ACTIVE);
  }

  // --- EndGame from various states ---

  @Test
  void endGame_fromScheduled() {
    CashGame game = buildGame(GameStatus.SCHEDULED, Instant.now().plus(1, ChronoUnit.HOURS), 3);

    TestableGameManager manager = createManager(game);
    manager.submitCommand(new EndGame(game.id(), adminUser));
    manager.processGameTick();

    assertThat(manager.getGame().status()).isEqualTo(GameStatus.COMPLETED);
  }

  @Test
  void endGame_fromSeating() {
    CashGame game = buildGameInSeating(3);

    TestableGameManager manager = createManager(game);
    manager.submitCommand(new EndGame(game.id(), adminUser));
    manager.processGameTick();

    assertThat(manager.getGame().status()).isEqualTo(GameStatus.COMPLETED);
  }

  @Test
  void endGame_fromPaused() {
    CashGame game = buildGameInPaused(3);

    TestableGameManager manager = createManager(game);
    manager.submitCommand(new EndGame(game.id(), adminUser));
    manager.processGameTick();

    assertThat(manager.getGame().status()).isEqualTo(GameStatus.COMPLETED);
  }

  @Test
  void endGame_fromActive_immediateWhenNoHandInProgress() {
    // When no hand is in progress (players haven't bought in), end happens immediately
    CashGame game = buildGameInActive(3);

    TestableGameManager manager = createManager(game);
    manager.submitCommand(new EndGame(game.id(), adminUser));
    manager.processGameTick();

    // With no hand in progress, tables transition PAUSE_AFTER_HAND -> PAUSED immediately
    // which triggers the game to transition to COMPLETED in the same tick
    assertThat(manager.getGame().status()).isEqualTo(GameStatus.COMPLETED);
  }

  @Test
  void endGame_failsForNonAdmin() {
    CashGame game = buildGameInActive(3);

    TestableGameManager manager = createManager(game);
    manager.submitCommand(new EndGame(game.id(), regularUser));
    manager.processGameTick();

    assertThat(manager.getGame().status()).isEqualTo(GameStatus.ACTIVE);
  }

  @Test
  void endGame_failsWhenAlreadyCompleted() {
    CashGame game = buildGame(GameStatus.COMPLETED, Instant.now(), 0);

    TestableGameManager manager = createManager(game);
    manager.submitCommand(new EndGame(game.id(), adminUser));
    manager.processGameTick();

    // Should remain COMPLETED with a validation error event
    assertThat(manager.getGame().status()).isEqualTo(GameStatus.COMPLETED);
  }

  // --- BuyIn command ---

  @Test
  void buyIn_addsChipsToPlayer() {
    CashGame game = buildGameInSeating(2);
    Player player = game.players().values().iterator().next();
    User playerUser = player.user();
    player.chipCount(0);
    player.status(PlayerStatus.AWAY);

    TestableGameManager manager = createManager(game);
    manager.submitCommand(new BuyIn(game.id(), playerUser, 5000));
    manager.processGameTick();

    Player updatedPlayer = manager.getGame().players().get(playerUser.id());
    assertThat(updatedPlayer.chipCount()).isEqualTo(5000);
    assertThat(updatedPlayer.status()).isEqualTo(PlayerStatus.ACTIVE);
    assertThat(manager.savedEvents()).anyMatch(e -> e instanceof PlayerBuyIn pbi &&
        pbi.amount() == 5000 && pbi.newChipCount() == 5000);
  }

  @Test
  void buyIn_failsWhenAmountExceedsMax() {
    CashGame game = buildGameInSeating(2);
    Player player = game.players().values().iterator().next();
    User playerUser = player.user();
    player.chipCount(0);

    TestableGameManager manager = createManager(game);
    // maxBuyIn is 10000
    manager.submitCommand(new BuyIn(game.id(), playerUser, 20000));
    manager.processGameTick();

    // Chip count should remain 0
    Player updatedPlayer = manager.getGame().players().get(playerUser.id());
    assertThat(updatedPlayer.chipCount()).isEqualTo(0);
  }

  @Test
  void buyIn_failsWhenChipCountWouldExceedMax() {
    CashGame game = buildGameInSeating(2);
    Player player = game.players().values().iterator().next();
    User playerUser = player.user();
    player.chipCount(8000);
    player.status(PlayerStatus.ACTIVE);

    TestableGameManager manager = createManager(game);
    // maxBuyIn is 10000, player has 8000, adding 5000 would be 13000
    manager.submitCommand(new BuyIn(game.id(), playerUser, 5000));
    manager.processGameTick();

    // Chip count should remain 8000
    Player updatedPlayer = manager.getGame().players().get(playerUser.id());
    assertThat(updatedPlayer.chipCount()).isEqualTo(8000);
  }

  @Test
  void buyIn_failsWhenNotRegistered() {
    CashGame game = buildGameInSeating(2);
    User unregistered = TestDataHelper.user("unregistered", "password", "Unregistered");

    TestableGameManager manager = createManager(game);
    manager.submitCommand(new BuyIn(game.id(), unregistered, 5000));
    manager.processGameTick();

    // No player changes
    assertThat(manager.getGame().players()).doesNotContainKey("unregistered");
  }

  @Test
  void buyIn_failsInScheduledState() {
    CashGame game = buildGame(GameStatus.SCHEDULED, Instant.now().plus(1, ChronoUnit.HOURS), 2);
    Player player = game.players().values().iterator().next();
    User playerUser = player.user();

    TestableGameManager manager = createManager(game);
    manager.submitCommand(new BuyIn(game.id(), playerUser, 5000));
    manager.processGameTick();

    // Should not change chip count (command rejected)
    assertThat(manager.getGame().status()).isEqualTo(GameStatus.SCHEDULED);
  }

  // --- LeaveGame command ---

  @Test
  void leaveGame_removesPlayerFromSeat() {
    CashGame game = buildGameInSeating(3);
    Player player = game.players().values().iterator().next();
    User playerUser = player.user();

    TestableGameManager manager = createManager(game);
    manager.submitCommand(new LeaveGame(game.id(), playerUser));
    manager.processGameTick();

    Player updatedPlayer = manager.getGame().players().get(playerUser.id());
    assertThat(updatedPlayer.status()).isEqualTo(PlayerStatus.OUT);
    assertThat(updatedPlayer.tableId()).isNull();
    assertThat(manager.savedEvents()).anyMatch(e -> e instanceof GameMessage gm &&
        gm.message().contains("has left the game"));
  }

  @Test
  void leaveGame_failsWhenNotRegistered() {
    CashGame game = buildGameInSeating(2);
    User unregistered = TestDataHelper.user("unregistered", "password", "Unregistered");

    TestableGameManager manager = createManager(game);
    manager.submitCommand(new LeaveGame(game.id(), unregistered));
    manager.processGameTick();

    // No changes
    assertThat(manager.getGame().players()).doesNotContainKey("unregistered");
  }

  // --- JoinGame during various states ---

  @Test
  void joinGame_duringSeating_doesNotSeatUntilBuyIn() {
    CashGame game = buildGameInSeating(2);
    User newUser = TestDataHelper.user("newPlayer", "password", "New Player");

    TestableGameManager manager = createManager(game);
    manager.submitCommand(new JoinGame(game.id(), newUser));
    manager.processGameTick();

    Player newPlayer = manager.getGame().players().get("newPlayer");
    assertThat(newPlayer).isNotNull();
    assertThat(newPlayer.status()).isEqualTo(PlayerStatus.AWAY);
    // Player should NOT be seated until they buy in
    assertThat(newPlayer.tableId()).isNull();
    assertThat(manager.savedEvents()).anyMatch(e -> e instanceof PlayerJoined uj &&
        uj.userId().equals("newPlayer"));

    // Now buy in — player should be seated
    manager.submitCommand(new BuyIn(game.id(), newUser, 1000));
    manager.processGameTick();

    assertThat(newPlayer.status()).isEqualTo(PlayerStatus.ACTIVE);
    assertThat(newPlayer.tableId()).isNotNull();
  }

  @Test
  void joinGame_duringActive_doesNotSeatUntilBuyIn() {
    CashGame game = buildGameInActive(2);
    User newUser = TestDataHelper.user("newPlayer", "password", "New Player");

    TestableGameManager manager = createManager(game);
    manager.submitCommand(new JoinGame(game.id(), newUser));
    manager.processGameTick();

    Player newPlayer = manager.getGame().players().get("newPlayer");
    assertThat(newPlayer).isNotNull();
    // Player should NOT be seated until they buy in
    assertThat(newPlayer.tableId()).isNull();

    // Now buy in — player should be seated
    manager.submitCommand(new BuyIn(game.id(), newUser, 1000));
    manager.processGameTick();

    assertThat(newPlayer.status()).isEqualTo(PlayerStatus.ACTIVE);
    assertThat(newPlayer.tableId()).isNotNull();
  }

  @Test
  void joinGame_duringScheduled_doesNotAssignSeat() {
    CashGame game = buildGame(GameStatus.SCHEDULED, Instant.now().plus(1, ChronoUnit.HOURS), 2);
    User newUser = TestDataHelper.user("newPlayer", "password", "New Player");

    TestableGameManager manager = createManager(game);
    manager.submitCommand(new JoinGame(game.id(), newUser));
    manager.processGameTick();

    Player newPlayer = manager.getGame().players().get("newPlayer");
    assertThat(newPlayer).isNotNull();
    assertThat(newPlayer.status()).isEqualTo(PlayerStatus.AWAY);
    // No tables yet in SCHEDULED, so no seat assignment
    assertThat(newPlayer.tableId()).isNull();
  }

  // --- LeaveGame during SCHEDULED ---

  @Test
  void leaveGame_duringScheduled_marksPlayerAsOut() {
    CashGame game = buildGame(GameStatus.SCHEDULED, Instant.now().plus(1, ChronoUnit.HOURS), 3);
    Player player = game.players().values().iterator().next();
    User playerUser = player.user();

    TestableGameManager manager = createManager(game);
    manager.submitCommand(new LeaveGame(game.id(), playerUser));
    manager.processGameTick();

    // Player should still exist but be marked OUT (for auditing)
    Player updatedPlayer = manager.getGame().players().get(playerUser.id());
    assertThat(updatedPlayer).isNotNull();
    assertThat(updatedPlayer.status()).isEqualTo(PlayerStatus.OUT);
    assertThat(manager.savedEvents()).anyMatch(e -> e instanceof GameMessage gm &&
        gm.message().contains("has left the game"));
  }

  @Test
  void joinGame_afterLeaving_allowsRejoin() {
    CashGame game = buildGameInSeating(3);
    Player player = game.players().values().iterator().next();
    User playerUser = player.user();

    TestableGameManager manager = createManager(game);

    // Leave the game
    manager.submitCommand(new LeaveGame(game.id(), playerUser));
    manager.processGameTick();
    assertThat(manager.getGame().players().get(playerUser.id()).status()).isEqualTo(PlayerStatus.OUT);

    // Rejoin the game
    manager.submitCommand(new JoinGame(game.id(), playerUser));
    manager.processGameTick();

    Player rejoinedPlayer = manager.getGame().players().get(playerUser.id());
    assertThat(rejoinedPlayer.status()).isEqualTo(PlayerStatus.AWAY);
    assertThat(rejoinedPlayer.tableId()).isNotNull();
  }

  // --- Table balancing ---

  @Test
  void tableBalancing_transitionsToBalancingState() {
    CashGame game = buildGameInActive(0);
    // Clear any auto-created tables and players from buildGameInActive
    game.tables().clear();
    game.players().clear();
    // Manually create two tables with imbalanced player counts
    // Use BUYING_IN player status so the table manager doesn't try to deal a hand
    Table table1 = buildTableWithPlayers("TABLE-0", 5, game, PlayerStatus.BUYING_IN);
    Table table2 = buildTableWithPlayers("TABLE-1", 2, game, PlayerStatus.BUYING_IN);
    game.tables().put(table1.id(), table1);
    game.tables().put(table2.id(), table2);

    TestableGameManager manager = createManager(game);
    manager.processGameTick();

    // Game should transition to BALANCING and tables set to PAUSE_AFTER_HAND
    assertThat(manager.getGame().status()).isEqualTo(GameStatus.BALANCING);

    // Since no hands are in progress, tables immediately transition to PAUSED
    // and then the next tick redistributes and goes back to ACTIVE
    manager.processGameTick();

    assertThat(manager.getGame().status()).isEqualTo(GameStatus.ACTIVE);

    // After redistribution, tables should be within 1 player of each other
    int total = 0;
    int maxCount = Integer.MIN_VALUE;
    int minCount = Integer.MAX_VALUE;
    for (Table table : manager.getGame().tables().values()) {
      int count = table.numberOfPlayers();
      total += count;
      maxCount = Math.max(maxCount, count);
      minCount = Math.min(minCount, count);
    }
    assertThat(total).isEqualTo(7); // 5 + 2 original players
    assertThat(maxCount - minCount).isLessThanOrEqualTo(1);
  }

  @Test
  void lonePlayer_movesImmediatelyToTableWithEmptySeat() {
    CashGame game = buildGameInActive(0);
    game.tables().clear();
    game.players().clear();
    Table sourceTable = buildTableWithPlayers("TABLE-0", 1, game, PlayerStatus.BUYING_IN);
    Table destTable = buildTableWithPlayers("TABLE-1", 3, game, PlayerStatus.BUYING_IN);
    game.tables().put(sourceTable.id(), sourceTable);
    game.tables().put(destTable.id(), destTable);

    String movedUserId = sourceTable.seats().stream()
        .filter(s -> s.status() != Seat.Status.EMPTY)
        .findFirst().orElseThrow().player().userId();

    TestableGameManager manager = createManager(game);
    manager.processGameTick();

    // Game stays ACTIVE — no game-wide BALANCING needed for a lone-player move
    assertThat(manager.getGame().status()).isEqualTo(GameStatus.ACTIVE);
    // Source table is now empty; destination has the new player
    Table src = manager.getGame().tables().get("TABLE-0");
    Table dst = manager.getGame().tables().get("TABLE-1");
    if (src != null) {
      assertThat(src.numberOfPlayers()).isEqualTo(0);
    }
    assertThat(dst.numberOfPlayers()).isEqualTo(4);
    assertThat(manager.getGame().players().get(movedUserId).tableId()).isEqualTo("TABLE-1");
    assertThat(manager.savedEvents()).anyMatch(e ->
        e instanceof org.homepoker.model.event.game.PlayerMovedTables m
            && m.userId().equals(movedUserId)
            && "TABLE-0".equals(m.fromTableId())
            && "TABLE-1".equals(m.toTableId()));
  }

  @Test
  void lonePlayer_singleTable_doesNotMove() {
    CashGame game = buildGameInActive(0);
    game.tables().clear();
    game.players().clear();
    Table onlyTable = buildTableWithPlayers("TABLE-0", 1, game, PlayerStatus.BUYING_IN);
    game.tables().put(onlyTable.id(), onlyTable);

    TestableGameManager manager = createManager(game);
    manager.processGameTick();

    assertThat(manager.getGame().status()).isEqualTo(GameStatus.ACTIVE);
    assertThat(manager.getGame().tables()).containsKey("TABLE-0");
    assertThat(manager.getGame().tables().get("TABLE-0").numberOfPlayers()).isEqualTo(1);
    assertThat(manager.savedEvents()).noneMatch(e ->
        e instanceof org.homepoker.model.event.game.PlayerMovedTables);
  }

  // --- Helper methods ---

  private CashGame buildGame(GameStatus status, Instant startTime, int playerCount) {
    CashGame game = CashGame.builder()
        .id("test-game")
        .name("Test Game")
        .type(GameType.TEXAS_HOLDEM)
        .status(status)
        .startTime(startTime)
        .maxBuyIn(10000)
        .smallBlind(25)
        .bigBlind(50)
        .owner(adminUser)
        .build();

    List<Player> players = TestDataHelper.generatePlayers(game, playerCount);
    return game;
  }

  private CashGame buildGameInSeating(int playerCount) {
    // Build a game that has already transitioned to SEATING (with tables set up)
    CashGame game = buildGame(GameStatus.SCHEDULED, Instant.now(), playerCount);

    // Transition to SEATING by running a tick
    TestableGameManager tempManager = createManager(game);
    tempManager.processGameTick();

    return tempManager.game();
  }

  private CashGame buildGameInActive(int playerCount) {
    CashGame game = buildGameInSeating(Math.max(playerCount, 2));

    // Start the game
    TestableGameManager tempManager = createManager(game);
    tempManager.submitCommand(new StartGame(game.id(), adminUser));
    tempManager.processGameTick();

    return tempManager.game();
  }

  private CashGame buildGameInPaused(int playerCount) {
    CashGame game = buildGameInActive(playerCount);

    // With no hands in progress, PauseGame transitions immediately
    TestableGameManager tempManager = createManager(game);
    tempManager.submitCommand(new PauseGame(game.id(), adminUser));
    tempManager.processGameTick();

    return tempManager.game();
  }

  private Table buildTableWithPlayers(String tableId, int playerCount, CashGame game) {
    return buildTableWithPlayers(tableId, playerCount, game, PlayerStatus.ACTIVE);
  }

  private Table buildTableWithPlayers(String tableId, int playerCount, CashGame game, PlayerStatus playerStatus) {
    Table table = Table.builder()
        .id(tableId)
        .emptySeats(GameSettings.TEXAS_HOLDEM_SETTINGS.numberOfSeats())
        .status(Table.Status.PLAYING)
        .build();

    for (int i = 0; i < playerCount; i++) {
      String uniqueId = tableId + "-player-" + i;
      User user = TestDataHelper.user(uniqueId, "password", "Player " + uniqueId);
      Player player = Player.builder()
          .user(user)
          .status(playerStatus)
          .chipCount(10000)
          .buyInTotal(10000)
          .reBuys(0)
          .addOns(0)
          .build();
      game.addPlayer(player);

      // Assign to a seat
      List<Seat> emptySeats = table.seats().stream()
          .filter(seat -> seat.status() == Seat.Status.EMPTY)
          .toList();
      if (!emptySeats.isEmpty()) {
        Seat seat = emptySeats.getFirst();
        seat.status(Seat.Status.JOINED_WAITING);
        seat.player(player);
        player.tableId(tableId);
      }
    }
    return table;
  }

  private TestableGameManager createManager(CashGame game) {
    return new TestableGameManager(game, cashGameService, userManager, securityUtilities);
  }

  /**
   * A test-friendly GameManager that does not require Spring context or database.
   * Persistence is a no-op, and events are captured for assertion.
   */
  static class TestableGameManager extends CashGameManager {

    private final List<PokerEvent> savedEvents = new ArrayList<>();

    TestableGameManager(CashGame game, CashGameService cashGameService, UserManager userManager, SecurityUtilities securityUtilities) {
      super(game, cashGameService, userManager, securityUtilities, null);
      // Add a listener that captures all events
      addGameListener(new GameListener() {
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
      // No-op for unit tests, just return the game as-is
      return game;
    }

    List<PokerEvent> savedEvents() {
      return savedEvents;
    }

    // Access game via the protected final game() method from parent
    CashGame getGame() {
      return game();
    }
  }
}
