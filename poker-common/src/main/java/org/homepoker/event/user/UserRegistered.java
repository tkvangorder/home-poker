package org.homepoker.event.user;

import org.homepoker.event.Event;
import org.homepoker.model.user.User;

public record UserRegistered(User user) implements Event {
}
