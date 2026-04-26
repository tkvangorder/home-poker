package org.homepoker.model.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.homepoker.model.user.User;

/**
 * Internal command queued by {@code GameManager.addGameListener}. Bumps the active-listener
 * ref count for the user on the game-loop thread; the first transition from 0 to 1 emits a
 * {@code PlayerReconnected} event when a {@code Player} record already exists for the user.
 * <p>
 * This command is never produced by an external client and is excluded from JSON serialization
 * of player-facing payloads via the inherited write-only {@code user()} contract.
 */
@GameCommandMarker
public record PlayerConnectedCommand(String gameId, String connectedUserId) implements GameCommand {
  @JsonIgnore
  @Override
  public User user() {
    return User.builder()
        .id(connectedUserId)
        .email(connectedUserId)
        .name(connectedUserId)
        .phone("")
        .build();
  }
}
