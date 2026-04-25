package org.homepoker.model.event.game;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.GameEvent;

import java.time.Instant;

@EventMarker
public record PlayerJoined(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String userId
) implements GameEvent {
  @Override
  public PlayerJoined withSequenceNumber(long sequenceNumber) {
    return new PlayerJoined(timestamp, sequenceNumber, gameId, userId);
  }
}
