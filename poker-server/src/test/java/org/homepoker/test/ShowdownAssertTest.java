package org.homepoker.test;

import org.homepoker.model.event.PokerEvent;
import org.homepoker.model.event.table.ShowdownResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShowdownAssertTest {

  @Test
  void assertsPotCountAmountAndSingleWinner() {
    ShowdownResult event = new ShowdownResult(
        Instant.now(), 1L, "g", "t",
        List.of(
            new ShowdownResult.PotResult(0, 400,
                List.of(new ShowdownResult.Winner(1, "user-1", 400, "Pair of Aces"))),
            new ShowdownResult.PotResult(1, 600,
                List.of(new ShowdownResult.Winner(2, "user-2", 600, "Pair of Kings")))
        ));
    List<PokerEvent> events = List.of(event);

    ShowdownAssert.from(events)
        .hasPotCount(2)
        .pot(0).amount(400).winner(1, "Pair of Aces").and()
        .pot(1).amount(600).winner(2, "Pair of Kings").and()
        .totalAwarded(1000);
  }

  @Test
  void chopsEvenlyDetectsEqualSplit() {
    ShowdownResult event = new ShowdownResult(
        Instant.now(), 1L, "g", "t",
        List.of(new ShowdownResult.PotResult(0, 600, List.of(
            new ShowdownResult.Winner(1, "u1", 300, "Two Pair"),
            new ShowdownResult.Winner(2, "u2", 300, "Two Pair")))));
    ShowdownAssert.from(List.of(event))
        .hasPotCount(1)
        .pot(0).amount(600).winners(1, 2).chopsEvenly();
  }

  @Test
  void oddChipDetectsRemainderRecipient() {
    ShowdownResult event = new ShowdownResult(
        Instant.now(), 1L, "g", "t",
        List.of(new ShowdownResult.PotResult(0, 301, List.of(
            new ShowdownResult.Winner(1, "u1", 101, "Pair"),
            new ShowdownResult.Winner(2, "u2", 100, "Pair"),
            new ShowdownResult.Winner(3, "u3", 100, "Pair")))));
    ShowdownAssert.from(List.of(event))
        .pot(0).winners(1, 2, 3).oddChipTo(1);
  }

  @Test
  void failsWhenWinnerSeatMismatches() {
    ShowdownResult event = new ShowdownResult(
        Instant.now(), 1L, "g", "t",
        List.of(new ShowdownResult.PotResult(0, 100,
            List.of(new ShowdownResult.Winner(1, "u1", 100, "x")))));
    assertThatThrownBy(() ->
        ShowdownAssert.from(List.of(event)).pot(0).winner(2, "x"))
        .isInstanceOf(AssertionError.class);
  }
}
