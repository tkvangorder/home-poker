package org.homepoker.model.event.table;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.TableEvent;
import org.homepoker.model.game.PlayerAction;

import java.time.Instant;

@EventMarker
public record PlayerTimedOut(
    Instant timestamp,
    String gameId,
    String tableId,
    int seatPosition,
    String userId,
    PlayerAction defaultAction
) implements TableEvent {
}
