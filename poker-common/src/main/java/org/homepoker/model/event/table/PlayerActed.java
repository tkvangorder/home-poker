package org.homepoker.model.event.table;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.TableEvent;
import org.homepoker.model.game.HandPlayerStatus;
import org.homepoker.model.game.PlayerAction;

import java.time.Instant;

@EventMarker
public record PlayerActed(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,
    int seatPosition,
    String userId,
    PlayerAction action,
    int chipCount,
    HandPlayerStatus resultingStatus,
    int currentBet,
    int minimumRaise,
    int potTotal
) implements TableEvent {
  @Override
  public PlayerActed withSequenceNumber(long sequenceNumber) {
    return new PlayerActed(timestamp, sequenceNumber, gameId, tableId, seatPosition,
        userId, action, chipCount, resultingStatus, currentBet, minimumRaise, potTotal);
  }
}
