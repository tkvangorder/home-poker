package org.homepoker.model.event.table;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.TableEvent;
import org.homepoker.model.poker.Card;

import java.time.Instant;
import java.util.List;

@EventMarker
public record ShowdownResult(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,
    List<PotResult> potResults
) implements TableEvent {

  /**
   * Result for a single pot.
   * @param potIndex The index of the pot (0 = main pot)
   * @param potAmount The total chips in this pot
   * @param winners The winning seats and their share
   */
  public record PotResult(int potIndex, int potAmount, List<Winner> winners) {
  }

  /**
   * A winner of a pot or portion of a pot.
   * @param seatPosition The seat position of the winner
   * @param userId The user ID of the winner
   * @param amount The chips won
   * @param handDescription A description of the winning hand (e.g., "Full House, Aces over Kings")
   * @param winningCards The concrete cards (value + suit) that make up the winner's best hand,
   *                     so clients can highlight them. Empty when the hand was won without a
   *                     showdown (e.g., everyone else folded).
   */
  public record Winner(int seatPosition, String userId, int amount, String handDescription,
                       List<Card> winningCards) {
  }

  @Override
  public ShowdownResult withSequenceNumber(long sequenceNumber) {
    return new ShowdownResult(timestamp, sequenceNumber, gameId, tableId, potResults);
  }
}
