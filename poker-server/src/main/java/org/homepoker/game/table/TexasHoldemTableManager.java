package org.homepoker.game.table;

import org.homepoker.game.GameContext;
import org.homepoker.game.GameSettings;
import org.homepoker.model.game.Game;
import org.homepoker.model.game.Seat;
import org.homepoker.model.game.Table;

import java.util.ArrayList;
import java.util.List;

public class TexasHoldemTableManager<T extends Game<T>> extends TableManager<T> {


  public TexasHoldemTableManager(GameSettings gameSettings) {
    super(gameSettings);
  }

  @Override
  public void transitionTable(Game<T> game, Table table, GameContext gameContext) {
  }

  @Override
  public Table createTable(String tableId) {
    List<Seat> seats = new ArrayList<>();
    for (int i = 0; i < gameSettings().numberOfSeats(); i++) {
      seats.add(Seat.builder().build());
    }
    return Table.builder()
        .id(tableId)
        .seats(seats)
        .build();
  }
}
