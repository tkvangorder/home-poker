package org.homepoker.model.event.table;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.TableEvent;
import org.homepoker.model.game.SeatSummary;

import java.time.Instant;
import java.util.List;

@EventMarker
public record HandStarted(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,
    int handNumber,
    int dealerPosition,
    int smallBlindPosition,
    int bigBlindPosition,
    int smallBlindAmount,
    int bigBlindAmount,
    int currentBet,
    int minimumRaise,
    List<SeatSummary> seats
) implements TableEvent {
  @Override
  public HandStarted withSequenceNumber(long sequenceNumber) {
    return new HandStarted(timestamp, sequenceNumber, gameId, tableId, handNumber,
        dealerPosition, smallBlindPosition, bigBlindPosition, smallBlindAmount,
        bigBlindAmount, currentBet, minimumRaise, seats);
  }
}
