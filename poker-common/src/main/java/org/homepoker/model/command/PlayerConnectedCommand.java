package org.homepoker.model.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.homepoker.model.user.User;

/**
 * Internal command queued by {@code GameManager.addGameListener}. Bumps the active-listener
 * ref count for the user on the game-loop thread; the first transition from 0 to 1 emits a
 * {@code PlayerReconnected} event when a {@code Player} record already exists for the user.
 * <p>
 * Server-internal only — intentionally NOT annotated with {@code @GameCommandMarker} so it is
 * excluded from the polymorphic Jackson registry that {@code PokerWebSocketHandler}
 * deserializes inbound messages with. An authenticated client must not be able to forge a
 * connect/disconnect for another userId.
 */
public record PlayerConnectedCommand(String gameId, String connectedUserId) implements GameCommand {
  @JsonIgnore
  @Override
  public User user() {
    // Synthetic identity: only the id is meaningful. Other fields are sentinel values so
    // log lines that print this user are obviously internal.
    return User.builder()
        .id(connectedUserId)
        .email("<internal>")
        .name("<internal>")
        .phone("")
        .build();
  }
}
