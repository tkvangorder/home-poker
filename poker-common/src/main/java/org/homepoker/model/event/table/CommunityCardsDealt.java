package org.homepoker.model.event.table;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.TableEvent;
import org.homepoker.model.poker.Card;

import java.time.Instant;
import java.util.List;

@EventMarker
public record CommunityCardsDealt(
    Instant timestamp,
    String gameId,
    String tableId,
    List<Card> cards,
    String phase
) implements TableEvent {
}
