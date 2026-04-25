package org.homepoker.model.event.table;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.TableEvent;
import org.homepoker.model.game.HandPhase;
import org.homepoker.model.poker.Card;

import java.time.Instant;
import java.util.List;

@EventMarker
public record CommunityCardsDealt(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,
    List<Card> cards,
    HandPhase phase,
    List<Card> allCommunityCards
) implements TableEvent {
  @Override
  public CommunityCardsDealt withSequenceNumber(long sequenceNumber) {
    return new CommunityCardsDealt(timestamp, sequenceNumber, gameId, tableId, cards, phase, allCommunityCards);
  }
}
