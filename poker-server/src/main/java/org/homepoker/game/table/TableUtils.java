package org.homepoker.game.table;

import org.homepoker.lib.util.RandomUtils;
import org.homepoker.model.game.Player;
import org.homepoker.model.game.Seat;
import org.homepoker.model.game.Table;

import java.util.List;

public class TableUtils {

  /**
   * Assigns a player to a random, empty seat at a table.
   *
   * @param player The player to assign to a seat.
   * @param table The table to assign the player to.
   */
  public static void assignPlayerToRandomSeat(Player player, Table table) {

    List<Seat> emptySeats = table.seats().stream()
        .filter(seat -> seat.status() == Seat.Status.EMPTY)
        .toList();

    if (emptySeats.isEmpty()) {
      throw new IllegalArgumentException("No empty seats available");
    } else {
      int randomIndex = RandomUtils.randomInt(emptySeats.size());
      Seat seat = emptySeats.get(randomIndex);
      seat.status(Seat.Status.JOINED_WAITING);
      seat.player(player);
      player.tableId(table.id());
    }
  }
}
