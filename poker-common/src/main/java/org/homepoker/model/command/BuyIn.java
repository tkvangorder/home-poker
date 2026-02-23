package org.homepoker.model.command;

import org.homepoker.model.user.User;

@GameCommandMarker
public record BuyIn(String gameId, User user, int amount) implements GameCommand {
}
