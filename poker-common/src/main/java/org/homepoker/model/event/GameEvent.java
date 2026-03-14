package org.homepoker.model.event;

/**
 * Interface for all game events.
 *
 * @author tyler.vangorder
 */
public interface GameEvent extends PokerEvent {

  String gameId();
}
