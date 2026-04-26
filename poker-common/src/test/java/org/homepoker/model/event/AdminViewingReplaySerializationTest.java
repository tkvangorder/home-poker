package org.homepoker.model.event;

import org.homepoker.model.event.game.AdminViewingReplay;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AdminViewingReplaySerializationTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().build();

  @Test
  void roundTripsWithSequenceNumber() {
    AdminViewingReplay original = new AdminViewingReplay(
        Instant.parse("2026-04-26T12:00:00Z"),
        99L,
        "g1",
        "admin-user",
        "admin",
        "table-A",
        7
    );
    String json = objectMapper.writeValueAsString(original);
    AdminViewingReplay parsed = objectMapper.readValue(json, AdminViewingReplay.class);
    assertThat(parsed).isEqualTo(original);
    assertThat(parsed.sequenceNumber()).isEqualTo(99L);
  }
}
