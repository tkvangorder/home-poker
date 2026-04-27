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
