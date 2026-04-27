package org.homepoker.test;

import org.homepoker.model.poker.Card;
import org.homepoker.model.poker.CardSuit;
import org.homepoker.model.poker.CardValue;
import org.homepoker.poker.Deck;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Test-only builder that produces a {@link Deck} whose draw order matches the implementation's
 * deal order in {@link org.homepoker.game.table.TexasHoldemTableManager}:
 * <ol>
 *   <li>For each seat 1..N: 2 hole cards drawn together (no alternating round, no burn)</li>
 *   <li>Flop (3 cards, no burn)</li>
 *   <li>Turn (1 card, no burn)</li>
 *   <li>River (1 card, no burn)</li>
 *   <li>All remaining cards from the 52-card set, appended in arbitrary order</li>
 * </ol>
 *
 * Card syntax: two-character strings like {@code "As"} (Ace of Spades), {@code "Td"}
 * (Ten of Diamonds), {@code "2c"} (Two of Clubs). Values: {@code A K Q J T 9 8 7 6 5 4 3 2}.
 * Suits: {@code s h d c}.
 */
public final class DeckBuilder {

  private final int numSeats;
  private final Map<Integer, List<Card>> holeCards = new HashMap<>();
  private List<Card> flop;
  private Card turn;
  private Card river;

  private DeckBuilder(int numSeats) {
    this.numSeats = numSeats;
  }

  public static DeckBuilder holdem(int numSeats) {
    if (numSeats < 2) throw new IllegalArgumentException("numSeats must be >= 2");
    return new DeckBuilder(numSeats);
  }

  public DeckBuilder holeCards(int seatPosition, String twoCards) {
    if (seatPosition < 1 || seatPosition > numSeats) {
      throw new IllegalArgumentException("seatPosition out of range: " + seatPosition);
    }
    List<Card> cards = parseCards(twoCards);
    if (cards.size() != 2) {
      throw new IllegalArgumentException("Expected 2 hole cards, got " + cards.size());
    }
    holeCards.put(seatPosition, cards);
    return this;
  }

  public DeckBuilder flop(String threeCards) {
    List<Card> cards = parseCards(threeCards);
    if (cards.size() != 3) {
      throw new IllegalArgumentException("Expected 3 flop cards, got " + cards.size());
    }
    this.flop = cards;
    return this;
  }

  public DeckBuilder turn(String oneCard) {
    List<Card> cards = parseCards(oneCard);
    if (cards.size() != 1) {
      throw new IllegalArgumentException("Expected 1 turn card");
    }
    this.turn = cards.get(0);
    return this;
  }

  public DeckBuilder river(String oneCard) {
    List<Card> cards = parseCards(oneCard);
    if (cards.size() != 1) {
      throw new IllegalArgumentException("Expected 1 river card");
    }
    this.river = cards.get(0);
    return this;
  }

  public Deck build() {
    for (int seat = 1; seat <= numSeats; seat++) {
      if (!holeCards.containsKey(seat)) {
        throw new IllegalStateException("Hole cards not specified for seat " + seat);
      }
    }
    if (flop == null) throw new IllegalStateException("flop not specified");
    if (turn == null) throw new IllegalStateException("turn not specified");
    if (river == null) throw new IllegalStateException("river not specified");

    LinkedHashSet<Card> used = new LinkedHashSet<>();
    List<Card> ordered = new ArrayList<>(52);

    // Hole cards: 2 per seat, in seat order — matches TexasHoldemTableManager.transitionFromDeal.
    for (int seat = 1; seat <= numSeats; seat++) {
      List<Card> seatHoles = holeCards.get(seat);
      addUnique(ordered, used, seatHoles.get(0));
      addUnique(ordered, used, seatHoles.get(1));
    }

    // Flop, turn, river — no burn cards (production code does not burn).
    for (Card c : flop) addUnique(ordered, used, c);
    addUnique(ordered, used, turn);
    addUnique(ordered, used, river);

    // Append any remaining unused cards so the deck always holds 52 (defensive).
    for (CardSuit suit : CardSuit.values()) {
      for (CardValue value : CardValue.values()) {
        Card c = new Card(value, suit);
        if (!used.contains(c)) {
          addUnique(ordered, used, c);
        }
      }
    }

    return new Deck(ordered);
  }

  private static void addUnique(List<Card> ordered, LinkedHashSet<Card> used, Card card) {
    if (!used.add(card)) {
      throw new IllegalArgumentException("Duplicate card: " + card);
    }
    ordered.add(card);
  }

  private static List<Card> parseCards(String input) {
    String[] tokens = input.trim().split("\\s+");
    List<Card> cards = new ArrayList<>(tokens.length);
    for (String t : tokens) {
      if (t.length() != 2) throw new IllegalArgumentException("Bad card token: " + t);
      cards.add(new Card(parseValue(t.charAt(0)), parseSuit(t.charAt(1))));
    }
    return cards;
  }

  private static CardValue parseValue(char c) {
    return switch (Character.toUpperCase(c)) {
      case 'A' -> CardValue.ACE;
      case 'K' -> CardValue.KING;
      case 'Q' -> CardValue.QUEEN;
      case 'J' -> CardValue.JACK;
      case 'T' -> CardValue.TEN;
      case '9' -> CardValue.NINE;
      case '8' -> CardValue.EIGHT;
      case '7' -> CardValue.SEVEN;
      case '6' -> CardValue.SIX;
      case '5' -> CardValue.FIVE;
      case '4' -> CardValue.FOUR;
      case '3' -> CardValue.THREE;
      case '2' -> CardValue.TWO;
      default -> throw new IllegalArgumentException("Bad rank: " + c);
    };
  }

  private static CardSuit parseSuit(char c) {
    return switch (Character.toLowerCase(c)) {
      case 's' -> CardSuit.SPADE;
      case 'h' -> CardSuit.HEART;
      case 'd' -> CardSuit.DIAMOND;
      case 'c' -> CardSuit.CLUB;
      default -> throw new IllegalArgumentException("Bad suit: " + c);
    };
  }
}
