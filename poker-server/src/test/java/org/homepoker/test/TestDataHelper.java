package org.homepoker.test;

import org.homepoker.model.game.cash.CashGameDetails;
import org.homepoker.model.game.GameStatus;
import org.homepoker.model.game.GameType;
import org.homepoker.model.user.User;
import org.homepoker.model.user.UserRole;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

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


}
