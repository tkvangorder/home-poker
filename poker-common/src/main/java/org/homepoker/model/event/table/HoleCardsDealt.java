package org.homepoker.model.event.table;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.TableEvent;
import org.homepoker.model.event.UserEvent;
import org.homepoker.model.game.Seat.SeatCard;

import java.time.Instant;
import java.util.List;

/**
 * Event indicating hole cards have been dealt to a specific player.
 * Implements both TableEvent (for table-wide broadcast) and UserEvent (for per-player privacy filtering).
 */
@EventMarker
public record HoleCardsDealt(
    Instant timestamp,
    String gameId,
    String tableId,
    String userId,
    int seatPosition,
    List<SeatCard> cards
) implements TableEvent, UserEvent {
}
