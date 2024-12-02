package org.homepoker.model.event.user;

import lombok.Builder;
import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.UserEvent;
import org.homepoker.model.MessageSeverity;

import java.time.Instant;

@Builder
@EventMarker
public record UserMessage(Instant timestamp, String userId, MessageSeverity severity, String message) implements UserEvent {
}
