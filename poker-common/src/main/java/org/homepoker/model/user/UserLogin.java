package org.homepoker.model.user;

import lombok.Value;

@Value
public class UserLogin {
  String loginId;
  String password;
}
