package org.homepoker.event;

/**
 * Interface for all game events.
 *
 * @author tyler.vangorder
 */
public interface GameEvent extends Event  {
  Integer getGameId();
}
