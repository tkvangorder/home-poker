package org.homepoker.event.game;

import org.homepoker.event.EventMarker;
import org.homepoker.event.GameEvent;

import java.time.Instant;

@EventMarker
public record GameMessage(Instant timestamp, String gameId, String message) implements GameEvent {
}
