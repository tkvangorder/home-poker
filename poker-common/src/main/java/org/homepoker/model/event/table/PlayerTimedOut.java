package org.homepoker.model.event.table;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.TableEvent;
import org.homepoker.model.game.PlayerAction;

import java.time.Instant;

@EventMarker
public record PlayerTimedOut(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,
    int seatPosition,
    String userId,
    PlayerAction defaultAction
) implements TableEvent {
  @Override
  public PlayerTimedOut withSequenceNumber(long sequenceNumber) {
    return new PlayerTimedOut(timestamp, sequenceNumber, gameId, tableId, seatPosition, userId, defaultAction);
  }
}
