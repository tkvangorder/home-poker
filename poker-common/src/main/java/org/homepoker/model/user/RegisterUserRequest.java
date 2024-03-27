package org.homepoker.model.user;

public record RegisterUserRequest(String serverPasscode, User user) {
}
