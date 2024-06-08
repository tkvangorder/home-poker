package org.homepoker.model.game;

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
  AWAY,

  /**
   * Player has been eliminated from the game.
   */
  OUT,

  /**
   * Player is registered for the game but has not connected.
   */
  REGISTERED
}
