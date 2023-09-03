package org.homepoker.event.user;

import org.homepoker.event.Event;
import org.homepoker.model.user.User;

public record CurrentUserUpdated(User user) implements Event {
}
