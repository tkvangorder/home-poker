package org.homepoker.model.event;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Interface for all game events.
 *
 * @author tyler.vangorder
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
public interface GameEvent extends PokerEvent {

  String gameId();
}
