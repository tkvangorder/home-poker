package org.homepoker.poker;

import org.homepoker.model.poker.Card;
import org.homepoker.model.poker.CardSuit;
import org.homepoker.model.poker.CardValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the concrete {@link Card}s that make up a ranked hand, given the original pool of
 * cards (the player's hole cards plus the community cards) and the {@link HandResult}'s rank
 * and ordered card values.
 * <p>
 * The bitwise ranker computes a hand's strength from suit bitmaps and discards card identity,
 * so the concrete cards must be reconstructed here. For every rank except a flush, matching by
 * value is sufficient (suit is irrelevant). For a FLUSH / STRAIGHT_FLUSH the values alone are
 * ambiguous — a same-value card can exist in a non-flush suit — so selection is restricted to
 * the suit that actually holds the flush (the suit with the most cards; with at most 7 cards
 * exactly one suit can have 5+).
 */
final class HandCardSelector {

  private HandCardSelector() {
  }

  /**
   * @param pool   the cards that were ranked (hole + community)
   * @param rank   the resulting hand rank
   * @param values the resulting card values, most significant first
   * @return the concrete cards forming the hand, in {@code values} order. Size equals
   *         {@code values.size()} for a valid (pool, result) pair.
   */
  static List<Card> select(List<Card> pool, HandRank rank, List<CardValue> values) {
    List<Card> candidates = (rank == HandRank.FLUSH || rank == HandRank.STRAIGHT_FLUSH)
        ? cardsOfSuit(pool, dominantSuit(pool))
        : new ArrayList<>(pool);
    return consumeByValue(candidates, values);
  }

  /** Walks {@code values} in order, consuming one not-yet-used card of each value. */
  private static List<Card> consumeByValue(List<Card> candidates, List<CardValue> values) {
    List<Card> remaining = new ArrayList<>(candidates);
    List<Card> result = new ArrayList<>(values.size());
    for (CardValue value : values) {
      for (int i = 0; i < remaining.size(); i++) {
        if (remaining.get(i).value() == value) {
          result.add(remaining.remove(i));
          break;
        }
      }
    }
    return result;
  }

  private static List<Card> cardsOfSuit(List<Card> pool, CardSuit suit) {
    List<Card> suited = new ArrayList<>();
    for (Card c : pool) {
      if (c.suit() == suit) {
        suited.add(c);
      }
    }
    return suited;
  }

  private static CardSuit dominantSuit(List<Card> pool) {
    int[] counts = new int[CardSuit.values().length];
    for (Card c : pool) {
      counts[c.suit().ordinal()]++;
    }
    int best = 0;
    for (int i = 1; i < counts.length; i++) {
      if (counts[i] > counts[best]) {
        best = i;
      }
    }
    return CardSuit.values()[best];
  }
}
