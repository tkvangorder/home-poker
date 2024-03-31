package org.homepoker.model.user;

import org.springframework.lang.Nullable;

public record UserCriteria(@Nullable String userLoginId, @Nullable String userEmail) {
}
