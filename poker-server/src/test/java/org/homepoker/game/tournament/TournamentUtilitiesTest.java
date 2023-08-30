package org.homepoker.game.tournament;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TournamentUtilitiesTest {

  @Test
  @DisplayName("round chip value to nearest denomination of 50")
  void testRoundToNearest50() {

    //Edge case for numbers lower than 25, should never be zero.
    assertThat(TournamentUtilities.roundToBigBlind(1)).isEqualTo(50);
    assertThat(TournamentUtilities.roundToBigBlind(37)).isEqualTo(50);

    assertThat(TournamentUtilities.roundToBigBlind(274)).isEqualTo(250);
    assertThat(TournamentUtilities.roundToBigBlind(275)).isEqualTo(300);
    assertThat(TournamentUtilities.roundToBigBlind(424)).isEqualTo(400);
    assertThat(TournamentUtilities.roundToBigBlind(425)).isEqualTo(450);
    assertThat(TournamentUtilities.roundToBigBlind(499)).isEqualTo(500);
  }

  @Test
  @DisplayName("round chip value to nearest denomination of 100")
  void testRoundToNearest100() {

    assertThat(TournamentUtilities.roundToBigBlind(549)).isEqualTo(500);
    assertThat(TournamentUtilities.roundToBigBlind(550)).isEqualTo(600);
    assertThat(TournamentUtilities.roundToBigBlind(1249)).isEqualTo(1200);
    assertThat(TournamentUtilities.roundToBigBlind(1250)).isEqualTo(1300);
    assertThat(TournamentUtilities.roundToBigBlind(1949)).isEqualTo(1900);
  }

  @Test
  @DisplayName("round chip value to nearest denomination of 500")
  void testRoundToNearest500() {
    assertThat(TournamentUtilities.roundToBigBlind(2249)).isEqualTo(2000);
    assertThat(TournamentUtilities.roundToBigBlind(2250)).isEqualTo(2500);
    assertThat(TournamentUtilities.roundToBigBlind(4449)).isEqualTo(4500);
    assertThat(TournamentUtilities.roundToBigBlind(4750)).isEqualTo(5000);
  }

  @Test
  @DisplayName("round chip value to nearest denomination of 1000")
  void testRoundToNearest1000() {
    assertThat(TournamentUtilities.roundToBigBlind(5499)).isEqualTo(5000);
    assertThat(TournamentUtilities.roundToBigBlind(5500)).isEqualTo(6000);
    assertThat(TournamentUtilities.roundToBigBlind(9499)).isEqualTo(9000);
    assertThat(TournamentUtilities.roundToBigBlind(9500)).isEqualTo(10000);
    assertThat(TournamentUtilities.roundToBigBlind(19499)).isEqualTo(19000);
    assertThat(TournamentUtilities.roundToBigBlind(19500)).isEqualTo(20000);
  }

  @Test
  @DisplayName("round chip value to nearest denomination of 5000")
  void testRoundToNearest5000() {
    assertThat(TournamentUtilities.roundToBigBlind(22499)).isEqualTo(20000);
    assertThat(TournamentUtilities.roundToBigBlind(22500)).isEqualTo(25000);
    assertThat(TournamentUtilities.roundToBigBlind(47499)).isEqualTo(45000);
    assertThat(TournamentUtilities.roundToBigBlind(47500)).isEqualTo(50000);
  }

  @Test
  @DisplayName("round chip value to nearest denomination of 10000")
  void testRoundToNearest10000() {
    assertThat(TournamentUtilities.roundToBigBlind(54_999)).isEqualTo(50_000);
    assertThat(TournamentUtilities.roundToBigBlind(55_000)).isEqualTo(60_000);
    assertThat(TournamentUtilities.roundToBigBlind(104_999)).isEqualTo(100_000);
    assertThat(TournamentUtilities.roundToBigBlind(105_000)).isEqualTo(110_000);
    assertThat(TournamentUtilities.roundToBigBlind(194_999)).isEqualTo(190_000);
    assertThat(TournamentUtilities.roundToBigBlind(195_000)).isEqualTo(200_000);
  }

  @Test
  @DisplayName("round chip value to nearest denomination of 25000")
  void testRoundToNearest25000() {
    assertThat(TournamentUtilities.roundToBigBlind(212_499)).isEqualTo(200_000);
    assertThat(TournamentUtilities.roundToBigBlind(212_500)).isEqualTo(225_000);
    assertThat(TournamentUtilities.roundToBigBlind(512_499)).isEqualTo(500_000);
    assertThat(TournamentUtilities.roundToBigBlind(512_500)).isEqualTo(525_000);
    assertThat(TournamentUtilities.roundToBigBlind(996_000)).isEqualTo(1_000_000);
  }
}
