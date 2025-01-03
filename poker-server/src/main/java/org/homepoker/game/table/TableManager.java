package org.homepoker.game.table;

import org.homepoker.game.GameContext;
import org.homepoker.game.GameSettings;
import org.homepoker.model.command.GameCommand;
import org.homepoker.model.game.Game;
import org.homepoker.model.game.Table;

public abstract class TableManager<T extends Game<T>> {

  private final GameSettings gameSettings;

  public TableManager(GameSettings gameSettings) {
    this.gameSettings = gameSettings;
  }

  protected GameSettings gameSettings() {
    return gameSettings;
  }

  public final void applyCommand(GameCommand command, Game<T> game, Table table, GameContext gameContext) {

    switch (command) {
      default ->
          // Allow the subclass to handle any commands that are specific to the game type.
          applySubcommand(command, game, table, gameContext);
    }
  }

  /**
   * This method handles state transitions (at the table level), subclasses override this method to
   * handle transitions specific to the game type.
   *
   * @param gameContext The current game context
   */
  public abstract void transitionTable(Game<T> game, Table table, GameContext gameContext);

  /**
   * Give subclasses the opportunity to handle any commands that are specific to the game type.
   * @param command The command to apply
   * @param gameContext The current game context
   */
  protected void applySubcommand(GameCommand command, Game<T> game, Table table, GameContext gameContext) {
  }

  public abstract Table createTable(String tableId);
}
