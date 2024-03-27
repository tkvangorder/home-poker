package org.homepoker.client;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.homepoker.event.MessageReceived;
import org.homepoker.event.user.CurrentUserRetrieved;
import org.homepoker.event.user.CurrentUserUpdated;
import org.homepoker.lib.util.JsonUtils;
import org.homepoker.model.user.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.shell.Availability;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.concurrent.ExecutionException;

@Slf4j
@Component
public class ClientConnectionManager {

  private final ApplicationEventPublisher applicationEventPublisher;
  @Nullable
  private User currentUser = null;

  public ClientConnectionManager(ApplicationEventPublisher applicationEventPublisher) {
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
//    try {
//      WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
//      headers.setBasicAuth(userId, password);
//      stompSession = stompClient.connectAsync("ws://{host}:{port}/connect", headers, new SessionHandler(), host, port).get();
//    } catch (InterruptedException | ExecutionException e) {
//      throw new RuntimeException(e);
//    }
    log.info("Connected to {}:{}", host, port);
  }

  public void send(String route, Object payload) {
    if (!isConnected()) {
      throw new IllegalStateException("You are not connected to the server.");
    }
//    assert stompSession != null;
//    stompSession.send("/poker" + route, payload);
  }

  public Availability connectionAvailability() {
    if (isConnected()) {
      return Availability.available();
    }
    return Availability.unavailable("You are not connected to the server.");
  }

  @PreDestroy
  void disconnect() {
//    if (stompSession != null && stompSession.isConnected()) {
//        stompSession.disconnect();
//    }
//    stompSession = null;
    currentUser = null;
  }

  public boolean isConnected() {
    return false;
  }

  public @Nullable User getCurrentUser() {
    return currentUser;
  }

  @EventListener
  public void currentUserReceived(CurrentUserRetrieved userRetrieved) {
    if (currentUser == null) {
      NotificationService.info("Welcome back " + userRetrieved.user().name() + "!");
    }
    currentUser = userRetrieved.user();
  }

  @EventListener
  public void currentUserUpdated(CurrentUserUpdated userUpdated) {
    currentUser = userUpdated.user();
    NotificationService.info("User information has been update!" + JsonUtils.toJson(currentUser));
  }
  @EventListener
  public void messageReceived(MessageReceived messageReceived) {
    switch (messageReceived.message().severity()) {
      case INFO -> NotificationService.info(messageReceived.message().message());
      case WARNING -> NotificationService.warn(messageReceived.message().message());
      case ERROR -> NotificationService.error(messageReceived.message().message());
    }
  }
}
