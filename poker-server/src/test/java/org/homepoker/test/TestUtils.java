package org.homepoker.test;

import org.homepoker.security.PokerSecurityProperties;
import org.homepoker.security.SecurityUtilities;

import java.util.Arrays;

public class TestUtils {

  public static SecurityUtilities testSecurityUtilities(String... adminUsers) {
    if (adminUsers.length == 0) {
      adminUsers = new String[]{"admin"};
    }
    return new SecurityUtilities(new PokerSecurityProperties(Arrays.asList(adminUsers), "1234",
        null, null));
  }
}
