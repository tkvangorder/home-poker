package org.homepoker.event.user;

import org.homepoker.event.Event;
import org.homepoker.model.user.User;

import java.util.List;

public record UserSearchCompleted(List<User> users) implements Event {
}
