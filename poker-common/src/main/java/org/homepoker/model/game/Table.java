package org.homepoker.model.game;

import lombok.*;
import lombok.experimental.Accessors;
import org.homepoker.model.poker.Card;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
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
  private HandPhase handPhase;
  private @Nullable Integer dealerPosition;
  private @Nullable Integer actionPosition;
  private @Nullable Integer smallBlindPosition;
  private @Nullable Integer bigBlindPosition;
  private @Nullable Integer lastRaiserPosition;
  private int currentBet;
  private int minimumRaise;
  private int handNumber;
  private @Nullable Instant phaseStartedAt;
  private @Nullable Instant actionDeadline;
  private List<Card> communityCards;
  private List<Pot> pots;


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


  public enum Status {
    PAUSE_AFTER_HAND,
    PAUSED,
    PLAYING
  }

  public static class TableBuilder {
    private Status status = Status.PAUSED;
    private HandPhase handPhase = HandPhase.WAITING_FOR_PLAYERS;
    private List<Card> communityCards = new ArrayList<>();
    private List<Pot> pots = new ArrayList<>();

    public TableBuilder emptySeats(int numberOfSeats) {
      this.seats = new ArrayList<>(numberOfSeats);
      for (int i = 0; i < numberOfSeats; i++) {
        this.seats.add(Seat.builder().build());
      }
      return this;
    }
  }
}
