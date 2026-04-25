package org.homepoker.model.event.game;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.GameEvent;

import java.time.Instant;

@EventMarker
public record GameMessage(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String message
) implements GameEvent {
  @Override
  public GameMessage withSequenceNumber(long sequenceNumber) {
    return new GameMessage(timestamp, sequenceNumber, gameId, message);
  }
}
