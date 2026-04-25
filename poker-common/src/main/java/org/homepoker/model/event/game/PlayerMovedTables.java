package org.homepoker.model.event.game;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.GameEvent;

import java.time.Instant;

@EventMarker
public record PlayerMovedTables(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String userId,
    String fromTableId,
    String toTableId
) implements GameEvent {
  @Override
  public PlayerMovedTables withSequenceNumber(long sequenceNumber) {
    return new PlayerMovedTables(timestamp, sequenceNumber, gameId, userId, fromTableId, toTableId);
  }
}
