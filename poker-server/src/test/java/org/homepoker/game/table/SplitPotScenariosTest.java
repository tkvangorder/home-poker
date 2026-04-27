package org.homepoker.game.table;

import org.homepoker.model.command.PlayerActionCommand;
import org.homepoker.model.game.HandPhase;
import org.homepoker.model.game.PlayerAction;
import org.homepoker.model.game.Seat;
import org.homepoker.model.game.Table;
import org.homepoker.poker.Deck;
import org.homepoker.test.DeckBuilder;
import org.homepoker.test.ShowdownAssert;
import org.homepoker.test.SplitPotScenarioFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for split-pot (side-pot) chip distribution. Each test stacks the deck
 * deterministically, drives a hand with explicit player actions, and asserts the
 * {@link org.homepoker.model.event.table.ShowdownResult} event plus chip conservation.
 */
class SplitPotScenariosTest {

  // -------- A1: 3 pots, preflop all-ins, distinct winners --------
  @Test
  void splitPot_threePotsPreflop_distinctWinners() {
    Deck deck = DeckBuilder.holdem(4)
        .holeCards(1, "As Ah")
        .holeCards(2, "Ks Kh")
        .holeCards(3, "Qs Qh")
        .holeCards(4, "8c 8d")
        .flop("Js Th 7c").turn("4d").river("2s")
        .build();

    SplitPotScenarioFixture fixture = SplitPotScenarioFixture.builder()
        .stacks(100, 300, 500, 1000)
        .deck(deck)
        .build();

    Table table = fixture.table();
    assertThat(table.handPhase()).isEqualTo(HandPhase.PRE_FLOP_BETTING);
    assertThat(table.actionPosition()).isEqualTo(4); // UTG with 4 players

    submitAction(fixture, new PlayerAction.Raise(1000));   // seat 4 all-in
    submitAction(fixture, new PlayerAction.Call(0));       // seat 1 all-in for 100
    submitAction(fixture, new PlayerAction.Call(0));       // seat 2 all-in for 300
    submitAction(fixture, new PlayerAction.Call(0));       // seat 3 all-in for 500

    runUntilHandComplete(fixture);

    // Seat 4's uncalled 500 forms a 4th pot with only seat 4 eligible — they win it back
    // at showdown (functionally equivalent to a returned uncalled bet).
    ShowdownAssert.from(fixture.savedEvents())
        .hasPotCount(4)
        .pot(0).amount(400).winner(1, "Pair").and()
        .pot(1).amount(600).winner(2, "Pair").and()
        .pot(2).amount(400).winner(3, "Pair").and()
        .pot(3).amount(500).winner(4, "Pair").and()
        .totalAwarded(1900);

    assertChipConservation(fixture);
    assertThat(fixture.seatAt(1).player().chipCount()).isEqualTo(400);
    assertThat(fixture.seatAt(2).player().chipCount()).isEqualTo(600);
    assertThat(fixture.seatAt(3).player().chipCount()).isEqualTo(400);
    assertThat(fixture.seatAt(4).player().chipCount()).isEqualTo(500);
  }

  // -------- A2: 4 pots, preflop all-ins, deep stack calls --------
  @Test
  void splitPot_fourPotsPreflop_deepStackCalls() {
    Deck deck = DeckBuilder.holdem(5)
        .holeCards(1, "As Ah")
        .holeCards(2, "Ks Kh")
        .holeCards(3, "Qs Qh")
        .holeCards(4, "Js Jh")
        .holeCards(5, "2c 3d")
        .flop("Th 7c 4d").turn("2d").river("9s")
        .build();

    SplitPotScenarioFixture fixture = SplitPotScenarioFixture.builder()
        .stacks(100, 300, 500, 700, 2000)
        .deck(deck)
        .build();

    assertThat(fixture.table().actionPosition()).isEqualTo(4); // UTG with 5 players

    submitAction(fixture, new PlayerAction.Raise(700));    // seat 4 all-in
    submitAction(fixture, new PlayerAction.Call(0));        // seat 5 calls 700 (not all-in)
    submitAction(fixture, new PlayerAction.Call(0));        // seat 1 all-in for 100
    submitAction(fixture, new PlayerAction.Call(0));        // seat 2 all-in for 300
    submitAction(fixture, new PlayerAction.Call(0));        // seat 3 all-in for 500

    runUntilHandComplete(fixture);

    ShowdownAssert.from(fixture.savedEvents())
        .hasPotCount(4)
        .pot(0).amount(500).winner(1, "Pair").and()
        .pot(1).amount(800).winner(2, "Pair").and()
        .pot(2).amount(600).winner(3, "Pair").and()
        .pot(3).amount(400).winner(4, "Pair").and()
        .totalAwarded(2300);

    assertChipConservation(fixture);
    assertThat(fixture.seatAt(5).player().chipCount()).isEqualTo(1300);
  }

  // -------- B1: 3 pots, all-ins staggered across streets --------
  @Test
  void splitPot_threePots_allInsAcrossStreets() {
    Deck deck = DeckBuilder.holdem(4)
        .holeCards(1, "As Ah")
        .holeCards(2, "Ks Kh")
        .holeCards(3, "Qs Qh")
        .holeCards(4, "8c 8d")
        .flop("Js Tc 7d").turn("4h").river("2s")
        .build();

    SplitPotScenarioFixture fixture = SplitPotScenarioFixture.builder()
        .stacks(100, 300, 500, 2000)
        .deck(deck)
        .build();

    Table table = fixture.table();
    assertThat(table.actionPosition()).isEqualTo(4); // UTG

    // Preflop: everyone calls 50.
    submitAction(fixture, new PlayerAction.Call(0)); // seat 4
    submitAction(fixture, new PlayerAction.Call(0)); // seat 1
    submitAction(fixture, new PlayerAction.Call(0)); // seat 2 (already 25 in as SB)
    submitAction(fixture, new PlayerAction.Check()); // seat 3 (BB)

    // Tick to deal the flop.
    fixture.tick();
    assertThat(fixture.table().handPhase()).isEqualTo(HandPhase.FLOP_BETTING);

    // Flop: SB (seat 2) bets all-in 250; seat 3 calls 250; seat 4 calls 250; seat 1 all-in for 50.
    submitAction(fixture, new PlayerAction.Bet(250));   // seat 2 all-in
    submitAction(fixture, new PlayerAction.Call(0));    // seat 3
    submitAction(fixture, new PlayerAction.Call(0));    // seat 4
    submitAction(fixture, new PlayerAction.Call(0));    // seat 1 all-in for 50

    // Tick to deal the turn.
    fixture.tick();
    assertThat(fixture.table().handPhase()).isEqualTo(HandPhase.TURN_BETTING);

    // Turn: seat 3 bets all-in 200; seat 4 calls.
    submitAction(fixture, new PlayerAction.Bet(200));   // seat 3 all-in
    submitAction(fixture, new PlayerAction.Call(0));    // seat 4

    // River: no betting (only seat 4 not all-in). Tick through to HAND_COMPLETE.
    runUntilHandComplete(fixture);

    ShowdownAssert.from(fixture.savedEvents())
        .hasPotCount(3)
        .pot(0).amount(400).winner(1, "Pair").and()
        .pot(1).amount(600).winner(2, "Pair").and()
        .pot(2).amount(400).winner(3, "Pair").and()
        .totalAwarded(1400);

    assertChipConservation(fixture);
    assertThat(fixture.seatAt(4).player().chipCount()).isEqualTo(1500);
  }

  // -------- B2: 2 pots, preflop all-ins + flop all-in (pot accumulation) --------
  @Test
  void splitPot_twoPots_preflopPlusFlopAllIn() {
    Deck deck = DeckBuilder.holdem(3)
        .holeCards(1, "As Ah")
        .holeCards(2, "Ks Kh")
        .holeCards(3, "Qs Qh")
        .flop("Js Tc 7d").turn("4h").river("2s")
        .build();

    SplitPotScenarioFixture fixture = SplitPotScenarioFixture.builder()
        .stacks(200, 400, 2000)
        .deck(deck)
        .build();

    Table table = fixture.table();
    // 3 players: dealer=1 (UTG in 3-handed), SB=2, BB=3.
    assertThat(table.actionPosition()).isEqualTo(1);

    // Preflop: dealer all-in 200; SB calls 200; BB calls 200.
    submitAction(fixture, new PlayerAction.Raise(200));  // seat 1 all-in
    submitAction(fixture, new PlayerAction.Call(0));     // seat 2
    submitAction(fixture, new PlayerAction.Call(0));     // seat 3

    // Tick to deal the flop.
    fixture.tick();
    assertThat(fixture.table().handPhase()).isEqualTo(HandPhase.FLOP_BETTING);

    // Flop: SB (seat 2) bets all-in 200; seat 3 calls.
    submitAction(fixture, new PlayerAction.Bet(200));    // seat 2 all-in
    submitAction(fixture, new PlayerAction.Call(0));     // seat 3

    runUntilHandComplete(fixture);

    ShowdownAssert.from(fixture.savedEvents())
        .hasPotCount(2)
        .pot(0).amount(600).winner(1, "Pair").and()
        .pot(1).amount(400).winner(2, "Pair").and()
        .totalAwarded(1000);

    assertChipConservation(fixture);
    assertThat(fixture.seatAt(3).player().chipCount()).isEqualTo(1600);
  }

  // -------- C1: 3 pots, two-way chop on main pot --------
  @Test
  void splitPot_threePots_chopOnMainPot() {
    Deck deck = DeckBuilder.holdem(4)
        .holeCards(1, "Qs Qh")
        .holeCards(2, "Qd Qc")
        .holeCards(3, "Js Jh")
        .holeCards(4, "8c 8d")
        .flop("7s 5h 4d").turn("3c").river("2s")
        .build();

    SplitPotScenarioFixture fixture = SplitPotScenarioFixture.builder()
        .stacks(100, 100, 300, 500)
        .deck(deck)
        .build();

    Table table = fixture.table();
    assertThat(table.actionPosition()).isEqualTo(4); // UTG with 4 players

    submitAction(fixture, new PlayerAction.Raise(500)); // seat 4 all-in 500
    submitAction(fixture, new PlayerAction.Call(0));    // seat 1 all-in for 100
    submitAction(fixture, new PlayerAction.Call(0));    // seat 2 all-in for 100
    submitAction(fixture, new PlayerAction.Call(0));    // seat 3 all-in for 300

    runUntilHandComplete(fixture);

    // Pot structure:
    // Pot 0: 100*4 = 400, eligible {1,2,3,4}, seats 1 & 2 chop (both pocket queens)
    // Pot 1: 200*2 = 400, eligible {3,4}, seat 3 wins (jacks beat eights)
    // Pot 2: 200*1 = 200, eligible {4} only — uncalled bet returned as a 1-eligible pot
    ShowdownAssert.from(fixture.savedEvents())
        .hasPotCount(3)
        .pot(0).amount(400).winners(1, 2).chopsEvenly().and()
        .pot(1).amount(400).winner(3, "Pair").and()
        .pot(2).amount(200).winner(4, "Pair").and()
        .totalAwarded(1000);

    assertChipConservation(fixture);
    assertThat(fixture.seatAt(1).player().chipCount()).isEqualTo(200);
    assertThat(fixture.seatAt(2).player().chipCount()).isEqualTo(200);
    assertThat(fixture.seatAt(3).player().chipCount()).isEqualTo(400);
    assertThat(fixture.seatAt(4).player().chipCount()).isEqualTo(200);
  }

  // -------- C2: odd-chip distribution on tied main pot --------
  @Test
  void splitPot_oddChipDistribution() {
    Deck deck = DeckBuilder.holdem(3)
        .holeCards(1, "Ah Ad")
        .holeCards(2, "As Ac")
        .holeCards(3, "Ks Kh")
        .flop("7c 5d 3h").turn("9s").river("2c")
        .build();

    SplitPotScenarioFixture fixture = SplitPotScenarioFixture.builder()
        .stacks(151, 200, 300)
        .deck(deck)
        .build();

    Table table = fixture.table();
    assertThat(table.actionPosition()).isEqualTo(1); // dealer = UTG in 3-handed

    submitAction(fixture, new PlayerAction.Raise(151)); // seat 1 all-in for 151
    submitAction(fixture, new PlayerAction.Raise(200)); // seat 2 all-in for 200
    submitAction(fixture, new PlayerAction.Raise(300)); // seat 3 all-in for 300

    runUntilHandComplete(fixture);

    // Pot structure:
    // Pot 0: 151*3 = 453, eligible {1,2,3}, seats 1 & 2 chop (both pocket aces) → 226 + 1 odd
    // Pot 1: 49*2 = 98, eligible {2,3}, seat 2 wins (aces beat kings)
    // Pot 2: 100*1 = 100, eligible {3} — uncalled portion returned to seat 3
    ShowdownAssert.from(fixture.savedEvents())
        .hasPotCount(3)
        .pot(0).amount(453).winners(1, 2).oddChipTo(1).and()
        .pot(1).amount(98).winner(2, "Pair").and()
        .pot(2).amount(100).winner(3, "Pair").and()
        .totalAwarded(651);

    assertChipConservation(fixture);
    assertThat(fixture.seatAt(1).player().chipCount()).isEqualTo(227);
    assertThat(fixture.seatAt(2).player().chipCount()).isEqualTo(324);
    assertThat(fixture.seatAt(3).player().chipCount()).isEqualTo(100);
  }

  // -------- D1: folded contributor (3 pots, folded player ineligible) --------
  @Test
  void splitPot_threePots_foldedContributor() {
    Deck deck = DeckBuilder.holdem(4)
        .holeCards(1, "Kc Kd")  // would win — but folds on flop
        .holeCards(2, "Qs Qh")
        .holeCards(3, "Js Jh")
        .holeCards(4, "8c 8d")
        .flop("7s 5d 3h").turn("4c").river("2s")
        .build();

    SplitPotScenarioFixture fixture = SplitPotScenarioFixture.builder()
        .stacks(2000, 300, 500, 2000)
        .deck(deck)
        .build();

    Table table = fixture.table();
    assertThat(table.actionPosition()).isEqualTo(4); // UTG

    // Preflop: UTG raises to 200; everyone calls.
    submitAction(fixture, new PlayerAction.Raise(200)); // seat 4
    submitAction(fixture, new PlayerAction.Call(0));    // seat 1
    submitAction(fixture, new PlayerAction.Call(0));    // seat 2
    submitAction(fixture, new PlayerAction.Call(0));    // seat 3 (BB had 50, calls 150 more)

    fixture.tick();
    assertThat(fixture.table().handPhase()).isEqualTo(HandPhase.FLOP_BETTING);

    // Flop: SB (seat 2) bets all-in 100; seat 3 calls; seat 4 calls; seat 1 FOLDS.
    submitAction(fixture, new PlayerAction.Bet(100));   // seat 2 all-in
    submitAction(fixture, new PlayerAction.Call(0));    // seat 3
    submitAction(fixture, new PlayerAction.Call(0));    // seat 4
    submitAction(fixture, new PlayerAction.Fold());     // seat 1 (FOLDS)

    fixture.tick();
    assertThat(fixture.table().handPhase()).isEqualTo(HandPhase.TURN_BETTING);

    // Turn: seat 3 bets all-in 200; seat 4 calls.
    submitAction(fixture, new PlayerAction.Bet(200));   // seat 3 all-in
    submitAction(fixture, new PlayerAction.Call(0));    // seat 4

    runUntilHandComplete(fixture);

    // Pots:
    //   Pot 0: 200*4=800, eligible {1,2,3,4} (set when collected at end of preflop, before
    //          seat 1 folded — but evaluatePotWinners skips folded seats at showdown)
    //   Pot 1: 100*3=300 (flop, eligible {2,3,4})
    //   Pot 2: 200*2=400 (turn, eligible {3,4})
    ShowdownAssert.from(fixture.savedEvents())
        .hasPotCount(3)
        .pot(0).amount(800).winner(2, "Pair").and()
        .pot(1).amount(300).winner(2, "Pair").and()
        .pot(2).amount(400).winner(3, "Pair").and()
        .totalAwarded(1500);

    assertChipConservation(fixture);
    assertThat(fixture.seatAt(1).player().chipCount()).isEqualTo(1800); // 2000 - 200 (folded after preflop call only)
    assertThat(fixture.seatAt(2).player().chipCount()).isEqualTo(1100); // 800 + 300
    assertThat(fixture.seatAt(3).player().chipCount()).isEqualTo(400);
    assertThat(fixture.seatAt(4).player().chipCount()).isEqualTo(1500); // 2000 - 500
  }

  // -------- D2: folded contributor + chopped pot --------
  @Test
  void splitPot_foldedContributorWithChoppedPot() {
    Deck deck = DeckBuilder.holdem(4)
        .holeCards(1, "Kc Kd")  // would win — but folds on flop
        .holeCards(2, "Qs Qh")
        .holeCards(3, "Qd Qc")  // tied with seat 2
        .holeCards(4, "8c 8d")
        .flop("7s 5h 3d").turn("2c").river("4h")
        .build();

    SplitPotScenarioFixture fixture = SplitPotScenarioFixture.builder()
        .stacks(2000, 300, 300, 2000)
        .deck(deck)
        .build();

    Table table = fixture.table();
    assertThat(table.actionPosition()).isEqualTo(4); // UTG

    // Preflop: UTG raises to 300; everyone calls.
    submitAction(fixture, new PlayerAction.Raise(300)); // seat 4
    submitAction(fixture, new PlayerAction.Call(0));    // seat 1
    submitAction(fixture, new PlayerAction.Call(0));    // seat 2 all-in
    submitAction(fixture, new PlayerAction.Call(0));    // seat 3 all-in

    fixture.tick();
    assertThat(fixture.table().handPhase()).isEqualTo(HandPhase.FLOP_BETTING);

    // Flop: only seats 1 & 4 not all-in. Seat 4 bets 500; seat 1 FOLDS.
    submitAction(fixture, new PlayerAction.Bet(500));   // seat 4
    submitAction(fixture, new PlayerAction.Fold());     // seat 1

    runUntilHandComplete(fixture);

    // Pots:
    //   Pot 0: 1200 (eligible {1,2,3,4}, seat 1 folded -> contested by {2,3,4}, chop 2/3)
    //   Pot 1: 500 (eligible {4} — uncalled flop bet, won back at showdown)
    ShowdownAssert.from(fixture.savedEvents())
        .hasPotCount(2)
        .pot(0).amount(1200).winners(2, 3).chopsEvenly().and()
        .pot(1).amount(500).winner(4, "Pair").and()
        .totalAwarded(1700);

    assertChipConservation(fixture);
    assertThat(fixture.seatAt(1).player().chipCount()).isEqualTo(1700);
    assertThat(fixture.seatAt(2).player().chipCount()).isEqualTo(600);
    assertThat(fixture.seatAt(3).player().chipCount()).isEqualTo(600);
    assertThat(fixture.seatAt(4).player().chipCount()).isEqualTo(1700);
  }

  // ============================================================
  // Helpers
  // ============================================================

  static void submitAction(SplitPotScenarioFixture fixture, PlayerAction action) {
    Table table = fixture.table();
    Integer pos = table.actionPosition();
    assertThat(pos)
        .withFailMessage("No action position set; phase=%s", table.handPhase())
        .isNotNull();
    Seat seat = table.seatAt(pos);
    assertThat(seat.player()).isNotNull();
    fixture.submitCommand(new PlayerActionCommand(
        fixture.game().id(), fixture.tableId(), seat.player().user(), action));
    fixture.tick();
  }

  static void runUntilHandComplete(SplitPotScenarioFixture fixture) {
    int safety = 100;
    while (safety-- > 0) {
      Table table = fixture.table();
      if (table.handPhase() == HandPhase.HAND_COMPLETE) return;
      // If everyone is all-in or only one player remains, no action position — just tick.
      if (table.actionPosition() == null) {
        fixture.tick();
        continue;
      }
      Seat seat = table.seatAt(table.actionPosition());
      if (seat.status() != Seat.Status.ACTIVE || seat.isAllIn()) {
        fixture.tick();
        continue;
      }
      // Auto-check or auto-call for any remaining (non-all-in) players.
      PlayerAction action = (seat.currentBetAmount() >= table.currentBet())
          ? new PlayerAction.Check()
          : new PlayerAction.Call(0);
      fixture.submitCommand(new PlayerActionCommand(
          fixture.game().id(), fixture.tableId(), seat.player().user(), action));
      fixture.tick();
    }
    throw new AssertionError("Hand did not complete within safety budget");
  }

  static void assertChipConservation(SplitPotScenarioFixture fixture) {
    int total = 0;
    for (var p : fixture.game().players().values()) total += p.chipCount();
    assertThat(total)
        .as("chip conservation")
        .isEqualTo(fixture.initialTotalChips());
  }
}
