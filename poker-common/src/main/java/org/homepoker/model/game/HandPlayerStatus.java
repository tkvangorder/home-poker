package org.homepoker.model.game;

/**
 * The codified status of a seat's player within the current hand.
 * <p>
 * Distinct from {@link PlayerStatus} (which tracks the player's presence in the overall
 * game session) and {@link Seat.Status} (the coarser seat lifecycle state). This enum
 * captures the derived, per-hand state that clients need to render the table.
 */
public enum HandPlayerStatus {
  /** Seated but joined mid-hand; not eligible to play this hand. */
  WAITING,
  /** In the hand, not currently on the clock. */
  ACTIVE,
  /** Action is currently on this seat. */
  TO_ACT,
  /** Folded this hand. */
  FOLDED,
  /** Committed all chips; no further action this hand. */
  ALL_IN,
  /** Seated but not participating (must post blind, missed BB, etc.). */
  SITTING_OUT
}
