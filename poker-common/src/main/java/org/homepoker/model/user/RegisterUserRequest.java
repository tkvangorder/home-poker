package org.homepoker.model.user;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record RegisterUserRequest(String serverPasscode, String loginId, String password, String email, String alias, String name, String phone) {
}
