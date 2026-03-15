package org.homepoker.game;

import org.homepoker.game.table.TableUtils;
import org.homepoker.model.game.Game;
import org.homepoker.model.game.Player;
import org.homepoker.model.game.Table;
import org.jspecify.annotations.Nullable;

public class GameUtils {

	/**
	 * Assign a player to a random seat on the table with the fewest players.
	 *
	 * @return The table ID the player was assigned to, or null if no seat was available.
	 */
	public static <T extends Game<T>> @Nullable String assignPlayerToTableWithFewestPlayers(Player player, T game, int numberOfSeats) {
		if (game.tables().isEmpty()) {
			return null;
		}
		Table targetTable = null;
		int minPlayers = Integer.MAX_VALUE;
		for (Table table : game.tables().values()) {
			int playerCount = table.numberOfPlayers();
			if (playerCount < minPlayers) {
				minPlayers = playerCount;
				targetTable = table;
			}
		}
		if (targetTable != null && minPlayers < numberOfSeats) {
			TableUtils.assignPlayerToRandomSeat(player, targetTable);
			return targetTable.id();
		}
		return null;
	}

}
