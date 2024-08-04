package org.homepoker.model.command;

import lombok.Builder;
import lombok.With;
import org.homepoker.model.user.User;

@Builder
@With
@GameCommandType(CommandId.REGISTER_USER)
public record RegisterUser(String gameId, User user) implements GameCommand {

  @Override
  public CommandId commandId() {
    return CommandId.REGISTER_USER;
  }
}
