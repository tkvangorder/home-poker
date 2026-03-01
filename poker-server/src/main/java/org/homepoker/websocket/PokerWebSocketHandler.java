package org.homepoker.websocket;

import lombok.extern.slf4j.Slf4j;
import org.homepoker.game.GameManager;
import org.homepoker.game.cash.CashGameService;
import org.homepoker.model.MessageSeverity;
import org.homepoker.model.command.GameCommand;
import org.homepoker.model.event.user.UserMessage;
import org.homepoker.security.PokerUserDetails;
import org.homepoker.model.user.User;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;

/**
 * WebSocket handler that bridges connected clients to the game engine.
 * <p>
 * On connection, a {@link WebSocketGameListener} is created and registered with the game manager
 * so the client receives real-time game events. Incoming text messages are deserialized as
 * {@link GameCommand}s, have the authenticated user injected, and are submitted to the game loop.
 */
@Slf4j
public class PokerWebSocketHandler extends TextWebSocketHandler {

  private static final String ATTR_LISTENER = "webSocketGameListener";
  private static final String ATTR_GAME_MANAGER = "gameManager";

  /** Send timeout in milliseconds */
  private static final int SEND_TIMEOUT_MS = 5_000;
  /** Buffer size limit in bytes */
  private static final int BUFFER_SIZE_LIMIT = 64 * 1024;

  private final CashGameService cashGameService;
  private final ObjectMapper objectMapper;

  public PokerWebSocketHandler(CashGameService cashGameService, ObjectMapper objectMapper) {
    this.cashGameService = cashGameService;
    this.objectMapper = objectMapper;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    PokerUserDetails userDetails = (PokerUserDetails) session.getAttributes().get(JwtHandshakeInterceptor.ATTR_USER_DETAILS);
    String gameId = (String) session.getAttributes().get(JwtHandshakeInterceptor.ATTR_GAME_ID);

    if (userDetails == null || gameId == null) {
      log.warn("WebSocket connection missing authentication attributes, closing session [{}]", session.getId());
      session.close(CloseStatus.POLICY_VIOLATION);
      return;
    }

    User user = userDetails.toUser();

    // Look up the game manager
    GameManager<?> gameManager;
    try {
      gameManager = cashGameService.getGameManger(gameId);
    } catch (Exception e) {
      log.warn("WebSocket connection for unknown game [{}], closing session [{}]", gameId, session.getId());
      session.close(CloseStatus.POLICY_VIOLATION);
      return;
    }

    // Wrap the session for thread safety with backpressure
    WebSocketSession concurrentSession = new ConcurrentWebSocketSessionDecorator(session, SEND_TIMEOUT_MS, BUFFER_SIZE_LIMIT);

    // Create and register the game listener
    WebSocketGameListener listener = new WebSocketGameListener(user, concurrentSession, objectMapper);
    gameManager.addGameListener(listener);

    // Store references for later cleanup
    session.getAttributes().put(ATTR_LISTENER, listener);
    session.getAttributes().put(ATTR_GAME_MANAGER, gameManager);

    log.info("WebSocket connected: user [{}], game [{}], session [{}]", user.loginId(), gameId, session.getId());
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    PokerUserDetails userDetails = (PokerUserDetails) session.getAttributes().get(JwtHandshakeInterceptor.ATTR_USER_DETAILS);
    String gameId = (String) session.getAttributes().get(JwtHandshakeInterceptor.ATTR_GAME_ID);
    GameManager<?> gameManager = (GameManager<?>) session.getAttributes().get(ATTR_GAME_MANAGER);

    if (userDetails == null || gameManager == null) {
      return;
    }

    User user = userDetails.toUser();

    try {
      // Deserialize into a tree, inject the authenticated user, then convert to the concrete command type.
      ObjectNode node = (ObjectNode) objectMapper.readTree(message.getPayload());
      node.set("user", objectMapper.valueToTree(user));
      GameCommand command = objectMapper.treeToValue(node, GameCommand.class);

      // Validate that the command targets the same game as this WebSocket connection
      if (!gameId.equals(command.gameId())) {
        sendError(session, user, "Command gameId [" + command.gameId() + "] does not match WebSocket game [" + gameId + "]");
        return;
      }

      gameManager.submitCommand(command);

    } catch (Exception e) {
      log.warn("Error processing WebSocket message from user [{}]: {}", user.loginId(), e.getMessage());
      sendError(session, user, "Invalid command: " + e.getMessage());
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    WebSocketGameListener listener = (WebSocketGameListener) session.getAttributes().get(ATTR_LISTENER);
    GameManager<?> gameManager = (GameManager<?>) session.getAttributes().get(ATTR_GAME_MANAGER);

    if (listener != null && gameManager != null) {
      gameManager.removeGameListener(listener);
      log.info("WebSocket disconnected: user [{}], session [{}], status [{}]",
          listener.getUser().loginId(), session.getId(), status);
    }
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
    log.error("WebSocket transport error for session [{}]: {}", session.getId(), exception.getMessage());
    session.close(CloseStatus.SERVER_ERROR);
  }

  private void sendError(WebSocketSession session, User user, String message) {
    try {
      UserMessage errorEvent = UserMessage.builder()
          .timestamp(Instant.now())
          .userId(user.id())
          .severity(MessageSeverity.ERROR)
          .message(message)
          .build();
      String json = objectMapper.writeValueAsString(errorEvent);
      session.sendMessage(new TextMessage(json));
    } catch (Exception e) {
      log.debug("Failed to send error message to session [{}]", session.getId(), e);
    }
  }
}
