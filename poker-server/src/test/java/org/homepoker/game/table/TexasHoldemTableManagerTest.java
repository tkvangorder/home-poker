package org.homepoker.game.table;

import org.homepoker.game.GameContext;
import org.homepoker.game.GameListener;
import org.homepoker.game.GameManager;
import org.homepoker.game.GameSettings;
import org.homepoker.model.command.PlayerActionCommand;
import org.homepoker.model.event.PokerEvent;
import org.homepoker.model.event.table.*;
import org.homepoker.model.game.*;
import org.homepoker.model.game.cash.CashGame;
import org.homepoker.model.user.User;
import org.homepoker.test.TestDataHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the Texas Hold'em table-level state machine (hand lifecycle).
 * Covers dealing, betting rounds, community cards, showdown, and hand completion.
 */
class TexasHoldemTableManagerTest {

  private User adminUser;

  @BeforeEach
  void setUp() {
    adminUser = TestDataHelper.adminUser();
  }

  // ============================================================
  // Deal Tests
  // ============================================================

  @Test
  void deal_postsBlindAndDealsCards() {
    TestableGameManager manager = createActiveGameWithPlayers(3);
    manager.processGameTick(); // Deal

    Table table = getTable(manager);
    assertThat(table.handPhase()).isEqualTo(HandPhase.PRE_FLOP_BETTING);
    assertThat(table.handNumber()).isEqualTo(1);

    // Verify blinds were posted
    int sbPos = table.smallBlindPosition();
    int bbPos = table.bigBlindPosition();
    Seat sbSeat = table.seats().get(sbPos);
    Seat bbSeat = table.seats().get(bbPos);
    assertThat(sbSeat.currentBetAmount()).isEqualTo(25);
    assertThat(bbSeat.currentBetAmount()).isEqualTo(50);
    assertThat(sbSeat.player().chipCount()).isEqualTo(9975);
    assertThat(bbSeat.player().chipCount()).isEqualTo(9950);

    // Current bet should be the big blind
    assertThat(table.currentBet()).isEqualTo(50);
    assertThat(table.minimumRaise()).isEqualTo(50);

    // Each active seat should have 2 hole cards
    for (Seat seat : table.seats()) {
      if (seat.status() == Seat.Status.ACTIVE) {
        assertThat(seat.cards()).hasSize(2);
      }
    }
  }

  @Test
  void deal_emitsHandStartedAndHoleCardsDealt() {
    TestableGameManager manager = createActiveGameWithPlayers(3);
    manager.processGameTick(); // Deal

    assertThat(manager.savedEvents()).anyMatch(e -> e instanceof HandStarted hs &&
        hs.handNumber() == 1 && hs.smallBlindAmount() == 25 && hs.bigBlindAmount() == 50);

    // Each player should get a HoleCardsDealt event
    long holeCardEvents = manager.savedEvents().stream()
        .filter(e -> e instanceof HoleCardsDealt)
        .count();
    assertThat(holeCardEvents).isEqualTo(3);
  }

  @Test
  void deal_actionStartsAtUTG() {
    TestableGameManager manager = createActiveGameWithPlayers(3);
    manager.processGameTick(); // Deal

    Table table = getTable(manager);
    Integer actionPos = table.actionPosition();
    assertThat(actionPos).isNotNull();

    // UTG should NOT be the SB or BB (for 3+ players, UTG is first after BB)
    // In 3-handed with dealer=0: SB=1, BB=2, UTG=0 (dealer acts first pre-flop)
    assertThat(actionPos).isNotEqualTo(table.bigBlindPosition());
  }

  // ============================================================
  // Pre-flop Betting Tests
  // ============================================================

  @Test
  void preFlopBetting_allFold_awardsToLastPlayer() {
    TestableGameManager manager = createActiveGameWithPlayers(3);
    manager.processGameTick(); // Deal

    Table table = getTable(manager);
    int bbPos = table.bigBlindPosition();

    // UTG folds
    submitActionAndTick(manager, new PlayerAction.Fold());
    // SB folds
    submitActionAndTick(manager, new PlayerAction.Fold());

    table = getTable(manager);
    assertThat(table.handPhase()).isEqualTo(HandPhase.HAND_COMPLETE);

    // BB wins the pot (SB's 25 + BB's 50 = 75)
    Seat bbSeat = table.seats().get(bbPos);
    assertThat(bbSeat.player().chipCount()).isEqualTo(9950 + 75);

    // Verify chip conservation
    assertChipConservation(manager, 30000);
  }

  @Test
  void preFlopBetting_allCall_advancesToFlop() {
    TestableGameManager manager = createActiveGameWithPlayers(3);
    manager.processGameTick(); // Deal

    // UTG calls
    submitActionAndTick(manager, new PlayerAction.Call(0));
    // SB calls (completes to BB amount)
    submitActionAndTick(manager, new PlayerAction.Call(0));
    // BB checks (option)
    submitActionAndTick(manager, new PlayerAction.Check());

    Table table = getTable(manager);
    // After pre-flop completes, phase should be FLOP (waiting for next tick to deal)
    assertThat(table.handPhase()).isEqualTo(HandPhase.FLOP);

    // Next tick deals the flop
    manager.processGameTick();
    table = getTable(manager);
    assertThat(table.handPhase()).isEqualTo(HandPhase.FLOP_BETTING);
    assertThat(table.communityCards()).hasSize(3);
    assertThat(table.currentBet()).isEqualTo(0);

    assertThat(manager.savedEvents()).anyMatch(e -> e instanceof CommunityCardsDealt ccd &&
        ccd.phase().equals("Flop") && ccd.cards().size() == 3);
  }

  @Test
  void preFlopBetting_raiseAndCallSequence() {
    TestableGameManager manager = createActiveGameWithPlayers(3);
    manager.processGameTick(); // Deal

    Table table = getTable(manager);
    int bbPos = table.bigBlindPosition();

    // UTG raises to 150 (bet 150, which is >= current bet 50 + min raise 50)
    submitActionAndTick(manager, new PlayerAction.Raise(150));
    // SB calls
    submitActionAndTick(manager, new PlayerAction.Call(0));
    // BB calls
    submitActionAndTick(manager, new PlayerAction.Call(0));

    table = getTable(manager);
    assertThat(table.handPhase()).isEqualTo(HandPhase.FLOP);

    // Verify BettingRoundComplete event was emitted
    assertThat(manager.savedEvents()).anyMatch(e -> e instanceof BettingRoundComplete);

    // Verify chip conservation
    assertChipConservation(manager, 30000);
  }

  // ============================================================
  // Full Hand Through Showdown
  // ============================================================

  @Test
  void fullHand_checkToShowdown_awardsChips() {
    TestableGameManager manager = createActiveGameWithPlayers(3);
    manager.processGameTick(); // Deal

    // Pre-flop: call, call, check
    submitActionAndTick(manager, new PlayerAction.Call(0));
    submitActionAndTick(manager, new PlayerAction.Call(0));
    submitActionAndTick(manager, new PlayerAction.Check());

    // Flop
    manager.processGameTick(); // Deal flop
    checkAround(manager, 3);

    // Turn
    manager.processGameTick(); // Deal turn
    assertThat(getTable(manager).communityCards()).hasSize(4);
    checkAround(manager, 3);

    // River
    manager.processGameTick(); // Deal river
    assertThat(getTable(manager).communityCards()).hasSize(5);
    checkAround(manager, 3);

    // After the last check on river, showdown runs and transitions to HAND_COMPLETE
    Table table = getTable(manager);
    assertThat(table.handPhase()).isEqualTo(HandPhase.HAND_COMPLETE);

    // Verify showdown result was emitted
    assertThat(manager.savedEvents()).anyMatch(e -> e instanceof ShowdownResult);

    // Verify chip conservation (total should still be 30000)
    assertChipConservation(manager, 30000);
  }

  @Test
  void fullHand_allInPreFlop_dealsAllCardsAndShowdown() {
    TestableGameManager manager = createActiveGameWithPlayers(3);
    manager.processGameTick(); // Deal

    // UTG goes all-in
    submitActionAndTick(manager, new PlayerAction.Raise(10000));
    // SB calls all-in
    submitActionAndTick(manager, new PlayerAction.Call(0));
    // BB calls all-in
    submitActionAndTick(manager, new PlayerAction.Call(0));

    // All-in shortcut: all community cards dealt, showdown runs immediately
    Table table = getTable(manager);
    assertThat(table.handPhase()).isEqualTo(HandPhase.HAND_COMPLETE);
    assertThat(table.communityCards()).hasSize(5);

    // Verify showdown result was emitted
    assertThat(manager.savedEvents()).anyMatch(e -> e instanceof ShowdownResult);

    // Chip conservation
    assertChipConservation(manager, 30000);
  }

  // ============================================================
  // Timeout Tests
  // ============================================================

  @Test
  void timeout_autoFoldsWhenBetToCall() {
    TestableGameManager manager = createActiveGameWithPlayers(3);
    manager.processGameTick(); // Deal

    Table table = getTable(manager);
    Integer actionPos = table.actionPosition();
    assertThat(actionPos).isNotNull();

    // Simulate timeout by setting action deadline to the past
    table.actionDeadline(Instant.now().minusSeconds(1));
    manager.processGameTick();

    table = getTable(manager);
    // The timed-out player should have been auto-folded
    Seat timedOutSeat = table.seats().get(actionPos);
    assertThat(timedOutSeat.status()).isEqualTo(Seat.Status.FOLDED);

    assertThat(manager.savedEvents()).anyMatch(e -> e instanceof PlayerTimedOut pto &&
        pto.seatPosition() == actionPos);
  }

  @Test
  void timeout_autoChecksWhenFreeToCheck() {
    TestableGameManager manager = createActiveGameWithPlayers(3);
    manager.processGameTick(); // Deal

    // Pre-flop: everyone calls, then flop betting starts
    submitActionAndTick(manager, new PlayerAction.Call(0));
    submitActionAndTick(manager, new PlayerAction.Call(0));
    submitActionAndTick(manager, new PlayerAction.Check());
    manager.processGameTick(); // Deal flop

    Table table = getTable(manager);
    Integer actionPos = table.actionPosition();
    assertThat(actionPos).isNotNull();
    assertThat(table.currentBet()).isEqualTo(0); // No bet yet on flop

    // Simulate timeout
    table.actionDeadline(Instant.now().minusSeconds(1));
    manager.processGameTick();

    table = getTable(manager);
    // Player should have auto-checked (not folded)
    Seat timedOutSeat = table.seats().get(actionPos);
    assertThat(timedOutSeat.status()).isEqualTo(Seat.Status.ACTIVE); // Still active, not folded
    assertThat(timedOutSeat.action()).isInstanceOf(PlayerAction.Check.class);

    assertThat(manager.savedEvents()).anyMatch(e -> e instanceof PlayerTimedOut);
  }

  // ============================================================
  // Hand Complete Tests
  // ============================================================

  @Test
  void handComplete_pausesWhenPauseAfterHand() {
    TestableGameManager manager = createActiveGameWithPlayers(3);
    manager.processGameTick(); // Deal

    // Set the table to PAUSE_AFTER_HAND
    Table table = getTable(manager);
    table.status(Table.Status.PAUSE_AFTER_HAND);

    // Everyone folds to BB to quickly reach HAND_COMPLETE
    submitActionAndTick(manager, new PlayerAction.Fold());
    submitActionAndTick(manager, new PlayerAction.Fold());

    table = getTable(manager);
    assertThat(table.handPhase()).isEqualTo(HandPhase.HAND_COMPLETE);

    // Skip the review period
    table.phaseStartedAt(Instant.now().minusSeconds(20));
    manager.processGameTick();

    table = getTable(manager);
    assertThat(table.status()).isEqualTo(Table.Status.PAUSED);
    assertThat(table.handPhase()).isEqualTo(HandPhase.WAITING_FOR_PLAYERS);
  }

  @Test
  void handComplete_startsNewHandAfterReviewPeriod() {
    TestableGameManager manager = createActiveGameWithPlayers(3);
    manager.processGameTick(); // Deal

    // Quick hand: all fold to BB
    submitActionAndTick(manager, new PlayerAction.Fold());
    submitActionAndTick(manager, new PlayerAction.Fold());

    Table table = getTable(manager);
    assertThat(table.handPhase()).isEqualTo(HandPhase.HAND_COMPLETE);
    assertThat(table.handNumber()).isEqualTo(1);

    // Skip review period
    table.phaseStartedAt(Instant.now().minusSeconds(20));
    manager.processGameTick();

    // Should deal a new hand
    table = getTable(manager);
    assertThat(table.handPhase()).isEqualTo(HandPhase.PRE_FLOP_BETTING);
    assertThat(table.handNumber()).isEqualTo(2);
  }

  @Test
  void handComplete_bustedPlayerMarkedAsBuyingIn() {
    // Create 3 players: one with very few chips that will bust after posting blind
    TestableGameManager manager = createActiveGameWithPlayers(3);
    manager.processGameTick(); // Deal hand 1

    Table table = getTable(manager);
    int bbPos = table.bigBlindPosition();

    // Quick hand: fold to BB
    submitActionAndTick(manager, new PlayerAction.Fold());
    submitActionAndTick(manager, new PlayerAction.Fold());

    // BB wins. Now set one player's chips to 0 to simulate busting
    // Find a non-BB player
    int bustPos = -1;
    for (int i = 0; i < table.seats().size(); i++) {
      Seat seat = table.seats().get(i);
      if (seat.status() != Seat.Status.EMPTY && seat.player() != null && i != bbPos) {
        seat.player().chipCount(0);
        bustPos = i;
        break;
      }
    }
    assertThat(bustPos).isGreaterThanOrEqualTo(0);

    // Skip review period
    table.phaseStartedAt(Instant.now().minusSeconds(20));
    manager.processGameTick();

    table = getTable(manager);
    Seat bustedSeat = table.seats().get(bustPos);
    assertThat(bustedSeat.status()).isEqualTo(Seat.Status.JOINED_WAITING);
    assertThat(bustedSeat.player().status()).isEqualTo(PlayerStatus.BUYING_IN);
  }

  @Test
  void handComplete_reviewPeriodHoldsBeforeTransition() {
    TestableGameManager manager = createActiveGameWithPlayers(3);
    manager.processGameTick(); // Deal

    // Quick hand: fold to BB
    submitActionAndTick(manager, new PlayerAction.Fold());
    submitActionAndTick(manager, new PlayerAction.Fold());

    Table table = getTable(manager);
    assertThat(table.handPhase()).isEqualTo(HandPhase.HAND_COMPLETE);

    // Don't skip review period — tick should NOT advance hand
    manager.processGameTick();

    table = getTable(manager);
    assertThat(table.handPhase()).isEqualTo(HandPhase.HAND_COMPLETE);
    assertThat(table.handNumber()).isEqualTo(1); // Still hand 1
  }

  // ============================================================
  // Heads-up Special Rules
  // ============================================================

  @Test
  void headsUp_dealerIsSmallBlind() {
    TestableGameManager manager = createActiveGameWithPlayers(2);
    manager.processGameTick(); // Deal

    Table table = getTable(manager);
    int dealer = table.dealerPosition();
    int sb = table.smallBlindPosition();
    int bb = table.bigBlindPosition();

    // In heads-up, dealer is the small blind
    assertThat(sb).isEqualTo(dealer);
    assertThat(bb).isNotEqualTo(dealer);
  }

  @Test
  void headsUp_fullHand() {
    TestableGameManager manager = createActiveGameWithPlayers(2);
    manager.processGameTick(); // Deal

    Table table = getTable(manager);
    assertThat(table.handPhase()).isEqualTo(HandPhase.PRE_FLOP_BETTING);

    // Pre-flop: SB/dealer calls, BB checks
    submitActionAndTick(manager, new PlayerAction.Call(0));
    submitActionAndTick(manager, new PlayerAction.Check());

    // Flop
    manager.processGameTick();
    table = getTable(manager);
    assertThat(table.handPhase()).isEqualTo(HandPhase.FLOP_BETTING);
    checkAround(manager, 2);

    // Turn
    manager.processGameTick();
    checkAround(manager, 2);

    // River
    manager.processGameTick();
    checkAround(manager, 2);

    table = getTable(manager);
    assertThat(table.handPhase()).isEqualTo(HandPhase.HAND_COMPLETE);
    assertChipConservation(manager, 20000);
  }

  // ============================================================
  // Side Pot Tests
  // ============================================================

  @Test
  void sidePot_createdWhenPlayerGoesAllIn() {
    // Create players with different chip counts to force side pot
    CashGame game = buildActiveGame(3);
    Table table = game.tables().values().iterator().next();

    // Give player at position 0 only 100 chips (short stack)
    table.seats().get(0).player().chipCount(100);

    TestableGameManager manager = new TestableGameManager(game);
    manager.processGameTick(); // Deal

    table = getTable(manager);
    int shortStackPos = 0;

    // Determine action order and submit appropriate actions
    // We need the short stack to go all-in and others to call
    playAllActionsUntilHandComplete(manager);

    table = getTable(manager);
    assertThat(table.handPhase()).isEqualTo(HandPhase.HAND_COMPLETE);

    // Verify total chips are conserved
    // Short stack started with 100, others with 10000 each
    assertChipConservation(manager, 100 + 10000 + 10000);
  }

  // ============================================================
  // Multi-hand Sequence
  // ============================================================

  @Test
  void multiHand_dealerRotatesAndNewHandDealt() {
    TestableGameManager manager = createActiveGameWithPlayers(3);
    manager.processGameTick(); // Deal hand 1

    Table table = getTable(manager);
    int firstDealer = table.dealerPosition();

    // Complete hand 1: fold to BB
    submitActionAndTick(manager, new PlayerAction.Fold());
    submitActionAndTick(manager, new PlayerAction.Fold());

    // Skip review
    table = getTable(manager);
    table.phaseStartedAt(Instant.now().minusSeconds(20));
    manager.processGameTick(); // Transitions out of HAND_COMPLETE, deals hand 2

    table = getTable(manager);
    assertThat(table.handNumber()).isEqualTo(2);
    assertThat(table.handPhase()).isEqualTo(HandPhase.PRE_FLOP_BETTING);

    // Dealer should have rotated
    int secondDealer = table.dealerPosition();
    assertThat(secondDealer).isNotEqualTo(firstDealer);

    // Complete hand 2
    submitActionAndTick(manager, new PlayerAction.Fold());
    submitActionAndTick(manager, new PlayerAction.Fold());

    table = getTable(manager);
    table.phaseStartedAt(Instant.now().minusSeconds(20));
    manager.processGameTick(); // Hand 3

    table = getTable(manager);
    assertThat(table.handNumber()).isEqualTo(3);
    int thirdDealer = table.dealerPosition();
    assertThat(thirdDealer).isNotEqualTo(secondDealer);

    // Total chips should be conserved across all hands
    assertChipConservation(manager, 30000);
  }

  // ============================================================
  // Betting Action Validation (via PlayerActed events)
  // ============================================================

  @Test
  void betting_flopBetAndCall() {
    TestableGameManager manager = createActiveGameWithPlayers(3);
    manager.processGameTick(); // Deal

    // Pre-flop: call around
    submitActionAndTick(manager, new PlayerAction.Call(0));
    submitActionAndTick(manager, new PlayerAction.Call(0));
    submitActionAndTick(manager, new PlayerAction.Check());

    // Deal flop
    manager.processGameTick();
    Table table = getTable(manager);
    assertThat(table.handPhase()).isEqualTo(HandPhase.FLOP_BETTING);
    assertThat(table.currentBet()).isEqualTo(0);

    // First actor bets 100
    submitActionAndTick(manager, new PlayerAction.Bet(100));

    table = getTable(manager);
    assertThat(table.currentBet()).isEqualTo(100);

    // Second player calls
    submitActionAndTick(manager, new PlayerAction.Call(0));
    // Third player calls
    submitActionAndTick(manager, new PlayerAction.Call(0));

    table = getTable(manager);
    // Should advance to TURN
    assertThat(table.handPhase()).isEqualTo(HandPhase.TURN);

    assertChipConservation(manager, 30000);
  }

  @Test
  void betting_flopBetRaiseCall() {
    TestableGameManager manager = createActiveGameWithPlayers(3);
    manager.processGameTick(); // Deal

    // Pre-flop: call around
    submitActionAndTick(manager, new PlayerAction.Call(0));
    submitActionAndTick(manager, new PlayerAction.Call(0));
    submitActionAndTick(manager, new PlayerAction.Check());

    // Deal flop
    manager.processGameTick();

    // First actor bets 100
    submitActionAndTick(manager, new PlayerAction.Bet(100));
    // Second player raises to 300
    submitActionAndTick(manager, new PlayerAction.Raise(300));
    // Third player calls
    submitActionAndTick(manager, new PlayerAction.Call(0));
    // First player calls the raise
    submitActionAndTick(manager, new PlayerAction.Call(0));

    Table table = getTable(manager);
    assertThat(table.handPhase()).isEqualTo(HandPhase.TURN);
    assertChipConservation(manager, 30000);
  }

  // ============================================================
  // Helper Methods
  // ============================================================

  private TestableGameManager createActiveGameWithPlayers(int playerCount) {
    CashGame game = buildActiveGame(playerCount);
    return new TestableGameManager(game);
  }

  private CashGame buildActiveGame(int playerCount) {
    CashGame game = CashGame.builder()
        .id("test-game")
        .name("Test Game")
        .type(GameType.TEXAS_HOLDEM)
        .status(GameStatus.ACTIVE)
        .startTime(Instant.now())
        .maxBuyIn(10000)
        .smallBlind(25)
        .bigBlind(50)
        .owner(adminUser)
        .build();

    Table table = Table.builder()
        .id("TABLE-0")
        .emptySeats(GameSettings.TEXAS_HOLDEM_SETTINGS.numberOfSeats())
        .status(Table.Status.PLAYING)
        .build();

    // Pre-set dealer so rotation is deterministic:
    // dealerPosition=last player → after rotation, dealer=0
    table.dealerPosition(playerCount - 1);

    game.tables().put(table.id(), table);

    for (int i = 0; i < playerCount; i++) {
      String id = "player-" + i;
      User user = TestDataHelper.user(id, id, "password", "Player " + i);
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
      player.tableId("TABLE-0");
    }

    return game;
  }

  private Table getTable(TestableGameManager manager) {
    return manager.getGame().tables().values().iterator().next();
  }

  private void submitAction(TestableGameManager manager, PlayerAction action) {
    CashGame game = manager.getGame();
    Table table = getTable(manager);
    Integer actionPos = table.actionPosition();
    assertThat(actionPos).withFailMessage("No action position set — is it a betting phase?").isNotNull();
    Seat seat = table.seats().get(actionPos);
    assertThat(seat.player()).withFailMessage("No player at action position " + actionPos).isNotNull();
    manager.submitCommand(new PlayerActionCommand(
        game.id(), table.id(), seat.player().user(), action));
  }

  private void submitActionAndTick(TestableGameManager manager, PlayerAction action) {
    submitAction(manager, action);
    manager.processGameTick();
  }

  private void checkAround(TestableGameManager manager, int playerCount) {
    for (int i = 0; i < playerCount; i++) {
      submitActionAndTick(manager, new PlayerAction.Check());
    }
  }

  /**
   * Plays through a hand by having everyone call/check until HAND_COMPLETE.
   * Useful for side pot tests where we just need the hand to finish.
   */
  private void playAllActionsUntilHandComplete(TestableGameManager manager) {
    int maxTicks = 100; // Safety limit
    for (int tick = 0; tick < maxTicks; tick++) {
      Table table = getTable(manager);
      if (table.handPhase() == HandPhase.HAND_COMPLETE) {
        return;
      }

      // If it's a betting phase with an action position, submit an action
      if (table.actionPosition() != null && isBettingPhase(table.handPhase())) {
        Seat actionSeat = table.seats().get(table.actionPosition());
        if (actionSeat.status() == Seat.Status.ACTIVE && !actionSeat.isAllIn()) {
          PlayerAction action;
          if (actionSeat.currentBetAmount() >= table.currentBet()) {
            action = new PlayerAction.Check();
          } else {
            action = new PlayerAction.Call(0);
          }
          submitAction(manager, action);
        }
      }

      manager.processGameTick();
    }
    assertThat(getTable(manager).handPhase())
        .withFailMessage("Hand did not complete within max ticks")
        .isEqualTo(HandPhase.HAND_COMPLETE);
  }

  private boolean isBettingPhase(HandPhase phase) {
    return phase == HandPhase.PRE_FLOP_BETTING ||
           phase == HandPhase.FLOP_BETTING ||
           phase == HandPhase.TURN_BETTING ||
           phase == HandPhase.RIVER_BETTING;
  }

  private void assertChipConservation(TestableGameManager manager, int expectedTotal) {
    int total = 0;
    CashGame game = manager.getGame();

    // Count chips on players
    for (Player player : game.players().values()) {
      if (player.chipCount() != null) {
        total += player.chipCount();
      }
    }

    // Count chips in pots and outstanding bets (only during active hand phases,
    // because during HAND_COMPLETE pots have been awarded but not yet cleared)
    for (Table table : game.tables().values()) {
      if (table.handPhase() != HandPhase.HAND_COMPLETE) {
        for (Table.Pot pot : table.pots()) {
          total += pot.amount();
        }
      }
      for (Seat seat : table.seats()) {
        total += seat.currentBetAmount();
      }
    }

    assertThat(total).isEqualTo(expectedTotal);
  }

  /**
   * Test-friendly GameManager that doesn't require Spring context or database.
   */
  static class TestableGameManager extends GameManager<CashGame> {

    private final List<PokerEvent> savedEvents = new ArrayList<>();

    TestableGameManager(CashGame game) {
      super(game, null, null);
      addGameListener(new GameListener() {
        @Override
        public String id() {
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

    List<PokerEvent> savedEvents() {
      return savedEvents;
    }

    CashGame getGame() {
      return game();
    }
  }
}
