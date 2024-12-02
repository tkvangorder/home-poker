package org.homepoker.model.event.game;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.GameEvent;

import java.time.Instant;

@EventMarker
public record GameMessage(Instant timestamp, String gameId, String message) implements GameEvent {
}
