package org.homepoker.model.event.game;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.GameEvent;

import java.time.Instant;

@EventMarker
public record PlayerSeated(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String userId,
    String tableId
) implements GameEvent {
  @Override
  public PlayerSeated withSequenceNumber(long sequenceNumber) {
    return new PlayerSeated(timestamp, sequenceNumber, gameId, userId, tableId);
  }
}
