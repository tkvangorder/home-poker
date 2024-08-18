package org.homepoker.test;

import org.homepoker.model.user.User;
import org.homepoker.model.user.UserRole;

public class TestDataHelper {

  public static User createUser(String loginId, String password, String name) {
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
  public static User createAdminUser(String loginId, String password, String name) {
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


}
