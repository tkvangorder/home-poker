package org.homepoker.game;

/**
 * The current status of a player within the context of a game.
 *
 * @author tyler.vangorder
 */
public enum PlayerStatus {

  /**
   * Player is active and responding to game events.
   */
  ACTIVE,

  /**
   * Player is in a game but idle.
   */
  AWAY,   //Active player but has stepped away from the table.

  /**
   * Player has been eliminated from the game.
   */
  OUT    //Player is out of the game (no chips)
}
