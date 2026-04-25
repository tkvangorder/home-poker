package org.homepoker.model.event.table;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.TableEvent;
import org.homepoker.model.game.Table;

import java.time.Instant;

/**
 * Emitted when a table's status changes (e.g. PAUSED, PLAYING, PAUSE_AFTER_HAND).
 */
@EventMarker
public record TableStatusChanged(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,
    Table.Status oldStatus,
    Table.Status newStatus
) implements TableEvent {
  @Override
  public TableStatusChanged withSequenceNumber(long sequenceNumber) {
    return new TableStatusChanged(timestamp, sequenceNumber, gameId, tableId, oldStatus, newStatus);
  }
}
