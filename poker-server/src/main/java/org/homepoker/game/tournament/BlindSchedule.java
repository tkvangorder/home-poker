package org.homepoker.game.tournament;

import lombok.Data;
import org.springframework.util.Assert;

import java.util.List;

/**
 * A class to represent the state of a blind schedule. The blind interval  and blind level factor are immutable. The blind levels
 * can be automatically computed using the blind level factor, however, we store the blind levels to allow for customizations to be
 * made.
 *
 * @author tyler.vangorder
 */
@Data
public class BlindSchedule {

  private final int blindLevelFactor;
  private List<Blinds> blindLevels;
  private int currentBlindLevel = 0;

  public BlindSchedule(List<Blinds> blindLevels, int blindLevelFactor) {
    Assert.notNull(blindLevels, "You must provide the blind levels.");
    this.blindLevelFactor = blindLevelFactor;
    this.blindLevels = blindLevels;
  }

  public Blinds getBlinds() {
    if (currentBlindLevel < blindLevels.size()) {
      return blindLevels.get(currentBlindLevel);
    } else {
      //If the current blind level exceeds the pre-determined schedule, we fall back to calculating the level.
      //Note: computeBigBlindAtLevel is 1-based, that is why we add one.
      int bigBlind = TournamentUtilities.computeBigBlindAtLevel(currentBlindLevel + 1, blindLevelFactor);
      return new Blinds(bigBlind / 2, bigBlind);
    }
  }
}
