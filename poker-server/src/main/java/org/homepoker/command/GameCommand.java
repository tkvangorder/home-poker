package org.homepoker.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.With;
import org.homepoker.user.User;
import org.springframework.util.Assert;

import java.util.Map;

/**
 * The command is meant to be serialized over the wire and therefore, we avoid polymorphic types to make serialization easier.
 * A command will always have an ID and most commands will be associated with a user that has initiated that command.
 * <p>
 * The payload is a generic key/value structure that will be populated with elements that are specific to that command.
 *
 * @author tyler.vangorder
 */
@With
public record GameCommand(CommandId commandId, String gameId, Map<String, String> payload, @JsonIgnore User user) {

  /**
   * Create a {@link CommandId#REGISTER_USER} command.
   * <p>
   * The self-initiated command allows a user to register themselves with the game.
   * <p>
   * Payload:
   * loginId : The login ID of the user registering for the game.
   *
   * @param user The user that is registering the player.
   * @return command
   */
  public static GameCommand asRegisterUser(User user, String gameId) {
    Assert.notNull(user, "The user is required when registering for a game.");
    Assert.hasText(user.getLoginId(), "The user's loginId is required when registering for a game.");
    return new GameCommand(
        CommandId.REGISTER_USER,
        gameId,
        Map.of("loginId", user.getLoginId()),
        user
    );
  }

  /**
   * Create a {@link CommandId#CONFIRM_USER} command.
   * <p>
   * Payload:
   * userId : The persistent ID of the user being confirmed for the game.
   *
   * @param user The user that is being confirmed for the game.
   * @return Confirm player command;
   */
  public static GameCommand confirmUser(User user, String gameId, String loginId) {
    Assert.notNull(user, "The user that is confirming the login ID is required.");
    Assert.hasText(user.getLoginId(), "The user's that is confirming the login ID is required.");
    Assert.hasText(loginId, "The login ID for the user being confirmed is required.");

    return new GameCommand(
        CommandId.CONFIRM_USER,
        gameId,
        Map.of("loginId", loginId),
        user
    );
  }
}
