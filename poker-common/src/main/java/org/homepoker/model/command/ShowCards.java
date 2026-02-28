package org.homepoker.model.command;

import org.homepoker.model.user.User;

/**
 * Command to show cards during the HAND_COMPLETE review period.
 */
@GameCommandMarker
public record ShowCards(String gameId, String tableId, User user) implements TableCommand {
}
