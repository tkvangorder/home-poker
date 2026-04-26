package org.homepoker.model.event.game;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.GameEvent;

import java.time.Instant;

/**
 * Emitted when the last active listener for a player who is currently in the game disconnects.
 * Driven by ref-counted listener tracking in {@code GameManager}.
 */
@EventMarker
public record PlayerDisconnected(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String userId
) implements GameEvent {
  @Override
  public PlayerDisconnected withSequenceNumber(long sequenceNumber) {
    return new PlayerDisconnected(timestamp, sequenceNumber, gameId, userId);
  }
}
