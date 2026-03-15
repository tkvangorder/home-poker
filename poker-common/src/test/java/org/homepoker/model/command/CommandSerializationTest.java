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
    JoinGame joinGame = new JoinGame("gameId", TestUtils.testUser());
    String json = JsonUtils.toJson(joinGame, objectMapper);
    GameCommand deserialized = JsonUtils.jsonToObject(json, GameCommand.class, objectMapper);
    assertThat(deserialized).isInstanceOf(JoinGame.class);
    assert deserialized != null;
    assertThat(deserialized.commandId()).isEqualTo("join-game");
    assertThat(deserialized.gameId()).isEqualTo("gameId");
    assertThat(deserialized.user()).isNull();
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
