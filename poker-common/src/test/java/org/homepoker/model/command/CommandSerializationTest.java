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
    RegisterUser registerUser = new RegisterUser("gameId", TestUtils.testUser());
    String json = JsonUtils.toJson(registerUser, objectMapper);
    GameCommand registeredUser = JsonUtils.jsonToObject(json, GameCommand.class, objectMapper);
    assertThat(registeredUser).isInstanceOf(RegisterUser.class);
    assert registeredUser != null;
    assertThat(registeredUser.commandId()).isEqualTo(CommandId.REGISTER_USER);
    assertThat(registeredUser.gameId()).isEqualTo("gameId");
    assertThat(registeredUser.user()).isNull();

  }
}
