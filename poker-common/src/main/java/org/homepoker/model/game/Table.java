package org.homepoker.model.game;

import lombok.*;
import lombok.experimental.Accessors;
import org.homepoker.model.poker.Card;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A table represents the state of a poker table at a given point in time.
 */
@Data
@Builder
@Accessors(fluent = true)
public final class Table {
  private final String id;
  private List<Seat> seats;
  private Status status;
  private @Nullable Integer dealerPosition;
  private @Nullable Integer actionPosition;
  private List<Card> communityCards;
  private List<Pot> pots;

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
    PAUSE_AFTER_HAND,
    PAUSED,
    PLAYING
  }

  public static class TableBuilder {
    private Status status = Status.PAUSED;
    private List<Card> communityCards = new ArrayList<>();
    private List<Pot> pots = new ArrayList<>();
  }
}
