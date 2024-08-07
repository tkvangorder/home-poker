package org.homepoker.event;

import com.fasterxml.jackson.annotation.JsonTypeId;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Interface for all game events.
 *
 * @author tyler.vangorder
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
public interface GameEvent {
  @JsonTypeId
  GameEventType eventType();
}
