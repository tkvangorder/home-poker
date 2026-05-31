package org.homepoker.poker;

import org.homepoker.model.poker.Card;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.homepoker.lib.poker.PokerUtilities.parseCards;
import static org.homepoker.model.poker.CardValue.FIVE;

class BitwisePokerRankerHandCardsTest {

  private final BitwisePokerRanker ranker = new BitwisePokerRanker();

  @Test
  void rankHandAttachesFlushCardsFromTheCorrectSuit() {
    List<Card> pool = parseCards("Ah Kh 9h 5h 2h As Kd");

    HandResult result = ranker.rankHand(pool);

    assertThat(result.getRank()).isEqualTo(HandRank.FLUSH);
    assertThat(result.getHandCards()).containsExactlyInAnyOrderElementsOf(parseCards("Ah Kh 9h 5h 2h"));
  }

  @Test
  void rankHandAttachesTripsAndKickers() {
    List<Card> pool = parseCards("As Ah Ad Ks 9s 5d 2c");

    HandResult result = ranker.rankHand(pool);

    assertThat(result.getRank()).isEqualTo(HandRank.THREE_OF_A_KIND);
    assertThat(result.getHandCards()).containsExactlyInAnyOrderElementsOf(parseCards("As Ah Ad Ks 9s"));
  }

  @Test
  void rankHandAttachesWheelStraightCards() {
    List<Card> pool = parseCards("Ah 2c 3d 4s 5h Ks Qd");

    HandResult result = ranker.rankHand(pool);

    assertThat(result.getRank()).isEqualTo(HandRank.STRAIGHT);
    assertThat(result.getHandCards()).hasSize(5);
    assertThat(result.getHandCards()).contains(parseCards("2c 3d 4s 5h").toArray(new Card[0]));
    assertThat(result.getHandCards()).filteredOn(c -> c.value() == FIVE).hasSize(1);
  }
}
