package org.homepoker.model.event.user;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.UserEvent;
import org.homepoker.model.game.Table;

import java.time.Instant;

@EventMarker
public record TableSnapshot(
    Instant timestamp,
    String userId,
    String gameId,
    Table table
) implements UserEvent {
}