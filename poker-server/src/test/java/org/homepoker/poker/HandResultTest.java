package org.homepoker.poker;

import org.homepoker.model.poker.Card;
import org.homepoker.model.poker.CardValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.homepoker.lib.poker.PokerUtilities.parseCards;

class HandResultTest {

  @Test
  void handCardsDefaultToEmpty() {
    HandResult result = new HandResult(HandRank.PAIR, List.of(CardValue.ACE, CardValue.ACE, CardValue.KING));
    assertThat(result.getHandCards()).isEmpty();
  }

  @Test
  void withHandCardsAttachesCardsAndPreservesRankAndValues() {
    HandResult base = new HandResult(HandRank.PAIR, List.of(CardValue.ACE, CardValue.ACE, CardValue.KING));
    List<Card> cards = parseCards("As Ah Ks");

    HandResult withCards = base.withHandCards(cards);

    assertThat(withCards.getRank()).isEqualTo(HandRank.PAIR);
    assertThat(withCards.getCardValues()).isEqualTo(base.getCardValues());
    assertThat(withCards.getHandCards()).containsExactlyElementsOf(cards);
  }

  @Test
  void equalityAndCompareIgnoreHandCards() {
    List<CardValue> values = List.of(CardValue.ACE, CardValue.ACE, CardValue.KING);
    HandResult withoutCards = new HandResult(HandRank.PAIR, values);
    HandResult withCards = new HandResult(HandRank.PAIR, values).withHandCards(parseCards("As Ah Ks"));

    assertThat(withCards).isEqualTo(withoutCards);
    assertThat(withCards.hashCode()).isEqualTo(withoutCards.hashCode());
    assertThat(withCards.compareTo(withoutCards)).isZero();
  }
}
