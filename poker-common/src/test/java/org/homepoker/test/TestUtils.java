package org.homepoker.test;

import org.homepoker.model.user.User;

public class TestUtils {
  private TestUtils() {
  }

  public static User testUser() {
    return User.builder()
        .id("testId")
        .loginId("test")
        .password("test")
        .email("test@test.com")
        .alias("testy")
        .name("Testy McTest Face")
        .phone("555-555-5555")
        .build();
  }
}
