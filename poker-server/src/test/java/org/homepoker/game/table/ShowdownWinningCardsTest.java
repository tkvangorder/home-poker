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

/**
 * End-to-end verification that {@link org.homepoker.model.event.table.ShowdownResult} records
 * the concrete cards forming each winner's best hand.
 */
class ShowdownWinningCardsTest {

  @Test
  void recordsTheFiveCardsOfTheWinningHand() {
    // Heads-up. Seat 1 wins with trip Aces (As + Ah Ad on board), kickers Ks (hole) and 9s (board).
    // Seat 2 (7c 2d) only pairs the aces and loses.
    Deck deck = DeckBuilder.holdem(2)
        .holeCards(1, "As Ks")
        .holeCards(2, "7c 2d")
        .flop("Ah Ad 9s").turn("5d").river("3c")
        .build();

    SplitPotScenarioFixture fixture = SplitPotScenarioFixture.builder()
        .stacks(1000, 1000)
        .deck(deck)
        .build();

    runUntilHandComplete(fixture);

    ShowdownAssert.from(fixture.savedEvents())
        .hasPotCount(1)
        .pot(0).winner(1, "THREE_OF_A_KIND").winningCards(1, "As Ah Ad Ks 9s");
  }

  // Drives the hand to completion by auto-checking/calling for any player to act.
  static void runUntilHandComplete(SplitPotScenarioFixture fixture) {
    int safety = 100;
    while (safety-- > 0) {
      Table table = fixture.table();
      if (table.handPhase() == HandPhase.HAND_COMPLETE) return;
      if (table.actionPosition() == null) {
        fixture.tick();
        continue;
      }
      Seat seat = table.seatAt(table.actionPosition());
      if (seat.status() != Seat.Status.ACTIVE || seat.isAllIn()) {
        fixture.tick();
        continue;
      }
      PlayerAction action = (seat.currentBetAmount() >= table.currentBet())
          ? new PlayerAction.Check()
          : new PlayerAction.Call(0);
      fixture.submitCommand(new PlayerActionCommand(
          fixture.game().id(), fixture.tableId(), seat.player().user(), action));
      fixture.tick();
    }
    throw new AssertionError("Hand did not complete within safety budget");
  }
}
