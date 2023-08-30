package org.homepoker.poker;


import java.util.List;

/**
 * This interface defines the contract for ranking an arbitrary set of cards and returning a {@link HandResult} to represent the
 * BEST hand for a classic poker game including five-card variants (like draw) and seven-card variants (Texas Hold'em, 7 Card Stud, etc)
 *
 * @author tyler.vangorder
 */
public interface ClassicPokerRanker {

  /**
   * This method will accept a list of cards and return the best hand result that can be formed with those cards.
   * <p>
   * The strength of a HandResult is first based on the HandRank (in descending order)
   *
   * <p>
   * <li>{@link HandRank#STRAIGHT_FLUSH}</li>
   * <li>{@link HandRank#FOUR_OF_A_KIND}</li>
   * <li>{@link HandRank#FULL_HOUSE}</li>
   * <li>{@link HandRank#FLUSH}</li>
   * <li>{@link HandRank#STRAIGHT}</li>
   * <li>{@link HandRank#THREE_OF_A_KIND}</li>
   * <li>{@link HandRank#TWO_PAIR}</li>
   * <li>{@link HandRank#PAIR}</li>
   * <li>{@link HandRank#HIGH_CARD}</li>
   * <p>
   * <p>
   * The list of {@link CardValue} where the CardValues are sorted using a poker lexioghraphical order (poker alphabet) where
   * the most significant CardValue is first and the least significant card is last. If more than five cards are passed into this
   * method, the two least significant cards will be discarded.
   *
   * <P><P>
   * <PRE>
   * <p>
   * Examples:
   * (FULL HOUSE 2 2 2 3 3) is stronger than (STRAIGHT A K Q J T)
   * (FULL HOUSE 3 3 3 2 2) is stronger than (FULL HOUSE 2 2 2 3 3)
   * (STRAIGHT FLUSH 5 4 3 2 A) is stronger than (FOUR OF A KIND A A A A K)
   * (STRAIGHT 6 5 4 3 2) is stronger than (STRAIGHT 5 4 3 2 A)
   *
   * </PRE>
   *
   * @author tyler.vangorder
   */
  HandResult rankHand(List<Card> cards);
}
