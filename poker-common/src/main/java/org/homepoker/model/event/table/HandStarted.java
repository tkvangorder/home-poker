package org.homepoker.model.event.table;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.TableEvent;

import java.time.Instant;

@EventMarker
public record HandStarted(
    Instant timestamp,
    String gameId,
    String tableId,
    int handNumber,
    int dealerPosition,
    int smallBlindPosition,
    int bigBlindPosition,
    int smallBlindAmount,
    int bigBlindAmount
) implements TableEvent {
}
