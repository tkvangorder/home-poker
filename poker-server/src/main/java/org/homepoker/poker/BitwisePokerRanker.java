package org.homepoker.poker;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BitwisePokerRanker implements ClassicPokerRanker {

  @Override
  public HandResult rankHand(List<Card> cards) {

    //First convert the values into suit-based bitmaps.
    int clubs = 0;
    int hearts = 0;
    int diamonds = 0;
    int spades = 0;
    for (Card card : cards) {

      switch (card.suit()) {
        case CLUB -> clubs = clubs | card.value().getMask();
        case DIAMOND -> diamonds = diamonds | card.value().getMask();
        case HEART -> hearts = hearts | card.value().getMask();
        case SPADE -> spades = spades | card.value().getMask();
        default -> {
        }
      }
    }

    //This method checks for each HandRank in descending order, if the method returns null,
    //we fall through to the next highest check. This is not sophisticated but gets the job
    //done.
    HandResult result = isStraighFlush(clubs, hearts, diamonds, spades);

    if (result == null) {
      result = isFourOfAKind(clubs, hearts, diamonds, spades);
      if (result == null) {
        result = isFullHouse(clubs, hearts, diamonds, spades);
        if (result == null) {
          result = isFlush(clubs, hearts, diamonds, spades);
          if (result == null) {
            result = isStraight(clubs, hearts, diamonds, spades);
            if (result == null) {
              result = isThreeOfAKind(clubs, hearts, diamonds, spades);
              if (result == null) {
                result = isThreeOfAKind(clubs, hearts, diamonds, spades);
                if (result == null) {
                  result = isTwoPair(clubs, hearts, diamonds, spades);
                  if (result == null) {
                    result = isPair(clubs, hearts, diamonds, spades);
                    if (result == null) {
                      List<CardValue> cardValues = getHighestCardValues((clubs | hearts | diamonds | spades), 5);
                      result = new HandResult(HandRank.HIGH_CARD, cardValues);
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    return result;
  }

  /**
   * Check for a straight flush by looking for five consecutive 1s in each of our suit bitmaps.
   *
   * @param clubs    The clubs bitmap
   * @param hearts   The hearts bitmap
   * @param diamonds The diamonds bitmap
   * @param spades   The spades bitmap
   * @return A HandResult if a straight flush is found, null otherwise.
   */
  private HandResult isStraighFlush(int clubs, int hearts, int diamonds, int spades) {

    int[] suits = {clubs, hearts, diamonds, spades};
    for (int suit : suits) {
      //If there are any aces, we need to insure we set the "low" ace when evaluating straights.
      suit = (suit & CardValue.ACE.getMask()) > 0 ? (suit | 0b1) : suit;
      int straight = 0b11111000000000;
      for (int index = 14; index > 4; index--) {
        if ((straight & suit) == straight) {
          return new HandResult(HandRank.STRAIGHT_FLUSH, Arrays.asList(
              CardValue.valueToEnum(index),
              CardValue.valueToEnum(index - 1),
              CardValue.valueToEnum(index - 2),
              CardValue.valueToEnum(index - 3),
              CardValue.valueToEnum(index - 4)));
        }
        straight = straight >> 1;
      }
    }
    return null;
  }

  /**
   * Check for a four of a kind, this will be the only case where logically anding all four suits will result in a non-zero result.
   *
   * @param clubs    The clubs bitmap
   * @param hearts   The hearts bitmap
   * @param diamonds The diamonds bitmap
   * @param spades   The spades bitmap
   * @return A HandResult if a four of a kind is found, null otherwise.
   */
  private HandResult isFourOfAKind(int clubs, int hearts, int diamonds, int spades) {

    //If we logical "and" all four suits, the only time a non-zero number will be present will be when we have the same card in all of the suits.
    int fourOfAKind = clubs & hearts & diamonds & spades;

    if (fourOfAKind > 0) {
      //Lookup the card value for our four of a kind.
      CardValue cardValue = CardValue.maskToEnum(fourOfAKind);
      //exclude our four of kind value and then compute the next highest bit for our fifth card.
      int otherCards = ~fourOfAKind;
      otherCards = otherCards & (clubs | hearts | diamonds | spades);
      CardValue fifthValue = CardValue.maskToEnum(Integer.highestOneBit(otherCards));
      return new HandResult(HandRank.FOUR_OF_A_KIND, Arrays.asList(cardValue, cardValue, cardValue, cardValue, fifthValue));
    }
    return null;
  }

  /**
   * Check for a full house by first doing a logical "and" on all possible combinations of three different suits.
   * If we get a non-zero result, we then negate that three of kind bit and then look for a pair with the remaining cards.
   *
   * @param clubs    The clubs bitmap
   * @param hearts   The hearts bitmap
   * @param diamonds The diamonds bitmap
   * @param spades   The spades bitmap
   * @return A HandResult if a full house is found, null otherwise.
   */
  private HandResult isFullHouse(int clubs, int hearts, int diamonds, int spades) {

    //There are only four operations needed to determine if there is a three of a kind:
    int threeOfKind = (clubs & hearts & diamonds) | (clubs & hearts & spades) | (clubs & diamonds & spades) | (hearts & spades & diamonds);
    if (threeOfKind > 0) {
      //We could actually have two three of a kinds in a seven-card hand, so we just find the highest bit.
      threeOfKind = Integer.highestOneBit(threeOfKind);

      //To look for a pair, we compliment our three of a kind 000100 -> 111011 and then do a logical and with each suit.
      int compliment = ~threeOfKind;
      clubs = clubs & compliment;
      hearts = hearts & compliment;
      diamonds = diamonds & compliment;
      spades = spades & compliment;

      //Now the bit for our three of a kind has been set to 0 across all suits, we can now look for the pair.
      int pair = (clubs & hearts) | (clubs & diamonds) | (clubs & spades) |
          (hearts & diamonds) | (hearts & spades) |
          (diamonds & spades);
      if (pair > 0) {
        pair = Integer.highestOneBit(pair);
        CardValue threeOfKindValue = CardValue.maskToEnum(threeOfKind);
        CardValue pairValue = CardValue.maskToEnum(Integer.highestOneBit(pair));
        return new HandResult(HandRank.FULL_HOUSE, Arrays.asList(
            threeOfKindValue,
            threeOfKindValue,
            threeOfKindValue,
            pairValue,
            pairValue));
      }
    }
    return null;
  }

  /**
   * Check for a flush by looking for one of the suits with 5 or more bits set.
   *
   * @param clubs    The clubs bitmap
   * @param hearts   The hearts bitmap
   * @param diamonds The diamonds bitmap
   * @param spades   The spades bitmap
   * @return A HandResult if a flush is found, null otherwise.
   */
  private HandResult isFlush(int clubs, int hearts, int diamonds, int spades) {

    int[] suits = {clubs, hearts, diamonds, spades};
    for (int suit : suits) {
      if (Integer.bitCount(suit) >= 5) {
        return new HandResult(HandRank.FLUSH, getHighestCardValues(suit, 5));
      }
    }
    return null;
  }

  /**
   * Check for a straight by doing a logical or across all suits and then looking for five consecutive bits,
   * starting at the most significant and then shifting AKQJT -> KQJT9, etc
   *
   * @param clubs    The clubs bitmap
   * @param hearts   The hearts bitmap
   * @param diamonds The diamonds bitmap
   * @param spades   The spades bitmap
   * @return A HandResult if a straight is found, null otherwise.
   */
  private HandResult isStraight(int clubs, int hearts, int diamonds, int spades) {


    int combined = clubs | hearts | diamonds | spades;

    //If there are any aces, we need to insure we set the "low" ace when evaluating straights.
    combined = (combined & CardValue.ACE.getMask()) > 0 ? (combined | 0b1) : combined;

    int straight = 0b11111000000000;
    for (int index = 14; index > 4; index--) {
      if ((straight & combined) == straight) {
        return new HandResult(HandRank.STRAIGHT, Arrays.asList(
            CardValue.valueToEnum(index),
            CardValue.valueToEnum(index - 1),
            CardValue.valueToEnum(index - 2),
            CardValue.valueToEnum(index - 3),
            CardValue.valueToEnum(index - 4)));
      }
      straight = straight >> 1;
    }

    return null;
  }

  /**
   * Check for three of a kind by doing a logical "and" on all possible combinations of three different suits. A non-zero result indicates
   * a three of a kind. This works because we have already evaluated for a full house PRIOR to calling this method.
   *
   * @param clubs    The clubs bitmap
   * @param hearts   The hearts bitmap
   * @param diamonds The diamonds bitmap
   * @param spades   The spades bitmap
   * @return A HandResult if a three of a kind is found, null otherwise.
   */
  private HandResult isThreeOfAKind(int clubs, int hearts, int diamonds, int spades) {

    int threeOfKind = (clubs & hearts & diamonds) | (clubs & hearts & spades) | (clubs & diamonds & spades) | (hearts & spades & diamonds);
    if (threeOfKind > 0) {
      //We could actually have two three of a kinds in a seven-card hand, so we just find the highest bit.
      threeOfKind = Integer.highestOneBit(threeOfKind);

      //We compliment our three of a kind 000100 -> 111011 and then do a logical and with each suit.
      CardValue threeValue = CardValue.maskToEnum(threeOfKind);
      int otherCards = (clubs | hearts | diamonds | spades) & ~threeOfKind;

      List<CardValue> values = new ArrayList<>(Arrays.asList(
          threeValue,
          threeValue,
          threeValue));

      //Then we can compute the next two highest cards (we also might not have more cards)
      int mask = Integer.highestOneBit(otherCards);
      if (mask > 0) {
        values.add(CardValue.maskToEnum(mask));
      }
      mask = Integer.highestOneBit(otherCards & ~mask);
      if (mask > 0) {
        values.add(CardValue.maskToEnum(mask));
      }

      return new HandResult(HandRank.THREE_OF_A_KIND, values);
    }
    return null;
  }

  /**
   * Check for two pair by doing a logical and on each combo of twos suits. A two pair means there will be at least two bits set after
   * doing this.
   *
   * @param clubs    The clubs bitmap
   * @param hearts   The hearts bitmap
   * @param diamonds The diamonds bitmap
   * @param spades   The spades bitmap
   * @return A HandResult if a two pair is found, null otherwise.
   */
  private HandResult isTwoPair(int clubs, int hearts, int diamonds, int spades) {

    int pair = (clubs & hearts) | (clubs & diamonds) | (clubs & spades) |
        (hearts & diamonds) | (hearts & spades) |
        (diamonds & spades);
    //If there are more than one bit set on the result, we have AT LEAST a two pair. (We could actually have three pairs in a 7 card hand.
    if (Integer.bitCount(pair) > 1) {
      //The first pair is the most significant bit.
      int firstPairMask = Integer.highestOneBit(pair);
      //The second pair is the second bit (which we can find by doing a compliment and then logical and to remove the bit of the first pair.
      int secondPairMask = Integer.highestOneBit(pair & ~firstPairMask);
      CardValue firstPair = CardValue.maskToEnum(firstPairMask);
      CardValue secondPair = CardValue.maskToEnum(secondPairMask);
      List<CardValue> values = new ArrayList<>(Arrays.asList(
          firstPair, firstPair,
          secondPair, secondPair));
      int mask = Integer.highestOneBit((clubs | hearts | diamonds | spades) & ~(firstPairMask | secondPairMask));
      if (mask > 0) {
        values.add(CardValue.maskToEnum(mask));
      }
      return new HandResult(HandRank.TWO_PAIR, values);
    }
    return null;
  }

  /**
   * Check for pair by doing a logical and on each combo of twos suits.
   *
   * @param clubs    The clubs bitmap
   * @param hearts   The hearts bitmap
   * @param diamonds The diamonds bitmap
   * @param spades   The spades bitmap
   * @return A HandResult if a pair is found, null otherwise.
   */
  private HandResult isPair(int clubs, int hearts, int diamonds, int spades) {

    int pair = (clubs & hearts) | (clubs & diamonds) | (clubs & spades) |
        (hearts & diamonds) | (hearts & spades) |
        (diamonds & spades);
    if (pair > 0) {
      int pairMask = Integer.highestOneBit(pair);
      int otherCards = (clubs | hearts | diamonds | spades) & ~pairMask;

      CardValue pairValue = CardValue.maskToEnum(pairMask);
      List<CardValue> cardValues = new ArrayList<>(Arrays.asList(pairValue, pairValue));
      cardValues.addAll(getHighestCardValues(otherCards, 3));
      return new HandResult(HandRank.PAIR, cardValues);
    }
    return null;
  }

  /**
   * This method will return the highest n number of card values where "n" is numberOfValues;
   * <p>
   * NOTE: If there are less bits set then requested, this method will return as many values as present or an empty list if there are no bits set.
   *
   * @param bits           bits representing card values as a 14 bit mask (where the least significant bit (low ace) is ignored.
   * @param numberOfValues The number of values to return.
   * @return The highest n number of card values represented in the 14 bit number.
   */
  private static List<CardValue> getHighestCardValues(int bits, int numberOfValues) {
    int index = numberOfValues;
    List<CardValue> values = new ArrayList<>(numberOfValues);
    while (bits != 0 && index > 0) {
      int mask = Integer.highestOneBit(bits);
      values.add(CardValue.maskToEnum(mask));
      bits = bits & ~mask;
      index--;
    }
    return values;
  }
}
