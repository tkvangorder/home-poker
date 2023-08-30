package org.homepoker.poker;

public record Card(CardValue value, CardSuit suit) {

  public String toString() {
    return new String(new char[]{value.getValue(), suit.getValue()});
  }
}
