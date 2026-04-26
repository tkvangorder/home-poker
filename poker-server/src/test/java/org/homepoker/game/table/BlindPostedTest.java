package org.homepoker.game.table;

import org.homepoker.model.event.PokerEvent;
import org.homepoker.model.event.table.ActionOnPlayer;
import org.homepoker.model.event.table.BlindPosted;
import org.homepoker.model.event.table.HandStarted;
import org.homepoker.model.game.BlindType;
import org.homepoker.test.GameManagerTestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlindPostedTest {

  @Test
  void blindPostedOrderingAroundHandStartedAndActionOnPlayer() {
    GameManagerTestFixture fixture =
        GameManagerTestFixture.singleTableHandStartedThroughActionOnPlayer();

    List<PokerEvent> events = fixture.savedEvents();

    int blindSmallIdx  = indexOfFirstBlind(events, BlindType.SMALL);
    int blindBigIdx    = indexOfFirstBlind(events, BlindType.BIG);
    int handStartedIdx = indexOfFirst(events, HandStarted.class);
    int actionOnIdx    = indexOfFirst(events, ActionOnPlayer.class);

    // SB posts before BB, both before HandStarted's post-blind snapshot, all before ActionOnPlayer.
    // (Per spec "Consistency with HandStarted": clients see chip counts move through the blinds,
    // then confirm against HandStarted's seats[]; HandStarted is emitted after postBlind.)
    assertThat(blindSmallIdx).isLessThan(blindBigIdx);
    assertThat(blindBigIdx).isLessThan(handStartedIdx);
    assertThat(handStartedIdx).isLessThan(actionOnIdx);
  }

  @Test
  void amountPostedEqualsBlindWhenStackIsLargeEnough() {
    GameManagerTestFixture fixture =
        GameManagerTestFixture.singleTableHandStartedThroughActionOnPlayer();

    BlindPosted small = firstBlind(fixture.savedEvents(), BlindType.SMALL);
    BlindPosted big = firstBlind(fixture.savedEvents(), BlindType.BIG);

    assertThat(small.amountPosted()).isEqualTo(fixture.smallBlindAmount());
    assertThat(big.amountPosted()).isEqualTo(fixture.bigBlindAmount());
  }

  @Test
  void amountPostedEqualsStackWhenPlayerCannotCoverBlind() {
    GameManagerTestFixture fixture =
        GameManagerTestFixture.singleTableSmallBlindPlayerStackBelowBlind(5, 10);

    BlindPosted small = firstBlind(fixture.savedEvents(), BlindType.SMALL);

    assertThat(small.amountPosted()).isEqualTo(5L);
    assertThat(fixture.seatAt(small.seatPosition()).isAllIn()).isTrue();
  }

  // --- helpers ---

  private static int indexOfFirst(List<PokerEvent> events, Class<?> type) {
    for (int i = 0; i < events.size(); i++) {
      if (type.isInstance(events.get(i))) return i;
    }
    throw new AssertionError("No event of type " + type.getSimpleName() + " in stream");
  }

  private static int indexOfFirstBlind(List<PokerEvent> events, BlindType type) {
    for (int i = 0; i < events.size(); i++) {
      if (events.get(i) instanceof BlindPosted bp && bp.blindType() == type) return i;
    }
    throw new AssertionError("No BlindPosted(" + type + ") in stream");
  }

  private static BlindPosted firstBlind(List<PokerEvent> events, BlindType type) {
    return events.stream()
        .filter(e -> e instanceof BlindPosted bp && bp.blindType() == type)
        .map(BlindPosted.class::cast)
        .findFirst()
        .orElseThrow(() -> new AssertionError("No BlindPosted(" + type + ") in stream"));
  }
}
