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
  private List<PendingMove> pendingMoves;


  public int numberOfPlayers() {
    return (int) seats.stream().filter(seat -> seat.status() != Seat.Status.EMPTY).count();
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

  /**
   * A pending move represents a deferred table balance move. When a player is in an active hand and needs to be moved,
   * the move is recorded as pending and executed during the next PREDEAL phase.
   *
   * @param seatPosition  The seat position of the player to move.
   * @param targetTableId The ID of the table to move the player to.
   */
  public record PendingMove(int seatPosition, String targetTableId) {
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
    private List<PendingMove> pendingMoves = new ArrayList<>();

    public TableBuilder emptySeats(int numberOfSeats) {
      this.seats = new ArrayList<>(numberOfSeats);
      for (int i = 0; i < numberOfSeats; i++) {
        this.seats.add(Seat.builder().build());
      }
      return this;
    }
  }
}
