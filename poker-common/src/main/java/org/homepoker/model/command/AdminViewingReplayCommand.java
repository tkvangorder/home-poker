package org.homepoker.model.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.homepoker.model.user.User;

/**
 * Internal command submitted by {@code ReplayController} when an admin replays a hand on a
 * game whose status is not {@code COMPLETED}. The handler validates the user is admin
 * (defense in depth) and emits {@code AdminViewingReplay} through the standard fan-out so
 * connected clients receive the warning.
 *
 * <p>Server-internal only — intentionally NOT annotated with {@code @GameCommandMarker} so it
 * is excluded from the polymorphic Jackson registry. An authenticated client must not be
 * able to spoof a replay-warning event.
 */
public record AdminViewingReplayCommand(
    String gameId,
    User user,
    String tableId,
    int handNumber
) implements GameCommand {

  @JsonIgnore
  @Override
  public User user() {
    return user;
  }
}
