package org.homepoker.model.command;

import org.homepoker.model.user.User;

/**
 * Command for a player to post a blind to enter the game immediately during PREDEAL.
 */
@GameCommandMarker
public record PostBlind(String gameId, String tableId, User user) implements TableCommand {
}
