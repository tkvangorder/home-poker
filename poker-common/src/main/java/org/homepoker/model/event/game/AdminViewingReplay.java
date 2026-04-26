package org.homepoker.model.event.game;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.GameEvent;

import java.time.Instant;

/**
 * Broadcast warning emitted when an admin views a replay of a hand on a game whose status is
 * not {@code COMPLETED}. Mirrors the existing CLAUDE.md "admin debug-view" rule: when an
 * admin gains visibility into a game that is currently being played, the players in that
 * game must be informed.
 *
 * <p>For replays of {@code COMPLETED} games, no event is emitted (no audience to warn).
 */
@EventMarker
public record AdminViewingReplay(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String adminUserId,
    String adminAlias,
    String tableId,
    int handNumber
) implements GameEvent {
  @Override
  public AdminViewingReplay withSequenceNumber(long sequenceNumber) {
    return new AdminViewingReplay(timestamp, sequenceNumber, gameId, adminUserId, adminAlias, tableId, handNumber);
  }
}
