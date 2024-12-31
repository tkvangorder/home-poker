package org.homepoker.game.table;

import org.homepoker.game.GameContext;
import org.homepoker.model.game.Game;
import org.homepoker.model.game.Table;

public class TexasHoldemTableManager<T extends Game<T>> extends TableManager<T> {
  public TexasHoldemTableManager() {
    super(false);
  }

  @Override
  protected Table transitionTable(Table table, GameContext gameContext) {
    return table;
  }
}
