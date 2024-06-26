package org.homepoker.security;

import org.homepoker.model.user.User;

/**
 * Response returned when either registerUser or login are called. The token represents the user and will expire based
 * on a server-configured property.
 *
 * @param token A token representing the user.
 */
public record AuthenticationResponse(String token, User user) {
}

