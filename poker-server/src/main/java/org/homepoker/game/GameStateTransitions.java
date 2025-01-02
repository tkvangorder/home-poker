package org.homepoker.game;

import org.homepoker.model.game.Game;
import org.homepoker.model.game.Table;

public class GameStateTransitions {

  public static void resetSeating(Game game, GameContext context) {


    int tableCount = (game.players().size() / context.settings().numberOfSeats()) + 1;
    for (int i = 0; i < tableCount; i++) {
      game.tables().put("TABLE-" + i, Table.builder().id("Table-" + i).build());
    }

//    gameContext.table().seats().forEach(seat -> {
//      seat.player(null);
//      seat.status(SeatStatus.EMPTY);
//    });
  }
}
