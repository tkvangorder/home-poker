package org.homepoker.websocket;

import org.homepoker.model.MessageSeverity;
import org.homepoker.model.event.SystemError;
import org.homepoker.model.event.game.GameMessage;
import org.homepoker.model.event.table.HandStarted;
import org.homepoker.model.event.table.HoleCardsDealt;
import org.homepoker.model.event.user.UserMessage;
import org.homepoker.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WebSocketGameListenerTest {

  private static final User ALICE = User.builder()
      .id("alice-id")
      .loginId("alice")
      .password("pass")
      .email("alice@test.com")
      .name("Alice")
      .phone("555-0001")
      .build();

  private static final String OTHER_USER_ID = "bob-id";

  private WebSocketGameListener listener;

  @BeforeEach
  void setUp() {
    WebSocketSession session = mock(WebSocketSession.class);
    ObjectMapper mapper = JsonMapper.builder().build();
    listener = new WebSocketGameListener(ALICE, session, mapper);
  }

  @Test
  void acceptsEvent_gameMessage_broadcast() {
    GameMessage event = new GameMessage(Instant.now(), "game1", "Hello");
    assertThat(listener.acceptsEvent(event)).isTrue();
  }

  @Test
  void acceptsEvent_handStarted_broadcast() {
    HandStarted event = new HandStarted(Instant.now(), "game1", "table1", 1, 0, 1, 2, 5, 10);
    assertThat(listener.acceptsEvent(event)).isTrue();
  }

  @Test
  void acceptsEvent_userMessage_matchingUser() {
    UserMessage event = UserMessage.builder()
        .timestamp(Instant.now())
        .userId("alice-id")
        .severity(MessageSeverity.INFO)
        .message("Hi Alice")
        .build();
    assertThat(listener.acceptsEvent(event)).isTrue();
  }

  @Test
  void acceptsEvent_userMessage_otherUser() {
    UserMessage event = UserMessage.builder()
        .timestamp(Instant.now())
        .userId(OTHER_USER_ID)
        .severity(MessageSeverity.INFO)
        .message("Hi Bob")
        .build();
    assertThat(listener.acceptsEvent(event)).isFalse();
  }

  @Test
  void acceptsEvent_holeCardsDealt_matchingUser() {
    // HoleCardsDealt implements both TableEvent and UserEvent.
    // The UserEvent check should come first, so it's user-filtered (not broadcast).
    HoleCardsDealt event = new HoleCardsDealt(Instant.now(), "game1", "table1", "alice-id", 0, List.of());
    assertThat(listener.acceptsEvent(event)).isTrue();
  }

  @Test
  void acceptsEvent_holeCardsDealt_otherUser() {
    HoleCardsDealt event = new HoleCardsDealt(Instant.now(), "game1", "table1", OTHER_USER_ID, 1, List.of());
    assertThat(listener.acceptsEvent(event)).isFalse();
  }

  @Test
  void acceptsEvent_systemError_matchingUser() {
    SystemError event = SystemError.builder()
        .timestamp(Instant.now())
        .gameId("game1")
        .userId("alice-id")
        .exception(new RuntimeException("test"))
        .build();
    assertThat(listener.acceptsEvent(event)).isTrue();
  }

  @Test
  void acceptsEvent_systemError_otherUser() {
    SystemError event = SystemError.builder()
        .timestamp(Instant.now())
        .gameId("game1")
        .userId(OTHER_USER_ID)
        .exception(new RuntimeException("test"))
        .build();
    assertThat(listener.acceptsEvent(event)).isFalse();
  }

  @Test
  void acceptsEvent_systemError_nullUser() {
    SystemError event = SystemError.builder()
        .timestamp(Instant.now())
        .gameId("game1")
        .exception(new RuntimeException("test"))
        .build();
    assertThat(listener.acceptsEvent(event)).isFalse();
  }
}
