package org.homepoker.game;

import org.homepoker.model.event.GameEvent;
import org.homepoker.model.event.PokerEvent;
import org.homepoker.model.event.TableEvent;
import org.homepoker.model.event.UserEvent;
import org.homepoker.model.event.table.HoleCardsDealt;
import org.homepoker.test.GameManagerTestFixture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the spec's sequence-number contract: per-table monotonic streams,
 * a separate game stream, and UserEvents excluded from numbering.
 */
class SequenceNumbersTest {

  @Test
  void perTableStreamsAreIndependentAndMonotonic() {
    // Use the project's existing test fixture to spin up a two-table game and play a hand on each.
    GameManagerTestFixture fixture = GameManagerTestFixture.twoTablesWithHandPlayed();

    List<PokerEvent> events = fixture.savedEvents();

    // Group TableEvents by tableId. Skip UserEvents so HoleCardsDealt (which is both a
    // TableEvent and a UserEvent) is excluded from sequence-number assertions.
    Map<String, List<Long>> seqByTable = new TreeMap<>();
    for (PokerEvent e : events) {
      if (e instanceof UserEvent) continue;
      if (e instanceof TableEvent te) {
        seqByTable.computeIfAbsent(te.tableId(), id -> new ArrayList<>()).add(te.sequenceNumber());
      }
    }

    assertThat(seqByTable).hasSize(2);
    seqByTable.forEach((tableId, seqs) -> {
      assertThat(seqs)
          .as("table " + tableId + " stream is strictly monotonic starting at 1")
          .first().isEqualTo(1L);
      for (int i = 1; i < seqs.size(); i++) {
        assertThat(seqs.get(i))
            .as("table " + tableId + " seq #" + i)
            .isEqualTo(seqs.get(i - 1) + 1);
      }
    });
  }

  @Test
  void gameStreamIsIndependentOfTableStreams() {
    GameManagerTestFixture fixture = GameManagerTestFixture.twoTablesWithHandPlayed();

    List<Long> gameSeqs = fixture.savedEvents().stream()
        .filter(e -> !(e instanceof UserEvent))
        .filter(e -> e instanceof GameEvent && !(e instanceof TableEvent))
        .map(e -> ((GameEvent) e).sequenceNumber())
        .collect(Collectors.toList());

    assertThat(gameSeqs).isNotEmpty();
    assertThat(gameSeqs.get(0)).isEqualTo(1L);
    for (int i = 1; i < gameSeqs.size(); i++) {
      assertThat(gameSeqs.get(i)).isEqualTo(gameSeqs.get(i - 1) + 1);
    }
  }

  @Test
  void holeCardsDealtCarriesZeroSequenceNumber() {
    GameManagerTestFixture fixture = GameManagerTestFixture.twoTablesWithHandPlayed();

    List<HoleCardsDealt> holeCardEvents = fixture.savedEvents().stream()
        .filter(HoleCardsDealt.class::isInstance)
        .map(HoleCardsDealt.class::cast)
        .toList();

    assertThat(holeCardEvents).isNotEmpty();
    assertThat(holeCardEvents).allSatisfy(e ->
        assertThat(e.sequenceNumber())
            .as("HoleCardsDealt is a UserEvent and must not carry a sequence number")
            .isEqualTo(0L));
  }
}
