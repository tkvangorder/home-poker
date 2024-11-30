package org.homepoker.model.command;

import org.homepoker.model.user.User;

@GameCommandMarker
public record UnregisterFromGame(String gameId, User user) implements GameCommand {
}
