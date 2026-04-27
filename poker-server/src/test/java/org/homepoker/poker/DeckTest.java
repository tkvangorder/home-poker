package org.homepoker.poker;

import org.homepoker.model.poker.Card;
import org.homepoker.model.poker.CardSuit;
import org.homepoker.model.poker.CardValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeckTest {

  @Test
  void stackedDeck_drawsInOrderProvided() {
    Card aceSpades = new Card(CardValue.ACE, CardSuit.SPADE);
    Card kingHearts = new Card(CardValue.KING, CardSuit.HEART);
    Card twoClubs = new Card(CardValue.TWO, CardSuit.CLUB);

    Deck deck = new Deck(List.of(aceSpades, kingHearts, twoClubs));

    assertThat(deck.drawCards(1)).containsExactly(aceSpades);
    assertThat(deck.drawCards(2)).containsExactly(kingHearts, twoClubs);
  }

  @Test
  void stackedDeck_rejectsDuplicateCards() {
    Card aceSpades = new Card(CardValue.ACE, CardSuit.SPADE);
    assertThatThrownBy(() -> new Deck(List.of(aceSpades, aceSpades)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate");
  }
}
