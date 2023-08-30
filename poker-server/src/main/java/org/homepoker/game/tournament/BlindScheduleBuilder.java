package org.homepoker.game.tournament;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * This builder provides a fluent DSL for creating a blind schedule for a tournament based on number of factors. The idea is
 * to come up with a reasonable blind schedule based on how long the tournament will last and how many players are in the
 * tournament.
 *
 * @author tyler.vangorder
 */
public class BlindScheduleBuilder {

  private int numberOfPlayers = 9;
  private int startingStack = 5000;
  private Duration blindInterval = Duration.ofMinutes(20);
  private Duration tournamentLength = Duration.ofHours(3);
  private int addOnAmount = 0;
  private int rebuyAmount = 0;

  public static BlindScheduleBuilder blindSchedule() {
    return new BlindScheduleBuilder();
  }

  private BlindScheduleBuilder() {
  }

  /**
   * The number of players registered for the tournament.
   *
   * @param numberOfPlayers The number of players
   * @return this builder
   */
  BlindScheduleBuilder numberOfPlayers(int numberOfPlayers) {
    this.numberOfPlayers = numberOfPlayers;
    return this;
  }

  /**
   * The starting chip size.
   *
   * @param startingStack The starting chip size
   * @return this builder
   */
  BlindScheduleBuilder startingStack(int startingStack) {
    this.startingStack = startingStack;
    return this;
  }

  /**
   * The interval in minutes when the next blind level is activated.
   *
   * @param minutes Interval in minutes.
   * @return this builder
   */
  BlindScheduleBuilder blindInternalMinutes(int minutes) {
    this.blindInterval = Duration.ofMinutes(20);
    return this;
  }

  /**
   * The desired length of the tournament in hours.
   *
   * @param hours The length of the tournament.
   * @return this builder
   */
  BlindScheduleBuilder tournamentLengthHours(int hours) {
    this.tournamentLength = Duration.ofHours(hours);
    return this;
  }

  /**
   * If the tournament supports an "add-on", the amount of chips given to each player that opts to add-on.
   * <p>
   * The default for this is zero which means "no add-ons"
   *
   * @param addOnAmount The amount of chips given for an add-on.
   * @return this builder
   */
  BlindScheduleBuilder addOnAmount(int addOnAmount) {
    this.addOnAmount = addOnAmount;
    return this;
  }

  /**
   * If the tournament supports "re-buys", the amount of chips given to a player if they re-buy.
   * <p>
   * The default for this is zero which means "no re-buys"
   *
   * @param rebuyAmount The amount of chips given for a re-buy.
   * @return this builder
   */
  BlindScheduleBuilder rebuyAmount(int rebuyAmount) {
    this.rebuyAmount = rebuyAmount;
    return this;
  }

  /**
   * Compute a blind schedule based on the parameters supplied to the builder.
   *
   * @return A blind schedule for a tournament.
   */
  public BlindSchedule build() {

    int estimatedTotalChips = numberOfPlayers * startingStack;
    if (rebuyAmount > 0) {
      //Currently, the estimate is about 10 percent of players will elect to re-buy
      estimatedTotalChips = estimatedTotalChips + (int) (numberOfPlayers * .1 * rebuyAmount);
    }
    if (addOnAmount > 0) {
      //Currently, the estimate is about 70 percent of players will elect to add-on.
      estimatedTotalChips = estimatedTotalChips + (int) (numberOfPlayers * .7 * addOnAmount);
    }

    int numberofIntervals = (int) tournamentLength.dividedBy(blindInterval);
    int blindLevelFactor = TournamentUtilities.computeBlindLevelFactor(estimatedTotalChips, numberofIntervals);

    //Precompute the blind levels.
    List<Blinds> blindLevels = new ArrayList<>(numberofIntervals + 1);
    for (int index = 0; index < (numberofIntervals + (numberofIntervals / 2)); index++) {
      int bigBlind = TournamentUtilities.computeBigBlindAtLevel(index, blindLevelFactor);
      blindLevels.add(new Blinds(bigBlind / 2, bigBlind));
    }

    return new BlindSchedule(blindLevels, blindLevelFactor);
  }
}
