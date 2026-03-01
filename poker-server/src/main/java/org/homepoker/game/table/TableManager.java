package org.homepoker.game.table;

import org.homepoker.game.GameContext;
import org.homepoker.game.GameSettings;
import org.homepoker.model.command.GameCommand;
import org.homepoker.model.game.Game;
import org.homepoker.model.game.Table;

public abstract class TableManager<T extends Game<T>> {

  private final GameSettings gameSettings;
  protected final Table table;

  public TableManager(GameSettings gameSettings, Table table) {
    this.gameSettings = gameSettings;
    this.table = table;
  }

  protected GameSettings gameSettings() {
    return gameSettings;
  }

  public Table table() {
    return table;
  }

  public final void applyCommand(GameCommand command, Game<T> game, GameContext gameContext) {

    switch (command) {
      default ->
          // Allow the subclass to handle any commands that are specific to the game type.
          applySubcommand(command, game, gameContext);
    }
  }

  /**
   * This method handles state transitions (at the table level), subclasses override this method to
   * handle transitions specific to the game type.
   *
   * @param gameContext The current game context
   */
  public abstract void transitionTable(Game<T> game, GameContext gameContext);

  /**
   * Give subclasses the opportunity to handle any commands that are specific to the game type.
   * @param command The command to apply
   * @param gameContext The current game context
   */
  protected void applySubcommand(GameCommand command, Game<T> game, GameContext gameContext) {
  }
}
