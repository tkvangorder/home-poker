package org.homepoker.model.game;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;
import org.homepoker.model.user.User;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

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

  /**
   * When the last active WebSocket listener for this player went away. {@code null} when
   * the player is connected. Set on the 1→0 ref-count transition in
   * {@code GameManager.handlePlayerDisconnected}; cleared on the 0→1 transition in
   * {@code handlePlayerConnected} and on every tick where the player is removed from the
   * game. Reset to {@code null} for all players when {@code GameManager} is constructed,
   * so a server restart gives everyone a fresh grace window.
   */
  @Nullable
  @JsonIgnore
  private Instant disconnectedAt;

  public String userId() {
    return user.id();
  }

}
