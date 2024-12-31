package org.homepoker.model.game;

import lombok.Builder;
import lombok.With;
import org.homepoker.model.user.User;

import java.util.Objects;

/**
 * This class represents the state of a player in the game and is always linked with a user.
 *
 * @author tyler.vangorder
 */
@Builder
@With
public final class Player {
  private final User user;
  private final PlayerStatus status;
  private final Integer chipCount;
  private final Integer buyInTotal;
  private final Integer reBuys;
  private final Integer addOns;

  /**
   *
   */
  public Player(User user, PlayerStatus status, Integer chipCount, Integer buyInTotal, Integer reBuys, Integer addOns) {
    this.user = user;
    this.status = status;
    this.chipCount = chipCount;
    this.buyInTotal = buyInTotal;
    this.reBuys = reBuys;
    this.addOns = addOns;
  }

  public String userId() {
    return user.id();
  }

  public String userLogin() {
    return user.loginId();
  }

  public User user() {
    return user;
  }

  public PlayerStatus status() {
    return status;
  }

  public Integer chipCount() {
    return chipCount;
  }

  public Integer buyInTotal() {
    return buyInTotal;
  }

  public Integer reBuys() {
    return reBuys;
  }

  public Integer addOns() {
    return addOns;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (Player) obj;
    return Objects.equals(this.user, that.user) &&
        Objects.equals(this.status, that.status) &&
        Objects.equals(this.chipCount, that.chipCount) &&
        Objects.equals(this.buyInTotal, that.buyInTotal) &&
        Objects.equals(this.reBuys, that.reBuys) &&
        Objects.equals(this.addOns, that.addOns);
  }

  @Override
  public int hashCode() {
    return Objects.hash(user, status, chipCount, buyInTotal, reBuys, addOns);
  }

  @Override
  public String toString() {
    return "Player[" +
        "user=" + user + ", " +
        "status=" + status + ", " +
        "chipCount=" + chipCount + ", " +
        "buyInTotal=" + buyInTotal + ", " +
        "reBuys=" + reBuys + ", " +
        "addOns=" + addOns + ']';
  }

}
