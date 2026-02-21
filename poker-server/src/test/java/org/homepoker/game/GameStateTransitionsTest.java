package org.homepoker.game;

import org.homepoker.model.game.Player;
import org.homepoker.model.game.Table;
import org.homepoker.model.game.cash.CashGame;
import org.homepoker.test.TestDataHelper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class GameStateTransitionsTest {

  private static final GameContext context = new GameContext(GameSettings.TEXAS_HOLDEM_SETTINGS);

  @Test
  public void testResetSeating() {

    CashGame game = TestDataHelper.cashGame("test", null);
    List<Player> players = TestDataHelper.generatePlayers(game, 10, true);
    players.forEach(game::addPlayer);

    // Call the method
    GameStateTransitions.resetSeating(game, context);

    assertTableCountsAreBalanced(game);
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