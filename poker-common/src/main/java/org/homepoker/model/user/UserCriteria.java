package org.homepoker.model.user;

import org.jspecify.annotations.Nullable;

public record UserCriteria(@Nullable String userId, @Nullable String userEmail) {
}
