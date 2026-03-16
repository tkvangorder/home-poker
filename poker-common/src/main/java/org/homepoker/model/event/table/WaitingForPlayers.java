package org.homepoker.model.event.table;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.TableEvent;

import java.time.Instant;

/**
 * Emitted when a table enters the WAITING_FOR_PLAYERS phase because there are
 * not enough active players to start a hand.
 */
@EventMarker
public record WaitingForPlayers(
    Instant timestamp,
    String gameId,
    String tableId,
    int activePlayers
) implements TableEvent {
}
