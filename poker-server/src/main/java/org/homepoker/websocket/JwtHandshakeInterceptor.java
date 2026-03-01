package org.homepoker.websocket;

import lombok.extern.slf4j.Slf4j;
import org.homepoker.security.JwtTokenService;
import org.homepoker.security.PokerUserDetails;
import org.jspecify.annotations.Nullable;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * Intercepts the WebSocket handshake to authenticate the user via JWT token passed as a query parameter.
 * <p>
 * The browser WebSocket API doesn't support custom headers, so the JWT is passed as
 * {@code ?token=<jwt>} in the connection URL. This interceptor validates the token,
 * loads the user, and stores both the {@link PokerUserDetails} and {@code gameId} in
 * the WebSocket session attributes for use by the handler.
 */
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

  static final String ATTR_USER_DETAILS = "pokerUserDetails";
  static final String ATTR_GAME_ID = "gameId";

  private final JwtTokenService jwtTokenService;
  private final UserDetailsService userDetailsService;

  public JwtHandshakeInterceptor(JwtTokenService jwtTokenService, UserDetailsService userDetailsService) {
    this.jwtTokenService = jwtTokenService;
    this.userDetailsService = userDetailsService;
  }

  @Override
  public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
      WebSocketHandler wsHandler, Map<String, Object> attributes) {
    try {
      URI uri = request.getURI();

      // Extract token from query parameter
      String token = UriComponentsBuilder.fromUri(uri).build()
          .getQueryParams().getFirst("token");
      if (token == null || token.isBlank()) {
        log.debug("WebSocket handshake rejected: missing token query parameter");
        return false;
      }

      // Validate JWT and extract loginId
      String loginId = jwtTokenService.extractUserLoginId(token);
      if (loginId == null) {
        log.debug("WebSocket handshake rejected: invalid JWT token");
        return false;
      }

      // Load the user details
      PokerUserDetails userDetails = (PokerUserDetails) userDetailsService.loadUserByUsername(loginId);

      // Extract gameId from the URI path: /ws/games/{gameId}
      String path = uri.getPath();
      String gameId = extractGameId(path);
      if (gameId == null) {
        log.debug("WebSocket handshake rejected: could not extract gameId from path [{}]", path);
        return false;
      }

      attributes.put(ATTR_USER_DETAILS, userDetails);
      attributes.put(ATTR_GAME_ID, gameId);
      return true;

    } catch (Exception e) {
      log.debug("WebSocket handshake rejected due to exception: {}", e.getMessage());
      return false;
    }
  }

  @Override
  public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
      WebSocketHandler wsHandler, @Nullable Exception exception) {
    // No-op
  }

  /**
   * Extracts the gameId from a path like {@code /ws/games/{gameId}}.
   */
  @Nullable
  private static String extractGameId(String path) {
    // Expected path: /ws/games/{gameId}
    String prefix = "/ws/games/";
    if (path.startsWith(prefix) && path.length() > prefix.length()) {
      String gameId = path.substring(prefix.length());
      // Remove trailing slash if present
      if (gameId.endsWith("/")) {
        gameId = gameId.substring(0, gameId.length() - 1);
      }
      return gameId.isBlank() ? null : gameId;
    }
    return null;
  }
}
