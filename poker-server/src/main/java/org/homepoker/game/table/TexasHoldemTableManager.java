package org.homepoker.game.table;

import org.homepoker.game.GameContext;
import org.homepoker.model.game.Game;
import org.homepoker.model.game.Table;

public class TexasHoldemTableManager<T extends Game<T>> extends TableManager<T> {
  public TexasHoldemTableManager() {
    super(false, 9);
  }

  @Override
  public void transitionTable(Game<T> game, Table table, GameContext gameContext) {
  }
}
