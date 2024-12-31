package org.homepoker.game.table;

import org.homepoker.game.GameContext;
import org.homepoker.model.command.GameCommand;
import org.homepoker.model.game.Game;
import org.homepoker.model.game.Table;

public abstract class TableManager<T extends Game<T>> {

  private final boolean isTwoBoardGame;

  public TableManager(boolean isTwoBoardGame) {
    this.isTwoBoardGame = isTwoBoardGame;
  }

  public boolean isTwoBoardGame() {
    return isTwoBoardGame;
  }

  public final Table applyCommand(GameCommand command, Table table, GameContext gameContext) {

    return switch (command) {
      default ->
          // Allow the subclass to handle any commands that are specific to the game type.
          applySubcommand(command, table, gameContext);
    };
  }

  /**
   * This method handles state transitions (at the table level), subclasses override this method to
   * handle transitions specific to the game type.
   *
   * @param gameContext The current game context
   * @return The updated game context
   */
  protected abstract Table transitionTable(Table table, GameContext gameContext);

  /**
   * Give subclasses the opportunity to handle any commands that are specific to the game type.
   * @param command The command to apply
   * @param gameContext The current game context
   * @return The updated game context
   */
  protected Table applySubcommand(GameCommand command, Table table, GameContext gameContext) {
    return table;
  }
}
