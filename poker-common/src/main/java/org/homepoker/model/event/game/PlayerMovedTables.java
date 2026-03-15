package org.homepoker.model.event.game;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.GameEvent;

import java.time.Instant;

@EventMarker
public record PlayerMovedTables(Instant timestamp, String gameId, String userId, String fromTableId, String toTableId) implements GameEvent {
}
