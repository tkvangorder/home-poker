package org.homepoker.poker;

import org.homepoker.poker.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.homepoker.poker.PokerUtilities.parseCards;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class ClassicPokerRankerTest {

  ClassicPokerRanker ranker = new BitwisePokerRanker();

  @Test
  @DisplayName("Test Five Card Hand Results")
  void testFiveCardHandResults() {

    HandResult result = ranker.rankHand(parseCards("AC TC QC KC JC"));
    assertThat(result.getRank()).isEqualTo(HandRank.STRAIGHT_FLUSH);
    assertThat(result.getCardValues()).containsExactly(CardValue.ACE, CardValue.KING, CardValue.QUEEN, CardValue.JACK, CardValue.TEN);
    result = ranker.rankHand(parseCards("AC AH JC AS AD"));
    assertThat(result.getRank()).isEqualTo(HandRank.FOUR_OF_A_KIND);
    assertThat(result.getCardValues()).containsExactly(CardValue.ACE, CardValue.ACE, CardValue.ACE, CardValue.ACE, CardValue.JACK);

    result = ranker.rankHand(parseCards("TC TH TS QD QC"));
    assertThat(result.getRank()).isEqualTo(HandRank.FULL_HOUSE);
    assertThat(result.getCardValues()).containsExactly(CardValue.TEN, CardValue.TEN, CardValue.TEN, CardValue.QUEEN, CardValue.QUEEN);

    result = ranker.rankHand(parseCards("AC 2C QC 7C 3C"));
    assertThat(result.getRank()).isEqualTo(HandRank.FLUSH);
    assertThat(result.getCardValues()).containsExactly(CardValue.ACE, CardValue.QUEEN, CardValue.SEVEN, CardValue.THREE, CardValue.TWO);

    result = ranker.rankHand(parseCards("AC 2D 4C 5H 3C"));
    assertThat(result.getRank()).isEqualTo(HandRank.STRAIGHT);
    assertThat(result.getCardValues()).containsExactly(CardValue.FIVE, CardValue.FOUR, CardValue.THREE, CardValue.TWO, CardValue.ACE);

    result = ranker.rankHand(parseCards("JH QC KD TS AC"));
    assertThat(result.getRank()).isEqualTo(HandRank.STRAIGHT);
    assertThat(result.getCardValues()).containsExactly(CardValue.ACE, CardValue.KING, CardValue.QUEEN, CardValue.JACK, CardValue.TEN);

    result = ranker.rankHand(parseCards("3C 3H QD JC 3S"));
    assertThat(result.getRank()).isEqualTo(HandRank.THREE_OF_A_KIND);
    assertThat(result.getCardValues()).containsExactly(CardValue.THREE, CardValue.THREE, CardValue.THREE, CardValue.QUEEN, CardValue.JACK);

    result = ranker.rankHand(parseCards("3C 4H QD 4C 3S"));
    assertThat(result.getRank()).isEqualTo(HandRank.TWO_PAIR);
    assertThat(result.getCardValues()).containsExactly(CardValue.FOUR, CardValue.FOUR, CardValue.THREE, CardValue.THREE, CardValue.QUEEN);

    result = ranker.rankHand(parseCards("3C 4H QD JC 3S"));
    assertThat(result.getRank()).isEqualTo(HandRank.PAIR);
    assertThat(result.getCardValues()).containsExactly(CardValue.THREE, CardValue.THREE, CardValue.QUEEN, CardValue.JACK, CardValue.FOUR);


    result = ranker.rankHand(parseCards("8H QS 4D AC KC"));
    assertThat(result.getRank()).isEqualTo(HandRank.HIGH_CARD);
    assertThat(result.getCardValues()).containsExactly(CardValue.ACE, CardValue.KING, CardValue.QUEEN, CardValue.EIGHT, CardValue.FOUR);
  }


  @Test
  @DisplayName("Test Seven Card Hand Results")
  void testSevenCardHandResults() {

    HandResult result = ranker.rankHand(parseCards("2C AH AC TC QC KC JC"));
    assertThat(result.getRank()).isEqualTo(HandRank.STRAIGHT_FLUSH);
    assertThat(result.getCardValues()).containsExactly(CardValue.ACE, CardValue.KING, CardValue.QUEEN, CardValue.JACK, CardValue.TEN);
    result = ranker.rankHand(parseCards("AC JH AH JC AS JS AD"));
    assertThat(result.getRank()).isEqualTo(HandRank.FOUR_OF_A_KIND);
    assertThat(result.getCardValues()).containsExactly(CardValue.ACE, CardValue.ACE, CardValue.ACE, CardValue.ACE, CardValue.JACK);

    result = ranker.rankHand(parseCards("TC KC TH TS 4D QD QC"));
    assertThat(result.getRank()).isEqualTo(HandRank.FULL_HOUSE);
    assertThat(result.getCardValues()).containsExactly(CardValue.TEN, CardValue.TEN, CardValue.TEN, CardValue.QUEEN, CardValue.QUEEN);

    result = ranker.rankHand(parseCards("AC 2C 8C QC 7C 3C 8H"));
    assertThat(result.getRank()).isEqualTo(HandRank.FLUSH);
    assertThat(result.getCardValues()).containsExactly(CardValue.ACE, CardValue.QUEEN, CardValue.EIGHT, CardValue.SEVEN, CardValue.THREE);

    result = ranker.rankHand(parseCards("AC 2D 4H 7H 4C 5H 3C"));
    assertThat(result.getRank()).isEqualTo(HandRank.STRAIGHT);
    assertThat(result.getCardValues()).containsExactly(CardValue.FIVE, CardValue.FOUR, CardValue.THREE, CardValue.TWO, CardValue.ACE);

    result = ranker.rankHand(parseCards("JH QC KD TD 8H TS AC"));
    assertThat(result.getRank()).isEqualTo(HandRank.STRAIGHT);
    assertThat(result.getCardValues()).containsExactly(CardValue.ACE, CardValue.KING, CardValue.QUEEN, CardValue.JACK, CardValue.TEN);

    result = ranker.rankHand(parseCards("3C 3H QD JC 2D 9C 3S"));
    assertThat(result.getRank()).isEqualTo(HandRank.THREE_OF_A_KIND);
    assertThat(result.getCardValues()).containsExactly(CardValue.THREE, CardValue.THREE, CardValue.THREE, CardValue.QUEEN, CardValue.JACK);

    result = ranker.rankHand(parseCards("3C 4H QD 4C 3S 2D 2H"));
    assertThat(result.getRank()).isEqualTo(HandRank.TWO_PAIR);
    assertThat(result.getCardValues()).containsExactly(CardValue.FOUR, CardValue.FOUR, CardValue.THREE, CardValue.THREE, CardValue.QUEEN);

    result = ranker.rankHand(parseCards("3C 4H QD JC AS KD 3S"));
    assertThat(result.getRank()).isEqualTo(HandRank.PAIR);
    assertThat(result.getCardValues()).containsExactly(CardValue.THREE, CardValue.THREE, CardValue.ACE, CardValue.KING, CardValue.QUEEN);

    result = ranker.rankHand(parseCards("2D 8H QS 4D AC KC 3C"));
    assertThat(result.getRank()).isEqualTo(HandRank.HIGH_CARD);
    assertThat(result.getCardValues()).containsExactly(CardValue.ACE, CardValue.KING, CardValue.QUEEN, CardValue.EIGHT, CardValue.FOUR);

    //A couple of edge cases
    //Two three pair should be a full house.
    result = ranker.rankHand(parseCards("4H 4S TD 9D 9C 4D 9H"));
    assertThat(result.getRank()).isEqualTo(HandRank.FULL_HOUSE);
    assertThat(result.getCardValues()).containsExactly(CardValue.NINE, CardValue.NINE, CardValue.NINE, CardValue.FOUR, CardValue.FOUR);

    //Low straight flush when all cards are in the same suit.
    result = ranker.rankHand(parseCards("5C 3C 2C 9C 4C JC AC"));
    assertThat(result.getRank()).isEqualTo(HandRank.STRAIGHT_FLUSH);
    assertThat(result.getCardValues()).containsExactly(CardValue.FIVE, CardValue.FOUR, CardValue.THREE, CardValue.TWO, CardValue.ACE);
  }


  @Test
  @DisplayName("Test ranking performance")
  void testSevenCardHandRankingPerformance() {

    //Test that the poker ranker can deal and rank 100,000 hands with 7 players per hand.
    //The ranker should easily accomplish this under 1.2 seconds.
    assertTimeout(Duration.ofMillis(1200), () ->
    {
      int numberOfHands = 100_000;
      int numberOfPlayers = 7;
      //Texas hold'em rules 5 community cards + 2 for each player (7 players) (over 10 rounds)
      for (int index = 0; index < numberOfHands; index++) {
        Deck deck = new Deck();
        List<Card> communityCards = deck.drawCards(5);
        for (int playerIndex = 0; playerIndex < numberOfPlayers; playerIndex++) {
          List<Card> playerHand = deck.drawCards(2);
          List<Card> combined = new ArrayList<>(playerHand);
          combined.addAll(communityCards);
          ranker.rankHand(combined);
        }
      }
    });
  }
}
