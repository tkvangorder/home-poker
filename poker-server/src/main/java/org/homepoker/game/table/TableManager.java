package org.homepoker.game.table;

import org.homepoker.game.GameContext;
import org.homepoker.game.GameSettings;
import org.homepoker.model.command.GameCommand;
import org.homepoker.model.command.GetTableState;
import org.homepoker.model.event.user.TableSnapshot;
import org.homepoker.lib.util.ListUtils;
import org.homepoker.model.game.Game;
import org.homepoker.model.game.Table;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public abstract class TableManager<T extends Game<T>> {

  private final GameSettings gameSettings;
  protected final Table table;

  /**
   * Per-table sequence counter for the table's broadcast event stream. Game-loop thread only.
   * Stamps every {@link org.homepoker.model.event.TableEvent} at fan-out so clients can detect gaps.
   */
  private final AtomicLong tableStreamSeq = new AtomicLong(0);

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

  /**
   * Returns the seq that has already been assigned to the most recently published TableEvent
   * on this table's stream. {@code 0} means none assigned yet.
   */
  public long currentStreamSeq() {
    return tableStreamSeq.get();
  }

  /**
   * Advance and return the next sequence number for this table's stream.
   * Game-loop thread only.
   */
  public long nextStreamSeq() {
    return tableStreamSeq.incrementAndGet();
  }

  public final void applyCommand(GameCommand command, Game<T> game, GameContext gameContext) {

    switch (command) {
      case GetTableState c -> gameContext.queueEvent(new TableSnapshot(
          Instant.now(), c.user().id(), c.gameId(), sanitizeTable(table, c.user().id()), currentStreamSeq()));
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

  /**
   * Creates a copy of the table with hole cards and pending intents stripped from all seats
   * except the requesting user's.
   */
  private static Table sanitizeTable(Table table, String userId) {
    return table.withSeats(ListUtils.map(table.seats(), seat ->
        seat.userLoginId() != null && userId.equals(seat.userLoginId())
            ? seat
            : seat.withCards(null).withPendingIntent(null)
    ));
  }
}
