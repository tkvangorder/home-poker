package org.homepoker.test;

import org.homepoker.model.poker.Card;
import org.homepoker.model.poker.CardSuit;
import org.homepoker.model.poker.CardValue;
import org.homepoker.poker.Deck;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeckBuilderTest {

  @Test
  void dealsHoleCardsInRoundOrderThenBoard() {
    Deck deck = DeckBuilder.holdem(3)
        .holeCards(1, "As Ks")
        .holeCards(2, "Qd Qc")
        .holeCards(3, "8h 8d")
        .flop("Js Ts 2c")
        .turn("3d")
        .river("4h")
        .build();

    // Hole-card round 1 (one to each seat)
    assertThat(deck.drawCards(1)).containsExactly(card(CardValue.ACE, CardSuit.SPADE));
    assertThat(deck.drawCards(1)).containsExactly(card(CardValue.QUEEN, CardSuit.DIAMOND));
    assertThat(deck.drawCards(1)).containsExactly(card(CardValue.EIGHT, CardSuit.HEART));
    // Hole-card round 2
    assertThat(deck.drawCards(1)).containsExactly(card(CardValue.KING, CardSuit.SPADE));
    assertThat(deck.drawCards(1)).containsExactly(card(CardValue.QUEEN, CardSuit.CLUB));
    assertThat(deck.drawCards(1)).containsExactly(card(CardValue.EIGHT, CardSuit.DIAMOND));
    // Burn 1
    deck.drawCards(1);
    // Flop
    assertThat(deck.drawCards(3)).containsExactly(
        card(CardValue.JACK, CardSuit.SPADE),
        card(CardValue.TEN, CardSuit.SPADE),
        card(CardValue.TWO, CardSuit.CLUB));
    // Burn 2 + Turn
    deck.drawCards(1);
    assertThat(deck.drawCards(1)).containsExactly(card(CardValue.THREE, CardSuit.DIAMOND));
    // Burn 3 + River
    deck.drawCards(1);
    assertThat(deck.drawCards(1)).containsExactly(card(CardValue.FOUR, CardSuit.HEART));
  }

  @Test
  void rejectsDuplicateCardsAcrossSeatsAndBoard() {
    assertThatThrownBy(() -> DeckBuilder.holdem(2)
        .holeCards(1, "As Ks")
        .holeCards(2, "As 2c")
        .flop("3c 4c 5c").turn("6c").river("7c")
        .build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsMissingSeat() {
    assertThatThrownBy(() -> DeckBuilder.holdem(3)
        .holeCards(1, "As Ks")
        .holeCards(2, "Qd Qc")
        .flop("Js Ts 2c").turn("3d").river("4h")
        .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("seat 3");
  }

  @Test
  void deckHasFiftyTwoCardsTotal() {
    Deck deck = DeckBuilder.holdem(2)
        .holeCards(1, "As Ks")
        .holeCards(2, "Qd Qc")
        .flop("Js Ts 2c").turn("3d").river("4h")
        .build();
    int counted = 0;
    while (true) {
      try {
        deck.drawCards(1);
        counted++;
      } catch (Exception e) {
        break;
      }
    }
    assertThat(counted).isEqualTo(52);
  }

  private static Card card(CardValue value, CardSuit suit) {
    return new Card(value, suit);
  }
}
