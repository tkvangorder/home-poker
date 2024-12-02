package org.homepoker.model.game;

import lombok.Builder;
import lombok.With;
import org.homepoker.model.user.User;

/**
 * This class represents the state of a player in the game and is always linked with a user.
 *
 * @author tyler.vangorder
 */
@Builder
@With
public record Player(User user, PlayerStatus status, Integer chipCount, Integer buyInTotal, Integer reBuys, Integer addOns) {

  public String userLogin() {
    return user.loginId();
  }
}
