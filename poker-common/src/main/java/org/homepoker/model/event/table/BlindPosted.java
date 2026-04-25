package org.homepoker.model.event.table;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.TableEvent;
import org.homepoker.model.game.BlindType;

import java.time.Instant;

@EventMarker
public record BlindPosted(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,
    int seatPosition,   // 1-based
    String userId,
    BlindType blindType,
    long amountPosted
) implements TableEvent {
  @Override
  public BlindPosted withSequenceNumber(long sequenceNumber) {
    return new BlindPosted(timestamp, sequenceNumber, gameId, tableId,
        seatPosition, userId, blindType, amountPosted);
  }
}
