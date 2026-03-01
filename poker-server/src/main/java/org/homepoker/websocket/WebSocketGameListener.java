package org.homepoker.websocket;

import lombok.extern.slf4j.Slf4j;
import org.homepoker.game.UserGameListener;
import org.homepoker.model.event.*;
import org.homepoker.model.user.User;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * A {@link org.homepoker.game.GameListener} that forwards game events to a WebSocket client.
 * <p>
 * Event filtering ensures that:
 * <ul>
 *   <li>{@link UserEvent} — only sent to the owning player (important for {@code HoleCardsDealt} privacy)</li>
 *   <li>{@link GameEvent} — broadcast to all connected clients</li>
 *   <li>{@link SystemError} — only sent to the user that triggered the error</li>
 * </ul>
 * <p>
 * The {@code UserEvent} check comes first so that events implementing both {@code TableEvent} and
 * {@code UserEvent} (like {@code HoleCardsDealt}) are filtered per-user rather than broadcast.
 */
@Slf4j
public class WebSocketGameListener extends UserGameListener {

  private final WebSocketSession session;
  private final ObjectMapper objectMapper;

  public WebSocketGameListener(User user, WebSocketSession session, ObjectMapper objectMapper) {
    super(user);
    this.session = session;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean acceptsEvent(PokerEvent event) {
		return switch (event) {
      // UserEvent check first — this ensures HoleCardsDealt (which is both TableEvent and UserEvent)
      // is only sent to the owning player.
			case UserEvent userEvent -> userEvent.userId().equals(getUser().id());

			// GameEvent (including TableEvent) is broadcast to all.
			case GameEvent _ -> true;

			// SystemError with a userId is only sent to that user.
			case SystemError systemError -> getUser().id().equals(systemError.userId());
			default -> false;
		};
	}

  @Override
  public void onEvent(PokerEvent event) {
    try {
      String json = objectMapper.writeValueAsString(event);
      session.sendMessage(new TextMessage(json));
    } catch (IOException e) {
      log.error("Failed to send event to WebSocket session [{}], closing session.", session.getId(), e);
      try {
        session.close();
      } catch (IOException ex) {
        log.debug("Error closing WebSocket session [{}]", session.getId(), ex);
      }
    }
  }
}
