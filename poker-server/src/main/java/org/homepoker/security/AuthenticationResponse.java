package org.homepoker.security;

/**
 * Response returned when either registerUser or login are called. The token represents the user and will expire based
 * on a server-configured property.
 *
 * @param token A token representing the user.
 */
public record AuthenticationResponse(String token) {
}

