package org.homepoker.model.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.homepoker.model.user.User;

/**
 * Internal command queued by {@code GameManager.removeGameListener} (and by user-id sweeps).
 * Decrements the active-listener ref count for the user on the game-loop thread; the
 * transition from 1 to 0 emits a {@code PlayerDisconnected} event.
 * <p>
 * This command is never produced by an external client.
 */
@GameCommandMarker
public record PlayerDisconnectedCommand(String gameId, String disconnectedUserId) implements GameCommand {
  @JsonIgnore
  @Override
  public User user() {
    return User.builder()
        .id(disconnectedUserId)
        .email(disconnectedUserId)
        .name(disconnectedUserId)
        .phone("")
        .build();
  }
}
