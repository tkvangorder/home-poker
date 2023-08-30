package org.homepoker.domain.user;

import lombok.Value;

@Value
public class UserLogin {
	String loginId;
	String password;
}
