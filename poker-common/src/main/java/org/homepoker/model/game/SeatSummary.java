package org.homepoker.model.game;

import org.jspecify.annotations.Nullable;

/**
 * Compact per-seat snapshot suitable for embedding in table events. Captures the
 * minimum state a client needs to render each seat at a point in time.
 *
 * @param seatPosition     1-indexed seat position on the table (range 1..numberOfSeats)
 * @param userId           the seated user's id, or null if the seat is empty
 * @param status           codified per-hand status of the seat
 * @param chipCount        the player's current chip count at the table
 * @param currentBetAmount chips wagered by this seat in the current betting round
 */
public record SeatSummary(
    int seatPosition,
    @Nullable String userId,
    HandPlayerStatus status,
    int chipCount,
    int currentBetAmount
) {
}
