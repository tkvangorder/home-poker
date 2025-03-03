package org.homepoker.test;

import org.homepoker.model.game.*;
import org.homepoker.model.game.cash.CashGame;
import org.homepoker.model.game.cash.CashGameDetails;
import org.homepoker.model.user.User;
import org.homepoker.model.user.UserRole;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TestDataHelper {

  public static User fred() {
    return user("fred", "password", "Fred");
  }

  public static User user(String loginId, String password, String name) {
    return User.builder()
        .loginId(loginId)
        .name(name)
        .alias(name)
        .phone("555-555-5555")
        .password(password)
        .email(loginId + "@example.com")
        .role(UserRole.USER)
        .build();
  }
  public static User adminUser(String loginId, String password, String name) {
    return User.builder()
        .loginId(loginId)
        .name(name)
        .alias(name)
        .phone("555-555-5555")
        .password(password)
        .email(loginId + "@example.com") // default value
        .role(UserRole.ADMIN)
        .build();
  }

  public static User adminUser() {
    return adminUser("testAdmin", "testAdmin", "Test Admin");
  }

  public static List<Player> generatePlayers(CashGame game, int count) {
    List<Player> players = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      players.add(player(game, user("user" + i, "password", "User" + i)));
    }
    return players;
  }

  public static Player player(CashGame game, User user) {
    return player(game, null, null, user);
  }

  public static Player player(CashGame game, @Nullable PlayerStatus status, @Nullable Integer chipCount, User user) {
    if (status == null) {
      status = PlayerStatus.REGISTERED;
    }
    if (chipCount == null) {
      chipCount = 10000;
    }

    return Player.builder()
        .user(user)
        .status(status)
        .chipCount(chipCount)
        .buyInTotal(1000)
        .reBuys(0)
        .addOns(0)
        .build();
  }
  public static CashGameDetails cashGameDetails(String name, @Nullable User owner) {

        if (owner == null) {
            owner = adminUser("fred", "fred", "Fred");
        }

        return CashGameDetails.builder()
            .name(name)
            .type(GameType.TEXAS_HOLDEM)
            .status(GameStatus.SCHEDULED)
            .startTime(Instant.now())
            .maxBuyIn(10000)
            .smallBlind(25)
            .bigBlind(50)
            .owner(owner)
            .build();
  }

  public static CashGame cashGame(String name, @Nullable User owner) {

    if (owner == null) {
      owner = adminUser("fred", "fred", "Fred");
    }

    return CashGame.builder()
        .name(name)
        .type(GameType.TEXAS_HOLDEM)
        .status(GameStatus.SCHEDULED)
        .startTime(Instant.now())
        .maxBuyIn(10000)
        .smallBlind(25)
        .bigBlind(50)
        .owner(owner)
        .build();
  }
}
