package org.homepoker.model.command;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.homepoker.lib.util.JsonUtils;
import org.homepoker.test.TestUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandSerializationTest {

  private static final ObjectMapper objectMapper = JsonMapper.builder()
			.addModule(GameCommand.gameCommandsModule())
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .build();

  @Test
  void testCommandSerialization() {
    RegisterForGame register = new RegisterForGame("gameId", TestUtils.testUser());
    String json = JsonUtils.toJson(register, objectMapper);
    GameCommand deserializedRegister = JsonUtils.jsonToObject(json, GameCommand.class, objectMapper);
    assertThat(deserializedRegister).isInstanceOf(RegisterForGame.class);
    assert deserializedRegister != null;
    assertThat(deserializedRegister.commandId()).isEqualTo("register-for-game");
    assertThat(deserializedRegister.gameId()).isEqualTo("gameId");
    assertThat(deserializedRegister.user()).isNull();

  }
}
