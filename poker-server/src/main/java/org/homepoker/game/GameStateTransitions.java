package org.homepoker.game;

import org.homepoker.game.table.TableUtils;
import org.homepoker.model.game.Game;
import org.homepoker.model.game.Player;
import org.homepoker.model.game.Table;

import java.util.TreeMap;

public class GameStateTransitions {

  /**
   * Completely resets the seating of all players in the game. This mixes up the players and assigns them to new tables.
   *
   * @param game The game to reset seating for.
   * @param context The game context
   * @param <T> The game type
   */
  public static <T extends Game<T>> void resetSeating (Game<T> game, GameContext context) {

    int tableCount = (game.players().size() / context.settings().numberOfSeats()) + 1;

    TreeMap<String, Table> tables = new TreeMap<>();

    String[] tableIds = new String[tableCount];
    for (int i = 0; i < tableCount; i++) {
        tableIds[i] = "TABLE-" + i;
        Table table = Table.builder()
            .id(tableIds[i])
            .emptySeats(context.settings().numberOfSeats())
            .build();
        tables.put(table.id(), table);
    }

    int tableIndex = 0;
    for (Player player : game.players().values()) {
      Table table = tables.get(tableIds[tableIndex]);
      TableUtils.assignPlayerToRandomSeat(player, table);
      if (tableIndex < tableCount - 1) {
        tableIndex++;
      } else if (tableIndex == tableCount) {
        tableIndex = 0;
      }
    }
  }
}
