package org.homepoker.model.command;

import lombok.Builder;
import lombok.With;
import org.homepoker.model.user.User;

@Builder
@With
@GameCommandMarker(GameCommandType.REGISTER_FOR_GAME)
public record RegisterForGame(String gameId, User user) implements GameCommand {

  @Override
  public GameCommandType commandId() {
    return GameCommandType.REGISTER_FOR_GAME;
  }
}
