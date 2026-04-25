package org.homepoker.model.event.table;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.TableEvent;
import org.homepoker.model.game.HandPhase;

import java.time.Instant;

/**
 * Emitted whenever a table's {@link HandPhase} changes. Lets clients render the
 * current hand phase explicitly without inferring it from other events.
 */
@EventMarker
public record HandPhaseChanged(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,
    HandPhase oldPhase,
    HandPhase newPhase
) implements TableEvent {
  @Override
  public HandPhaseChanged withSequenceNumber(long sequenceNumber) {
    return new HandPhaseChanged(timestamp, sequenceNumber, gameId, tableId, oldPhase, newPhase);
  }
}
