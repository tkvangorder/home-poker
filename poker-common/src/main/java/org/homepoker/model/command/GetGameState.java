package org.homepoker.model.command;

import org.homepoker.model.user.User;

@GameCommandMarker
public record GetGameState(String gameId, User user) implements GameCommand {
}