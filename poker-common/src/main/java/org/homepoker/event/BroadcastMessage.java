package org.homepoker.event;

import org.homepoker.model.MessageSeverity;

public record BroadcastMessage(MessageSeverity severity, String message) implements GameEvent {

  @Override
  public GameEventType eventType() {
    return GameEventType.BROADCAST_MESSAGE;
  }
}
