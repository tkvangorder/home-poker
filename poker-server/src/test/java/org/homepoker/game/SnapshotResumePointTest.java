package org.homepoker.game;

import org.homepoker.model.command.GetTableState;
import org.homepoker.model.event.PokerEvent;
import org.homepoker.model.event.TableEvent;
import org.homepoker.model.event.user.TableSnapshot;
import org.homepoker.test.GameManagerTestFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the spec's snapshot resume-point contract: a {@link TableSnapshot} carries
 * the current table-stream seq at the moment it was built, and the next stamped
 * {@link TableEvent} on that stream is exactly {@code seq + 1}. Together these let a
 * client recover from a snapshot and resume gap detection on the live stream.
 */
class SnapshotResumePointTest {

  @Test
  void tableSnapshotCarriesCurrentStreamSeqAndNextEventIsSeqPlusOne() {
    GameManagerTestFixture fixture = GameManagerTestFixture.singleTableMidHand();
    String tableId = fixture.tableId();

    // The table has been driven into PRE_FLOP_BETTING; multiple TableEvents have been
    // stamped, so the table's current stream seq is > 0.
    long seqBeforeSnapshot = fixture.lastTableSeq(tableId);
    assertThat(seqBeforeSnapshot).isGreaterThan(0L);

    // Issue GetTableState as a player. Snapshot is queued as a UserEvent at fan-out so
    // it does NOT advance the table-stream counter.
    fixture.submitCommand(new GetTableState(fixture.gameId(), tableId, fixture.player1()));
    fixture.tick();

    TableSnapshot snapshot = fixture.savedEvents().stream()
        .filter(TableSnapshot.class::isInstance)
        .map(TableSnapshot.class::cast)
        .reduce((first, second) -> second) // pick the most recent
        .orElseThrow();

    // The snapshot reports the seq that was current at construction.
    assertThat(snapshot.streamSeq()).isEqualTo(seqBeforeSnapshot);

    // Drive one more table event and assert it has seq = N + 1.
    fixture.driveOneMoreTableEvent(tableId);
    PokerEvent next = fixture.lastSavedEvent();
    assertThat(next).isInstanceOf(TableEvent.class);
    assertThat(((TableEvent) next).sequenceNumber()).isEqualTo(seqBeforeSnapshot + 1);
  }
}
