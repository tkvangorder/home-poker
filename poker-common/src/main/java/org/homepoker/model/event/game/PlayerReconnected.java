package org.homepoker.model.event.game;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.GameEvent;

import java.time.Instant;

/**
 * Emitted when the first active listener for a player who is already in the game reconnects.
 * Not emitted on the initial connection that joins the game (that path emits {@code PlayerJoined});
 * only on subsequent re-attaches from 0 active listeners back to 1.
 */
@EventMarker
public record PlayerReconnected(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String userId
) implements GameEvent {
  @Override
  public PlayerReconnected withSequenceNumber(long sequenceNumber) {
    return new PlayerReconnected(timestamp, sequenceNumber, gameId, userId);
  }
}
