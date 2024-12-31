package org.homepoker.model.game;

import lombok.*;
import org.homepoker.model.poker.Card;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A table represents the state of a poker table at a given point in time.
 */
@Builder
@Setter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public final class Table {
  private final String id;
  private List<Seat> seats;
  private Status status;
  private @Nullable Integer dealerPosition;
  private @Nullable Integer actionPosition;
  private List<Card> communityCards;
  private List<Pot> pots;

  public String id() {
    return id;
  }

  public List<Seat> seats() {
    return seats;
  }

  public Status status() {
    return status;
  }

  public @Nullable Integer dealerPosition() {
    return dealerPosition;
  }

  public @Nullable Integer actionPosition() {
    return actionPosition;
  }

  public List<Card> communityCards() {
    return communityCards;
  }

  public List<Pot> pots() {
    return pots;
  }

  /**
   * A pot represents the collection of chips that are in play for a given hand, along with the seat (positions) that
   * are eligible to win the pot.
   *
   * @param amount        The total amount of chips in the pot.
   * @param seatPositions The positions of the seats that are eligible to win the pot.
   */
  public record Pot(int amount, List<Integer> seatPositions) {
  }

  public enum Status {
    PAUSED_AFTER_HAND,
    PLAYING
  }
}
