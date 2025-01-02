package org.homepoker.game;

import lombok.Builder;

/**
 * This class represents the settings for a game.
 *
 * @param isTwoBoardGame Determines if the game has two community boards
 * @param numberOfSeats The number of seats at the table
 * @param saveIntervalSeconds The interval at which the game state is saved
 * @param seatingTimeSeconds The number of seconds (prior to the start of the game) that players can join the game
 * @param actionTimeSeconds The number of seconds that players have to make an action when it is their turn
 * @param reviewHandTimeSeconds The number of seconds that players have to review hand results (and show their cards)
 */
@Builder
public record GameSettings(
    boolean isTwoBoardGame,
    int numberOfSeats,
    int saveIntervalSeconds,
    int seatingTimeSeconds,
    int actionTimeSeconds,
    int reviewHandTimeSeconds
) {

  public final static GameSettings TEXAS_HOLDEM_SETTINGS = GameSettings.builder()
      .isTwoBoardGame(false)
      .numberOfSeats(9)
      .saveIntervalSeconds(5)
      .seatingTimeSeconds(60)
      .actionTimeSeconds(30)
      .reviewHandTimeSeconds(8)
      .build();

}
