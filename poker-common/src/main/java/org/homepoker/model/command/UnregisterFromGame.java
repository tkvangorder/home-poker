package org.homepoker.model.command;

import lombok.Builder;
import lombok.With;
import org.homepoker.model.user.User;

@Builder
@With
@GameCommandMarker(GameCommandType.UNREGISTER_FROM_GAME)
public record UnregisterFromGame(String gameId, User user) implements GameCommand {

  @Override
  public GameCommandType commandId() {
    return GameCommandType.UNREGISTER_FROM_GAME;
  }
}
