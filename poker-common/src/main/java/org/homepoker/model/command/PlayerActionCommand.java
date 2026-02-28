package org.homepoker.model.command;

import org.homepoker.model.game.PlayerAction;
import org.homepoker.model.user.User;

@GameCommandMarker
public record PlayerActionCommand(String gameId, String tableId, User user, PlayerAction action) implements TableCommand {
}
