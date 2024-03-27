package org.homepoker.model.user;

import lombok.Value;

public record UserLogin(String loginId, String password) {
}
