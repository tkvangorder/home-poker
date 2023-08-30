package org.homepoker.poker;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class PokerUtilities {

  private PokerUtilities() {
  }

  /**
   * This method takes an array of string values with two characters each.
   *
   * <P><P>
   * The first characters is meant to represent the card value : '2', '3', '4', '5', '6', '7', '8', '9', 'T', 'J', 'Q', 'K', 'A'
   * <p>
   * The second characters is meant to represent the suite: 'S' = Spades, 'D' = Diamonds, 'H' = Hearts, 'C' = Clubs
   *
   * @param cardStrings A variable list of string values
   * @return A list of strongly typed cards.
   * @throws IllegalArgumentException If any of the inputs do not conform to the above rules.
   */
  public static List<Card> toCards(String... cardStrings) {
    Assert.notNull(cardStrings, "You must provide at least one string value to this method.");
    List<Card> cards = new ArrayList<>(cardStrings.length);
    for (String card : cardStrings) {
      if (card.length() != 2) {
        throw new IllegalArgumentException("Malformed card string value.");
      }
      char valueChar = card.charAt(0);
      if (valueChar > 'T') {
        //This is effectively lowercasing the value (97 ('a') - 65 ('A')  = 32)
        valueChar = (char) (valueChar - 32);
      }
      char suitChar = card.charAt(1);
      if (suitChar > 'S') {
        //This is effectively lowercasing the value (97 ('a') - 65 ('A')  = 32)
        suitChar = (char) (suitChar - 32);
      }
      cards.add(new Card(CardValue.valueToEnum(valueChar), CardSuit.valueToEnum(suitChar)));
    }
    return cards;
  }


  /**
   * This method takes a string value containing sets of two characters separated by a space. Each character pair is meant to represent a card value.
   *
   * <P><P>
   * The first characters is meant to represent the card value : '2', '3', '4', '5', '6', '7', '8', '9', 'T', 'J', 'Q', 'K', 'A'
   * <p>
   * The second characters is meant to represent the suite: 'S' = Spades, 'D' = Diamonds, 'H' = Hearts, 'C' = Clubs
   *
   * @param listOfCards A variable list of string values
   * @return A list of strongly typed cards.
   * @throws IllegalArgumentException If the input is not in the correct format.
   */
  public static List<Card> parseCards(String listOfCards) {
    Assert.notNull(listOfCards, "You cannot pass a null to the parseCards method.");
    listOfCards = listOfCards.trim();
    if (!StringUtils.hasText(listOfCards)) {
      return new ArrayList<>();
    }
    String[] cardStrings = listOfCards.split(" ");
    return toCards(cardStrings);
  }
}
