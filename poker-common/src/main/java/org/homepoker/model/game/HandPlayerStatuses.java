package org.homepoker.model.game;

import java.util.ArrayList;
import java.util.List;

/**
 * Derives {@link HandPlayerStatus} and {@link SeatSummary} values from a {@link Table}.
 * Centralises the rules for translating raw seat flags into the codified per-hand status
 * clients use to render the table.
 */
public final class HandPlayerStatuses {

  private HandPlayerStatuses() {
  }

  /**
   * Derives the per-hand status for a single seat. The seat must not be {@link Seat.Status#EMPTY}.
   * {@code seatPosition} is 1-indexed (matching the API).
   */
  public static HandPlayerStatus from(Table table, Seat seat, int seatPosition) {
    return switch (seat.status()) {
      case EMPTY -> throw new IllegalArgumentException("Cannot derive HandPlayerStatus for an empty seat");
      case JOINED_WAITING -> HandPlayerStatus.WAITING;
      case FOLDED -> HandPlayerStatus.FOLDED;
      case ACTIVE -> {
        if (seat.isAllIn()) {
          yield HandPlayerStatus.ALL_IN;
        }
        if (seat.mustPostBlind() || seat.missedBigBlind()) {
          yield HandPlayerStatus.SITTING_OUT;
        }
        Integer actionPos = table.actionPosition();
        // A seat is "to act" when action is on them AND they haven't acted yet this round.
        if (actionPos != null && actionPos == seatPosition && seat.action() == null) {
          yield HandPlayerStatus.TO_ACT;
        }
        yield HandPlayerStatus.ACTIVE;
      }
    };
  }

  /**
   * Returns a compact summary of every non-empty seat at the table. Seat positions in
   * the returned summaries are 1-indexed.
   */
  public static List<SeatSummary> snapshot(Table table) {
    List<SeatSummary> summaries = new ArrayList<>();
    List<Seat> seats = table.seats();
    for (int i = 0; i < seats.size(); i++) {
      Seat seat = seats.get(i);
      if (seat.status() == Seat.Status.EMPTY) {
        continue;
      }
      int position = i + 1;
      HandPlayerStatus status = from(table, seat, position);
      Player player = seat.player();
      String userId = player == null ? null : player.userId();
      int chipCount = player == null ? 0 : player.chipCount();
      summaries.add(new SeatSummary(position, userId, status, chipCount, seat.currentBetAmount()));
    }
    return summaries;
  }

  /**
   * Sum of chips across all pots on the table.
   */
  public static int potTotal(Table table) {
    int total = 0;
    for (Table.Pot pot : table.pots()) {
      total += pot.amount();
    }
    for (Seat seat : table.seats()) {
      total += seat.currentBetAmount();
    }
    return total;
  }
}
