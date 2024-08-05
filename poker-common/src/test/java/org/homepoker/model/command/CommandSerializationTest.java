package org.homepoker.model.command;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.homepoker.lib.util.JsonUtils;
import org.homepoker.test.TestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandSerializationTest {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeAll
  static void setup() {
    objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    GameCommand.registerCommandsWithJackson(objectMapper);
  }

  @Test
  void testCommandSerialization() {
    RegisterForGame register = new RegisterForGame("gameId", TestUtils.testUser());
    String json = JsonUtils.toJson(register, objectMapper);
    GameCommand deserializedRegister = JsonUtils.jsonToObject(json, GameCommand.class, objectMapper);
    assertThat(deserializedRegister).isInstanceOf(RegisterForGame.class);
    assert deserializedRegister != null;
    assertThat(deserializedRegister.commandId()).isEqualTo(GameCommandType.REGISTER_FOR_GAME);
    assertThat(deserializedRegister.gameId()).isEqualTo("gameId");
    assertThat(deserializedRegister.user()).isNull();

  }
}
