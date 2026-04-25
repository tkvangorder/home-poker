package org.homepoker.model.game;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlindTypeTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().build();

  @Test
  void smallSerializesAsName() throws Exception {
    String json = objectMapper.writeValueAsString(BlindType.SMALL);
    assertThat(json).isEqualTo("\"SMALL\"");
  }

  @Test
  void bigSerializesAsName() throws Exception {
    String json = objectMapper.writeValueAsString(BlindType.BIG);
    assertThat(json).isEqualTo("\"BIG\"");
  }

  @Test
  void smallDeserializesFromName() throws Exception {
    BlindType actual = objectMapper.readValue("\"SMALL\"", BlindType.class);
    assertThat(actual).isEqualTo(BlindType.SMALL);
  }

  @Test
  void bigDeserializesFromName() throws Exception {
    BlindType actual = objectMapper.readValue("\"BIG\"", BlindType.class);
    assertThat(actual).isEqualTo(BlindType.BIG);
  }
}
