package org.homepoker.game;

import org.homepoker.game.table.TableManager;
import org.homepoker.game.table.TexasHoldemTableManager;
import org.homepoker.model.game.Player;
import org.homepoker.model.game.Table;
import org.homepoker.model.game.cash.CashGame;
import org.homepoker.test.TestDataHelper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.NavigableMap;

import static org.assertj.core.api.Assertions.assertThat;

public class GameStateTransitionsTest {

  private static final GameSettings settings = GameSettings.TEXAS_HOLDEM_SETTINGS;
  private static final GameContext context = new GameContext(settings);

  @Test
  public void testResetSeating() {

    CashGame game = TestDataHelper.cashGame("test", null);
    List<Player> players = TestDataHelper.generatePlayers(game, 10, true);
    players.forEach(game::addPlayer);

    NavigableMap<String, TableManager<CashGame>> tableManagers = new TreeMap<>();

    // Call the method
    GameStateTransitions.resetSeating(game, context, tableManagers,
        tableId -> TexasHoldemTableManager.forNewTable(tableId, settings));

    assertTableCountsAreBalanced(game);
    assertThat(tableManagers).hasSameSizeAs(game.tables());
  }

  private static void assertTableCountsAreBalanced(CashGame game) {
    int playerCount = game.players().size();
    int expectedTableCount = (playerCount / context.settings().numberOfSeats()) + 1;
    int minPerTable = playerCount / expectedTableCount;
    int maxPerTable = minPerTable + 1;

    assertThat(game.tables().size()).isEqualTo(expectedTableCount);

    for (Table table : game.tables().values()) {
      assertThat(table.numberOfPlayers()).isBetween(minPerTable, maxPerTable);
    }

    // Verify that the players are correctly assigned to tables.
    Map<String, Integer> tablePlayerCount = new HashMap<>();
    for (Player player : game.players().values()) {
      String tableId = player.tableId();
      assertThat(tableId).isNotNull();
      tablePlayerCount.put(tableId, tablePlayerCount.getOrDefault(tableId, 0) + 1);
    }
    assertThat(tablePlayerCount).hasSize(expectedTableCount);
  }

}
