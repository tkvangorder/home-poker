package org.homepoker.model.game;

import com.fasterxml.jackson.annotation.JsonProperty;
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
  @JsonProperty
  private final User user;
  @JsonProperty
  private PlayerStatus status;
  @JsonProperty
  private int chipCount;
  @JsonProperty
  private int buyInTotal;
  @JsonProperty
  private int reBuys;
  @JsonProperty
  private int addOns;

  @Nullable
  @JsonProperty
  private String tableId;

  public String userId() {
    return user.id();
  }

}
