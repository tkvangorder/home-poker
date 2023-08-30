package org.homepoker.poker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Represents a deck of cards, the constructor will automatically generate the cards in the deck and shuffle them.
 * <p>
 * Then the "drawCards()" method can be used to draw cards from the deck. (Those cards are removed). This
 * implies that for round of play, a new deck should be created.
 *
 * @author tyler.vangorder
 */
public class Deck {

  private final List<Card> cards = new LinkedList<>();

  public Deck() {
    for (CardSuit suit : CardSuit.values()) {
      for (CardValue value : CardValue.values()) {
        cards.add(new Card(value, suit));
      }
    }
    Collections.shuffle(cards);
  }

  public List<Card> drawCards(int numberOfCards) {
    List<Card> drawnCards = new ArrayList<>(numberOfCards);
    for (int index = 0; index < numberOfCards; index++) {
      drawnCards.add(cards.remove(0));
    }
    return drawnCards;
  }
}
