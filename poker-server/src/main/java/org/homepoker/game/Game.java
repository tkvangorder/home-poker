package org.homepoker.game;

import org.homepoker.user.User;

import java.util.List;
import java.util.Map;

/**
 * An interface to define common properties and operations regardless of the game format : CASH/TOURNAMENT
 *
 * @author tyler.vangorder
 */
public interface Game {

  /**
   * Unique Id of the game.
   */
  String getId();

  /**
   * A human readable name for the game.
   */
  String getName();

  /**
   * This is a simple enumeration for the format: CASH or TOURNAMENT
   *
   * @return The game format.
   */
  GameFormat getGameFormat();

  /**
   * What type of poker game? Texas Hold'em, Draw, etc.
   */
  GameType getGameType();

  /**
   * Current status of the game (useful when persisting the game to storage)
   */
  GameStatus getStatus();

  /**
   * User that created/owns the game.
   */
  User getOwner();

  /**
   * The players registered/participating in the game.
   */
  Map<String, Player> getPlayers();

  /**
   * A game may have multiple tables depending on how many players are registered/participating in the game.
   * Each table can hold up to nine players and as players come and go, the players may be moved to different tables.
   */
  List<Table> getTables();
}
