package org.homepoker.model.event.game;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.GameEvent;
import org.homepoker.model.game.GameStatus;

import java.time.Instant;

@EventMarker
public record GameStatusChanged(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    GameStatus oldStatus,
    GameStatus newStatus
) implements GameEvent {
  @Override
  public GameStatusChanged withSequenceNumber(long sequenceNumber) {
    return new GameStatusChanged(timestamp, sequenceNumber, gameId, oldStatus, newStatus);
  }
}
