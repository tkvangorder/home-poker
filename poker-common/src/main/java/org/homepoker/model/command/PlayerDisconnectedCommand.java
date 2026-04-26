package org.homepoker.model.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.homepoker.model.user.User;

/**
 * Internal command queued by {@code GameManager.removeGameListener} (and by user-id sweeps).
 * Decrements the active-listener ref count for the user on the game-loop thread; the
 * transition from 1 to 0 emits a {@code PlayerDisconnected} event.
 * <p>
 * Server-internal only — intentionally NOT annotated with {@code @GameCommandMarker} so it is
 * excluded from the polymorphic Jackson registry that {@code PokerWebSocketHandler}
 * deserializes inbound messages with. An authenticated client must not be able to forge a
 * disconnect for another userId.
 */
public record PlayerDisconnectedCommand(String gameId, String disconnectedUserId) implements GameCommand {
  @JsonIgnore
  @Override
  public User user() {
    // Synthetic identity: only the id is meaningful. Other fields are sentinel values so
    // log lines that print this user are obviously internal.
    return User.builder()
        .id(disconnectedUserId)
        .email("<internal>")
        .name("<internal>")
        .phone("")
        .build();
  }
}
