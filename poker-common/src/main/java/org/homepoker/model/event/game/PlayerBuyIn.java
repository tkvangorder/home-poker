package org.homepoker.model.event.game;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.GameEvent;

import java.time.Instant;

@EventMarker
public record PlayerBuyIn(Instant timestamp, String gameId, String userId, int amount, int newChipCount) implements GameEvent {
}
