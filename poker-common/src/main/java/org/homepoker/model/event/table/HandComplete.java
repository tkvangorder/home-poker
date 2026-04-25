package org.homepoker.model.event.table;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.TableEvent;

import java.time.Instant;

@EventMarker
public record HandComplete(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,
    int handNumber
) implements TableEvent {
  @Override
  public HandComplete withSequenceNumber(long sequenceNumber) {
    return new HandComplete(timestamp, sequenceNumber, gameId, tableId, handNumber);
  }
}
