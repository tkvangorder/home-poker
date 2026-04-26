package org.homepoker.model.event;

import org.homepoker.model.event.game.PlayerDisconnected;
import org.homepoker.model.event.game.PlayerReconnected;
import org.homepoker.model.event.table.BlindPosted;
import org.homepoker.model.game.BlindType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip serialization tests for the new event types added in the
 * 2026-04-17 event-sequencing spec. Verifies that {@code sequenceNumber}
 * is preserved through JSON ser/de — clients depend on this for gap
 * detection.
 */
class PokerEventSerializationTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().build();

  @Test
  void blindPostedRoundTripsWithSequenceNumber() {
    BlindPosted original = new BlindPosted(
        Instant.parse("2026-04-25T12:00:00Z"),
        42L,
        "g1",
        "t1",
        3,
        "alice",
        BlindType.SMALL,
        50L
    );
    String json = objectMapper.writeValueAsString(original);
    BlindPosted parsed = objectMapper.readValue(json, BlindPosted.class);
    assertThat(parsed).isEqualTo(original);
    assertThat(parsed.sequenceNumber()).isEqualTo(42L);
  }

  @Test
  void playerDisconnectedRoundTripsWithSequenceNumber() {
    PlayerDisconnected original = new PlayerDisconnected(
        Instant.parse("2026-04-25T12:00:00Z"),
        7L,
        "g1",
        "alice"
    );
    String json = objectMapper.writeValueAsString(original);
    PlayerDisconnected parsed = objectMapper.readValue(json, PlayerDisconnected.class);
    assertThat(parsed).isEqualTo(original);
    assertThat(parsed.sequenceNumber()).isEqualTo(7L);
  }

  @Test
  void playerReconnectedRoundTripsWithSequenceNumber() {
    PlayerReconnected original = new PlayerReconnected(
        Instant.parse("2026-04-25T12:00:00Z"),
        8L,
        "g1",
        "alice"
    );
    String json = objectMapper.writeValueAsString(original);
    PlayerReconnected parsed = objectMapper.readValue(json, PlayerReconnected.class);
    assertThat(parsed).isEqualTo(original);
    assertThat(parsed.sequenceNumber()).isEqualTo(8L);
  }
}
