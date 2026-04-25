package org.homepoker.model.event;

/**
 * Interface for all game events.
 *
 * @author tyler.vangorder
 */
public interface GameEvent extends PokerEvent {

  String gameId();

  /**
   * Monotonic per-stream sequence number assigned at fan-out. The game stream is one
   * stream; each table has its own stream. {@link UserEvent} instances are excluded
   * from sequence assignment and carry {@code 0}; clients must not use them for gap
   * detection. Set to {@code 0} at construction; the manager stamps the real value
   * by calling {@link #withSequenceNumber(long)}.
   */
  long sequenceNumber();

  /**
   * Return a copy of this event with the given sequence number. Each concrete event
   * record overrides this to return its own type (covariant return).
   */
  GameEvent withSequenceNumber(long sequenceNumber);
}
