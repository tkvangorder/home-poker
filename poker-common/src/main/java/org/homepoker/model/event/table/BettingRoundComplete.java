package org.homepoker.model.event.table;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.TableEvent;
import org.homepoker.model.game.HandPhase;
import org.homepoker.model.game.SeatSummary;
import org.homepoker.model.game.Table;

import java.time.Instant;
import java.util.List;

@EventMarker
public record BettingRoundComplete(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,
    HandPhase completedPhase,
    List<Table.Pot> pots,
    List<SeatSummary> seats,
    int potTotal
) implements TableEvent {
  @Override
  public BettingRoundComplete withSequenceNumber(long sequenceNumber) {
    return new BettingRoundComplete(timestamp, sequenceNumber, gameId, tableId,
        completedPhase, pots, seats, potTotal);
  }
}
