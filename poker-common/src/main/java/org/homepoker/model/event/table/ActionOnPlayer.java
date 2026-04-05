package org.homepoker.model.event.table;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.TableEvent;

import java.time.Instant;

/**
 * Emitted when the action moves to a player during a betting round. Carries the full
 * decision context so clients can render the action UI without additional state lookups.
 */
@EventMarker
public record ActionOnPlayer(
    Instant timestamp,
    String gameId,
    String tableId,
    int seatPosition,
    String userId,
    Instant actionDeadline,
    int currentBet,
    int minimumRaise,
    int callAmount,
    int playerChipCount,
    int potTotal
) implements TableEvent {
}
