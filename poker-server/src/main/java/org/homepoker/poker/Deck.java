package org.homepoker.poker;

import org.homepoker.lib.util.RandomUtils;
import org.homepoker.model.poker.Card;
import org.homepoker.model.poker.CardSuit;
import org.homepoker.model.poker.CardValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

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
    RandomUtils.shuffleCollection(cards);
  }

  private Deck(List<Card> remainingCards) {
    cards.addAll(remainingCards);
  }

  /**
   * Builds a deck from the remaining cards after some have already been dealt.
   * Constructs the full 52-card set, removes the already-dealt cards, shuffles the remainder.
   */
  public static Deck fromRemainingCards(Collection<Card> alreadyDealt) {
    Set<Card> dealt = new HashSet<>(alreadyDealt);
    List<Card> remaining = new ArrayList<>();
    for (CardSuit suit : CardSuit.values()) {
      for (CardValue value : CardValue.values()) {
        Card card = new Card(value, suit);
        if (!dealt.contains(card)) {
          remaining.add(card);
        }
      }
    }
    RandomUtils.shuffleCollection(remaining);
    return new Deck(remaining);
  }

  public List<Card> drawCards(int numberOfCards) {
    List<Card> drawnCards = new ArrayList<>(numberOfCards);
    for (int index = 0; index < numberOfCards; index++) {
      drawnCards.add(cards.removeFirst());
    }
    return drawnCards;
  }
}
