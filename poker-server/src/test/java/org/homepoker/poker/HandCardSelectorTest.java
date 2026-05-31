package org.homepoker.poker;

import org.homepoker.model.poker.Card;
import org.homepoker.model.poker.CardValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.homepoker.lib.poker.PokerUtilities.parseCards;
import static org.homepoker.model.poker.CardValue.ACE;
import static org.homepoker.model.poker.CardValue.EIGHT;
import static org.homepoker.model.poker.CardValue.FIVE;
import static org.homepoker.model.poker.CardValue.FOUR;
import static org.homepoker.model.poker.CardValue.KING;
import static org.homepoker.model.poker.CardValue.NINE;
import static org.homepoker.model.poker.CardValue.SEVEN;
import static org.homepoker.model.poker.CardValue.SIX;
import static org.homepoker.model.poker.CardValue.THREE;
import static org.homepoker.model.poker.CardValue.TWO;

class HandCardSelectorTest {

  @Test
  void flushPicksTheFlushSuitNotASameValueCardInAnotherSuit() {
    // As/Kd come BEFORE Ah/Kh so a naive value-only impl would pick the spade ace
    // and diamond king; suit-filtering must select the hearts instead.
    List<Card> pool = parseCards("As Kd Ah Kh 9h 5h 2h");
    List<CardValue> values = List.of(ACE, KING, NINE, FIVE, TWO);

    List<Card> selected = HandCardSelector.select(pool, HandRank.FLUSH, values);

    // All hearts, in value order — NOT the As/Kd from other suits.
    assertThat(selected).containsExactlyElementsOf(parseCards("Ah Kh 9h 5h 2h"));
  }

  @Test
  void straightFlushWheelResolvesTheLowAce() {
    List<Card> pool = parseCards("Ah 2h 3h 4h 5h Ks Qd");
    List<CardValue> values = List.of(FIVE, FOUR, THREE, TWO, ACE);

    List<Card> selected = HandCardSelector.select(pool, HandRank.STRAIGHT_FLUSH, values);

    assertThat(selected).containsExactlyElementsOf(parseCards("5h 4h 3h 2h Ah"));
  }

  @Test
  void fourOfAKindIncludesTheKicker() {
    List<Card> pool = parseCards("As Ah Ad Ac Kd 9s 2c");
    List<CardValue> values = List.of(ACE, ACE, ACE, ACE, KING);

    List<Card> selected = HandCardSelector.select(pool, HandRank.FOUR_OF_A_KIND, values);

    assertThat(selected).containsExactlyInAnyOrderElementsOf(parseCards("As Ah Ad Ac Kd"));
  }

  @Test
  void fullHouseSelectsTripsAndPair() {
    List<Card> pool = parseCards("Ks Kh Kd 2s 2h 9c 5d");
    List<CardValue> values = List.of(KING, KING, KING, TWO, TWO);

    List<Card> selected = HandCardSelector.select(pool, HandRank.FULL_HOUSE, values);

    assertThat(selected).containsExactlyInAnyOrderElementsOf(parseCards("Ks Kh Kd 2s 2h"));
  }

  @Test
  void threeOfAKindIncludesTwoKickers() {
    List<Card> pool = parseCards("As Ah Ad Ks 9s 5d 2c");
    List<CardValue> values = List.of(ACE, ACE, ACE, KING, NINE);

    List<Card> selected = HandCardSelector.select(pool, HandRank.THREE_OF_A_KIND, values);

    assertThat(selected).containsExactlyInAnyOrderElementsOf(parseCards("As Ah Ad Ks 9s"));
  }

  @Test
  void twoPairIncludesTheKicker() {
    List<Card> pool = parseCards("As Ah Ks Kh 9s 5d 2c");
    List<CardValue> values = List.of(ACE, ACE, KING, KING, NINE);

    List<Card> selected = HandCardSelector.select(pool, HandRank.TWO_PAIR, values);

    assertThat(selected).containsExactlyInAnyOrderElementsOf(parseCards("As Ah Ks Kh 9s"));
  }

  @Test
  void pairIncludesThreeKickers() {
    List<Card> pool = parseCards("As Ah Ks 9s 5d 2c 3d");
    List<CardValue> values = List.of(ACE, ACE, KING, NINE, FIVE);

    List<Card> selected = HandCardSelector.select(pool, HandRank.PAIR, values);

    assertThat(selected).containsExactlyInAnyOrderElementsOf(parseCards("As Ah Ks 9s 5d"));
  }

  @Test
  void straightWithAPairedValuePicksExactlyFiveCards() {
    // 9-8-7-6-5 straight; the FIVE appears twice (5s, 5h). Either is acceptable.
    List<Card> pool = parseCards("9s 8h 7d 6c 5s 5h 2c");
    List<CardValue> values = List.of(NINE, EIGHT, SEVEN, SIX, FIVE);

    List<Card> selected = HandCardSelector.select(pool, HandRank.STRAIGHT, values);

    assertThat(selected).hasSize(5);
    assertThat(selected).contains(parseCards("9s 8h 7d 6c").toArray(new Card[0]));
    assertThat(selected).filteredOn(c -> c.value() == FIVE).hasSize(1);
  }

  @Test
  void highCardSelectsTheFiveHighestByValue() {
    List<Card> pool = parseCards("As Kh 9d 5s 3c 2h 7s");
    List<CardValue> values = List.of(ACE, KING, NINE, SEVEN, FIVE);

    List<Card> selected = HandCardSelector.select(pool, HandRank.HIGH_CARD, values);

    assertThat(selected).containsExactlyInAnyOrderElementsOf(parseCards("As Kh 9d 7s 5s"));
  }
}
