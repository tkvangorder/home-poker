package org.homepoker.poker;

public enum CardSuit {
  CLUB('C'),
  HEART('H'),
  DIAMOND('D'),
  SPADE('S');

  private final char value;

  CardSuit(char value) {
    this.value = value;
  }

  public char getValue() {
    return value;
  }

  public static CardSuit valueToEnum(char value) {
    for (CardSuit suit : values()) {
      if (suit.value == value) {
        return suit;
      }
    }
    throw new IllegalArgumentException("Unrecognized suit");
  }
}
