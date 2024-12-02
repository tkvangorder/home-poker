package org.homepoker.model.game;

import lombok.Builder;
import org.homepoker.model.poker.Card;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A table represents the state of a poker table at a given point in time.
 *
 * @param seats The seats available at the table regardless of whether they are occupied.
 * @param status The current status of the table.
 * @param dealerPosition The position of the dealer "button" at the table.
 * @param actionPosition The position of the player who is currently required to take action.
 * @param communityCards The cards that are in play for all players to use.
 * @param pots The pots that are in play for the current hand. There can potentially be multiple side pots in play depending on player actions.
 */
@Builder
public record Table(List<Seat> seats, Status status, @Nullable Integer dealerPosition, @Nullable Integer actionPosition, List<Card> communityCards, List<Pot> pots) {

  /**
   * A pot represents the collection of chips that are in play for a given hand, along with the seat (positions) that
   * are eligible to win the pot.
   *
   * @param amount The total amount of chips in the pot.
   * @param seatPositions The positions of the seats that are eligible to win the pot.
   */
  public record Pot(int amount, List<Integer> seatPositions) {
  }

  public enum Status {
    PAUSED,
    PLAYING
  }
}
