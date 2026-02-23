package org.homepoker.model.event.game;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.GameEvent;
import org.homepoker.model.game.GameStatus;

import java.time.Instant;

@EventMarker
public record GameStatusChanged(Instant timestamp, String gameId, GameStatus oldStatus, GameStatus newStatus) implements GameEvent {
}
