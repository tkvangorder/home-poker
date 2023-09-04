package org.homepoker.websocket;

import org.homepoker.event.ApplicationError;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;

import static org.homepoker.PokerMessageRoutes.USER_QUEUE_DESTINATION;

@ControllerAdvice
public class WebSocketExceptionHandler {

  @MessageExceptionHandler(AccessDeniedException.class)
  @SendToUser(USER_QUEUE_DESTINATION)
  public ApplicationError handleException(Exception e) {
    return new ApplicationError("Access Denied", e.getMessage());
  }
}
