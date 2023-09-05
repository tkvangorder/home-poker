package org.homepoker.websocket;

import org.homepoker.event.MessageReceived;
import org.homepoker.lib.exception.ValidationException;
import org.homepoker.model.Message;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;

import static org.homepoker.PokerMessageRoutes.USER_QUEUE_DESTINATION;

@ControllerAdvice
public class WebSocketExceptionHandler {

  @MessageExceptionHandler(Exception.class)
  @SendToUser(USER_QUEUE_DESTINATION)
  public MessageReceived handleException(Exception e) {
    return new MessageReceived(Message.error(e.getMessage()));
  }

}
