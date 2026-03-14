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

  @Test
  void testGetGameStateSerialization() {
    GetGameState command = new GetGameState("gameId", TestUtils.testUser());
    String json = JsonUtils.toJson(command, objectMapper);
    GameCommand deserialized = JsonUtils.jsonToObject(json, GameCommand.class, objectMapper);
    assertThat(deserialized).isInstanceOf(GetGameState.class);
    assert deserialized != null;
    assertThat(deserialized.commandId()).isEqualTo("get-game-state");
    assertThat(deserialized.gameId()).isEqualTo("gameId");
    assertThat(deserialized.user()).isNull();
  }

  @Test
  void testGetTableStateSerialization() {
    GetTableState command = new GetTableState("gameId", "tableId", TestUtils.testUser());
    String json = JsonUtils.toJson(command, objectMapper);
    GameCommand deserialized = JsonUtils.jsonToObject(json, GameCommand.class, objectMapper);
    assertThat(deserialized).isInstanceOf(GetTableState.class);
    assert deserialized != null;
    assertThat(deserialized.commandId()).isEqualTo("get-table-state");
    assertThat(deserialized.gameId()).isEqualTo("gameId");
    assertThat(((GetTableState) deserialized).tableId()).isEqualTo("tableId");
    assertThat(deserialized.user()).isNull();
  }
}
