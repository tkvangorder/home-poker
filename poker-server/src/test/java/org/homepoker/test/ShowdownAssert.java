package org.homepoker.test;

import org.homepoker.model.event.PokerEvent;
import org.homepoker.model.event.table.ShowdownResult;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Fluent assertion over a captured {@link ShowdownResult} event.
 * <p>
 * Usage:
 * <pre>{@code
 *   ShowdownAssert.from(fixture.savedEvents())
 *       .hasPotCount(3)
 *       .pot(0).amount(400).winner(1, "Pair").and()
 *       .pot(1).amount(600).winners(2, 3).chopsEvenly().and()
 *       .totalAwarded(1000);
 * }</pre>
 */
public final class ShowdownAssert {

  private final ShowdownResult showdown;

  private ShowdownAssert(ShowdownResult showdown) {
    this.showdown = showdown;
  }

  public static ShowdownAssert from(List<PokerEvent> events) {
    List<ShowdownResult> results = events.stream()
        .filter(e -> e instanceof ShowdownResult)
        .map(e -> (ShowdownResult) e)
        .toList();
    if (results.isEmpty()) {
      fail("No ShowdownResult event captured");
    }
    if (results.size() > 1) {
      fail("Expected exactly one ShowdownResult event, got " + results.size());
    }
    return new ShowdownAssert(results.get(0));
  }

  public ShowdownAssert hasPotCount(int expected) {
    assertThat(showdown.potResults())
        .as("pot count")
        .hasSize(expected);
    return this;
  }

  public PotAssert pot(int index) {
    assertThat(index)
        .as("pot index in range")
        .isGreaterThanOrEqualTo(0)
        .isLessThan(showdown.potResults().size());
    return new PotAssert(this, showdown.potResults().get(index), index);
  }

  public ShowdownAssert totalAwarded(int expected) {
    int sum = showdown.potResults().stream().mapToInt(ShowdownResult.PotResult::potAmount).sum();
    assertThat(sum).as("total awarded across all pots").isEqualTo(expected);
    int winnerSum = showdown.potResults().stream()
        .flatMap(p -> p.winners().stream())
        .mapToInt(ShowdownResult.Winner::amount)
        .sum();
    assertThat(winnerSum).as("sum of all Winner.amount").isEqualTo(expected);
    return this;
  }

  public static final class PotAssert {
    private final ShowdownAssert parent;
    private final ShowdownResult.PotResult pot;
    private final int index;

    private PotAssert(ShowdownAssert parent, ShowdownResult.PotResult pot, int index) {
      this.parent = parent;
      this.pot = pot;
      this.index = index;
    }

    public PotAssert amount(int expected) {
      assertThat(pot.potAmount()).as("pot[%d] amount", index).isEqualTo(expected);
      return this;
    }

    public PotAssert winner(int seatPosition, String handDescriptionContains) {
      assertThat(pot.winners()).as("pot[%d] single winner", index).hasSize(1);
      ShowdownResult.Winner w = pot.winners().get(0);
      assertThat(w.seatPosition()).as("pot[%d] winner seat", index).isEqualTo(seatPosition);
      assertThat(w.handDescription())
          .as("pot[%d] winner hand description", index)
          .containsIgnoringCase(handDescriptionContains);
      assertThat(w.amount()).as("pot[%d] winner amount equals pot amount", index)
          .isEqualTo(pot.potAmount());
      return this;
    }

    public WinnersAssert winners(int... seatPositions) {
      List<Integer> actualSeats = pot.winners().stream()
          .map(ShowdownResult.Winner::seatPosition)
          .collect(Collectors.toList());
      List<Integer> expectedSeats = java.util.Arrays.stream(seatPositions).boxed().toList();
      assertThat(actualSeats)
          .as("pot[%d] winner seats", index)
          .containsExactlyInAnyOrderElementsOf(expectedSeats);
      return new WinnersAssert(this, pot, index);
    }

    public ShowdownAssert and() {
      return parent;
    }
  }

  public static final class WinnersAssert {
    private final PotAssert pot;
    private final ShowdownResult.PotResult potResult;
    private final int index;

    private WinnersAssert(PotAssert pot, ShowdownResult.PotResult potResult, int index) {
      this.pot = pot;
      this.potResult = potResult;
      this.index = index;
    }

    public PotAssert chopsEvenly() {
      int expectedShare = potResult.potAmount() / potResult.winners().size();
      int remainder = potResult.potAmount() % potResult.winners().size();
      assertThat(remainder).as("pot[%d] divides evenly", index).isZero();
      for (ShowdownResult.Winner w : potResult.winners()) {
        assertThat(w.amount())
            .as("pot[%d] winner %d share", index, w.seatPosition())
            .isEqualTo(expectedShare);
      }
      return pot;
    }

    public PotAssert oddChipTo(int seatPosition) {
      int share = potResult.potAmount() / potResult.winners().size();
      int remainder = potResult.potAmount() % potResult.winners().size();
      assertThat(remainder).as("pot[%d] has an odd-chip remainder", index).isPositive();
      for (ShowdownResult.Winner w : potResult.winners()) {
        int expected = (w.seatPosition() == seatPosition) ? share + remainder : share;
        assertThat(w.amount())
            .as("pot[%d] winner %d expected %d (share=%d, remainder=%d)",
                index, w.seatPosition(), expected, share, remainder)
            .isEqualTo(expected);
      }
      return pot;
    }
  }
}
