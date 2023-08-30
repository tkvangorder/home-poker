package org.homepoker.poker;

import org.homepoker.poker.Card;
import org.homepoker.poker.PokerUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PokerUtilitiesTest {

  @Test
  @DisplayName("Test toCards error cases")
  void testToCardsErrors() {
    assertThatThrownBy(() -> PokerUtilities.toCards((String[]) null)).as("toCards should throw an IllegalArugmentException ").isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PokerUtilities.toCards("")).as("toCards should throw an IllegalArugmentException when string is empty").isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PokerUtilities.toCards("7SASDFASDF")).as("toCards should throw an IllegalArugmentException because it is greater than 2 characters.").isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PokerUtilities.toCards("FS")).as("toCards should throw an IllegalArugmentException because the value is not valid.").isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PokerUtilities.toCards("7P")).as("toCards should throw an IllegalArugmentException because the suit is not valid").isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Test toCards valid for all card suit/value combinations.")
  void testToCardsValid() {
    char[] values = new char[]{'2', '3', '4', '5', '6', '7', '8', '9', 'T', 'J', 'Q', 'K', 'A', 't', 'j', 'q', 'k', 'a'};
    char[] suits = new char[]{'S', 'D', 'H', 'C', 's', 'd', 'h', 'c'};

    List<String> cardStrings = new ArrayList<>(144);

    for (char suit : suits) {
      for (char value : values) {
        cardStrings.add(new String(new char[]{value, suit}));
      }
    }
    List<Card> cards = PokerUtilities.toCards(cardStrings.toArray(new String[144]));
    assertThat(cards).hasSize(144);
  }

  @Test
  @DisplayName("Test parseCards error cases")
  void testParseCardsErrors() {
    assertThatThrownBy(() -> PokerUtilities.parseCards(null)).as("parseCards should throw an IllegalArugmentException ").isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PokerUtilities.toCards("7S ASDFASDF")).as("toCards should throw an IllegalArugmentException because the second card value  is greater than 2 characters.").isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PokerUtilities.toCards("7H FS")).as("toCards should throw an IllegalArugmentException because the second card value is not valid.").isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PokerUtilities.toCards("7C 7P")).as("toCards should throw an IllegalArugmentException because the second card suit is not valid").isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Test parseCards valid for all card suit/value combinations.")
  void testParseCardsValid() {
    char[] values = new char[]{'2', '3', '4', '5', '6', '7', '8', '9', 'T', 'J', 'Q', 'K', 'A', 't', 'j', 'q', 'k', 'a'};
    char[] suits = new char[]{'S', 'D', 'H', 'C', 's', 'd', 'h', 'c'};

    StringBuilder cardsString = new StringBuilder(288);
    for (char suit : suits) {
      for (char value : values) {
        cardsString.append(value);
        cardsString.append(suit);
        cardsString.append(' ');
      }
    }
    List<Card> cards = PokerUtilities.parseCards(cardsString.toString());
    assertThat(cards).hasSize(144);
  }


  @Test
  @DisplayName("Test parseCards with empty and whitespace")
  void testParseCardsWhitespace() {
    assertThat(PokerUtilities.parseCards("")).isEmpty();
    assertThat(PokerUtilities.parseCards("      ")).isEmpty();
    assertThat(PokerUtilities.parseCards("						")).isEmpty();
  }
}
