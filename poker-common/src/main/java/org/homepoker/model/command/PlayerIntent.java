package org.homepoker.model.command;

import org.homepoker.model.game.PlayerAction;
import org.homepoker.model.user.User;

/**
 * A player intent allows a player to pre-select their action before their turn arrives.
 * The intent will be auto-applied when their turn comes, if still valid.
 */
@GameCommandMarker
public record PlayerIntent(String gameId, String tableId, User user, PlayerAction action) implements TableCommand {
}
