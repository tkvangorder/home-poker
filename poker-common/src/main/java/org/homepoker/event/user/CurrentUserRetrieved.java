package org.homepoker.event.user;

import org.homepoker.event.Event;
import org.homepoker.model.user.User;

public record CurrentUserRetrieved(User user) implements Event {
}
