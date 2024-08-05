package org.homepoker.event;

import lombok.Builder;
import org.homepoker.model.MessageSeverity;

@Builder
public record UserMessage(String userId, MessageSeverity severity, String message) implements GameEvent, UserEvent {

  @Override
  public GameEventType eventType() {
    return GameEventType.USER_MESSAGE;
  }
}
