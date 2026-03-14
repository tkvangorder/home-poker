package org.homepoker.model.command;

import org.homepoker.model.user.User;

@GameCommandMarker
public record GetTableState(String gameId, String tableId, User user) implements TableCommand {
}