package org.homepoker.game;

import org.homepoker.model.event.PokerEvent;
import org.homepoker.model.event.UserEvent;
import org.homepoker.model.event.SystemError;
import org.homepoker.model.user.User;

import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A {@link UserGameListener} that queues user-specific events and provides blocking {@code take()} methods
 * for consumers to wait on events. Only accepts {@link UserEvent}s for the owning user and
 * {@link SystemError}s targeted at the owning user — game-level events are ignored.
 */
public class BlockingUserGameListener extends UserGameListener {

  private final LinkedBlockingQueue<PokerEvent> eventQueue = new LinkedBlockingQueue<>();
  private final Duration timeout;

  public BlockingUserGameListener(User user, Duration timeout) {
    super(user);
    this.timeout = timeout;
  }

  @Override
  public boolean acceptsEvent(PokerEvent event) {
    return switch (event) {
      case UserEvent userEvent -> userEvent.userId().equals(getUser().id());
      case SystemError systemError -> getUser().id().equals(systemError.userId());
      default -> false;
    };
  }

  @Override
  public void onEvent(PokerEvent event) {
    eventQueue.add(event);
  }

  /**
   * Blocks until the next event is available or the timeout expires.
   *
   * @return the next event
   * @throws IllegalStateException if the timeout expires before an event is available
   */
  public PokerEvent take() {
    try {
      PokerEvent event = eventQueue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (event == null) {
        throw new IllegalStateException("Timed out waiting for event after " + timeout);
      }
      return event;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for event", e);
    }
  }

  /**
   * Blocks until an event matching the given condition is received or the timeout expires.
   * Events that do not match the condition are discarded.
   *
   * @param condition a function that returns {@code true} when the desired event is received
   * @return the first event that satisfies the condition
   * @throws IllegalStateException if the timeout expires before a matching event is received
   */
  public PokerEvent take(Function<PokerEvent, Boolean> condition) {
    long deadlineNanos = System.nanoTime() + timeout.toNanos();
    try {
      while (true) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
          throw new IllegalStateException("Timed out waiting for matching event after " + timeout);
        }
        PokerEvent event = eventQueue.poll(remainingNanos, TimeUnit.NANOSECONDS);
        if (event == null) {
          throw new IllegalStateException("Timed out waiting for matching event after " + timeout);
        }
        if (condition.apply(event)) {
          return event;
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for matching event", e);
    }
  }

  /**
   * Drains any queued events, returning the number of events discarded.
   */
  public int drain() {
    int count = eventQueue.size();
    eventQueue.clear();
    return count;
  }
}