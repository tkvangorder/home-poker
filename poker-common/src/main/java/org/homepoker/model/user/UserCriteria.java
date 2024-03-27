package org.homepoker.model.user;

import lombok.Value;

public record UserCriteria(String userLoginId, String userEmail) {
}
