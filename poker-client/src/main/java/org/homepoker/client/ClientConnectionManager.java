package org.homepoker.client;

import static org.homepoker.PokerMessageRoutes.*;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.homepoker.event.ApplicationError;
import org.homepoker.event.Event;
import org.homepoker.event.user.CurrentUserRetrieved;
import org.homepoker.event.user.CurrentUserUpdated;
import org.homepoker.lib.util.JsonUtils;
import org.homepoker.model.user.User;
import org.homepoker.model.user.UserInformationUpdate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.shell.Availability;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.ExecutionException;

@Slf4j
@Component
public class ClientConnectionManager {

  private final WebSocketStompClient stompClient;

  private final ApplicationEventPublisher applicationEventPublisher;
  @Nullable
  private User currentUser = null;

  @Nullable
  private StompSession stompSession;

  public ClientConnectionManager(WebSocketStompClient stompClient, ApplicationEventPublisher applicationEventPublisher) {
    this.stompClient = stompClient;
    this.applicationEventPublisher = applicationEventPublisher;
  }

  /**
   * Connect to the server as an anonymous user. This is allowed so a user may register with the server.
   * The only thing an anonymous user is allowed to do is connect and register.
   *
   * @param host Poker server host
   * @param port Poker server port
   */
  public void connect(String host, Integer port) {
    connect(host, port, "anonymous", "anonymous");
  }

  /**
   * Connect to the server as a specific user.
   *
   * @param host Poker server host
   * @param port Poker server port
   */
  public void connect(String host, Integer port, String userId, String password) {
    disconnect();
    log.info("Connecting to server...");
    try {
      WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
      headers.setBasicAuth(userId, password);
      stompSession = stompClient.connectAsync("ws://{host}:{port}/connect", headers, new SessionHandler(), host, port).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
    log.info("Connected to {}:{}", host, port);
  }

  public void send(String route, Object payload) {
    if (!isConnected()) {
      throw new IllegalStateException("You are not connected to the server.");
    }
    assert stompSession != null;
    stompSession.send("/poker" + route, payload);
  }

  public Availability connectionAvailability() {
    if (isConnected()) {
      return Availability.available();
    }
    return Availability.unavailable("You are not connected to the server.");
  }

  @PreDestroy
  void disconnect() {
    if (stompSession != null && stompSession.isConnected()) {
        stompSession.disconnect();
    }
    stompSession = null;
    currentUser = null;
  }

  public boolean isConnected() {
    return stompSession != null && stompSession.isConnected();
  }

  public @Nullable User getCurrentUser() {
    return currentUser;
  }

  @EventListener
  public void currentUserReceived(CurrentUserRetrieved userRetrieved) {
    if (currentUser == null) {
      NotificationService.info("Welcome back " + userRetrieved.user().getName() + "!");
    }
    currentUser = userRetrieved.user();
  }

  @EventListener
  public void currentUserUpdated(CurrentUserUpdated userUpdated) {
    currentUser = userUpdated.user();
    NotificationService.info("User information has been update!" + JsonUtils.toJson(currentUser));
  }
  @EventListener
  public void applicationError(ApplicationError error) {
    NotificationService.error(error.message() + " : " + error.details());
  }

  private class SessionHandler extends StompSessionHandlerAdapter {

    @Override
    public Type getPayloadType(StompHeaders headers) {
      return Event.class;
    }

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
      //session.subscribe(USER_TOPIC_DESTINATION, this);
      session.subscribe("/user" + USER_QUEUE_DESTINATION, this);
      send(ROUTE_USER_MANAGER_GET_CURRENT_USER, null);
    }

    @Override
    public void handleFrame(StompHeaders headers, @Nullable Object payload) {
      if (payload != null) {
        applicationEventPublisher.publishEvent(payload);
      } else {
        //Not sure if there is a use case for this
        NotificationService.error("Received null payload from server.");
      }
    }

    @Override
    public void handleException(StompSession session, @Nullable StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
      NotificationService.error("WebSocket Exception [" + command + "], Headers: [" + headers + "], Payload: [" + new String(payload) + "] " + exception.getMessage(),  exception);
    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
      NotificationService.error("Transport Error: " +  exception.getMessage(),  exception);
    }
  }
}
