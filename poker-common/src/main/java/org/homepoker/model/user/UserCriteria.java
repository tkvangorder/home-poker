package org.homepoker.model.user;

import lombok.Value;

@Value
public class UserCriteria {
  String userLoginId;
  String userEmail;
}
