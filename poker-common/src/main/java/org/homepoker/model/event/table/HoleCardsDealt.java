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
 *
 * @param cards         The player's own hole cards (visible to the receiving user).
 * @param seatsWithCards 1-based seat positions of all seats that received cards. Allows the client
 *                       to render face-down card indicators for other players without revealing their cards.
 */
@EventMarker
public record HoleCardsDealt(
    Instant timestamp,
    String gameId,
    String tableId,
    String userId,
    int seatPosition,
    List<SeatCard> cards,
    List<Integer> seatsWithCards
) implements TableEvent, UserEvent {
}
