package org.homepoker.model.game;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;
import org.homepoker.model.user.User;
import org.jspecify.annotations.Nullable;

/**
 * This class represents the state of a player in the game and is always linked with a user.
 *
 * @author tyler.vangorder
 */
@Data
@Builder
@Accessors(fluent = true)
public final class Player {
  private final User user;
  private PlayerStatus status;
  private Integer chipCount;
  private Integer buyInTotal;
  private Integer reBuys;
  private Integer addOns;

  @Nullable
  private String tableId;

  public String userId() {
    return user.id();
  }

  public String userLogin() {
    return user.loginId();
  }

}
