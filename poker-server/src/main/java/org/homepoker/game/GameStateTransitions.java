package org.homepoker.game;

import org.homepoker.game.table.TableManager;
import org.homepoker.game.table.TableUtils;
import org.homepoker.model.event.game.PlayerSeated;
import org.homepoker.model.game.Game;
import org.homepoker.model.game.Player;
import org.homepoker.model.game.Table;

import java.time.Instant;
import java.util.NavigableMap;
import java.util.function.Function;

public class GameStateTransitions {

  /**
   * Completely resets the seating of all players in the game. Creates new tables via the provided factory
   * and distributes players round-robin across them.
   *
   * @param game The game to reset seating for.
   * @param context The game context
   * @param tableManagers The map of table managers to populate (cleared first)
   * @param tableManagerFactory Factory function that creates a new TableManager (and its Table) for a given table ID
   * @param <T> The game type
   */
  public static <T extends Game<T>> void resetSeating(
      Game<T> game, GameContext context,
      NavigableMap<String, TableManager<T>> tableManagers,
      Function<String, TableManager<T>> tableManagerFactory) {

    long playersWithChips = game.players().values().stream().filter(p -> p.chipCount() > 0).count();
    int tableCount = ((int) playersWithChips / context.settings().numberOfSeats()) + 1;

    tableManagers.clear();
    game.tables().clear();

    String[] tableIds = new String[tableCount];
    for (int i = 0; i < tableCount; i++) {
      tableIds[i] = "TABLE-" + i;
      TableManager<T> tm = tableManagerFactory.apply(tableIds[i]);
      tableManagers.put(tableIds[i], tm);
      game.tables().put(tableIds[i], tm.table());
    }

    // Only seat players who have chips — players without chips must buy in before being seated
    int tableIndex = 0;
    for (Player player : game.players().values()) {
      if (player.chipCount() > 0) {
        Table table = game.tables().get(tableIds[tableIndex]);
        TableUtils.assignPlayerToRandomSeat(player, table);
        context.queueEvent(new PlayerSeated(Instant.now(), game.id(), player.userId(), table.id()));
        tableIndex = (tableIndex + 1) % tableCount;
      }
    }
  }
}
