package org.homepoker.client;

import static org.homepoker.client.ClientRoutes.*;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.homepoker.domain.user.User;
import org.homepoker.domain.user.UserInformationUpdate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.lang.Nullable;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.shell.Availability;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.ExecutionException;

@Slf4j
@Component
public class ClientConnectionManager {

  private final WebSocketStompClient stompClient;

  @Nullable
  private User currentUser = null;

  @Nullable
  private StompSession stompSession;

  public ClientConnectionManager(WebSocketStompClient stompClient) {
    this.stompClient = stompClient;
  }

  /**
   * Connect to the server as an anonymous user. This is allowed so a user may register with the server.
   * The only thing an anonymous user is allowed to do is connect and register.
   *
   * @param host Poker server host
   * @param port Poker server port
   */
  public void connect(String host, Integer port) {
    connect(host, port, "admin", "admin");
  }

  /**
   * Connect to the server as a specific user.
   *
   * @param host Poker server host
   * @param port Poker server port
   */
  public void connect(String host, Integer port, String userId, String password) {
    disconnect();
    log.info("\nConnecting to server...");
    try {
      WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
      headers.setBasicAuth(userId, password);
      stompSession = stompClient.connectAsync("ws://{host}:{port}/connect", headers, new SessionHandler(), host, port).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
    log.info("\nConnected to {}:{} as {}", host, port, userId);
  }

  /**
   * Update the user's basic contact information. You cannot update a user's password with this method.
   *
   * @param userInformation User information to update.
   */
  public void updateUser(UserInformationUpdate userInformation) {
    stompSession.send(ROUTE_USER_MANAGER_UPDATE_USER, userInformation);
    currentUser = null;
  }

  Availability connectionAvailability() {
    if (stompSession != null && stompSession.isConnected()) {
      return Availability.available();
    }
    return Availability.unavailable("You are not connected to the server.");
  }

  @PreDestroy
  void disconnect() {
    if (stompSession != null) {
      if (stompSession.isConnected()) {
        stompSession.disconnect();
      }
      stompSession = null;
    }
    currentUser = null;
  }

  public boolean isConnected() {
    return false;
  }

  public @Nullable User getCurrentUser() {
    return currentUser;
  }

  private static class SessionHandler extends StompSessionHandlerAdapter {

    @Override
    public Type getPayloadType(StompHeaders headers) {
      return User.class;
    }

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
      //session.subscribe(USER_TOPIC_DESTINATION, this);
      session.subscribe("/user/queue/events", this);
      session.send("/poker/user/get", null);
    }

    @Override
    public void handleFrame(StompHeaders headers, @Nullable Object payload) {
      log.info("Received a message\nHeaders [" +headers + "]\n" + payload);
    }

    @Override
    public void handleException(StompSession session, @Nullable StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
      log.error("WebSocket Exception [" + command + "], Headers: [" + headers + "], Payload: [" + new String(payload) + "] " + exception.getMessage(),  exception);
    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
      log.error("Transport Error: " +  exception.getMessage(),  exception);
    }
  }
}
