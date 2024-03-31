package org.homepoker.model.user;

import lombok.*;

import java.util.Set;

/**
 * A user registered with the system.
 */
@Builder
@With
public record User(String id, String loginId, String password, String email, String alias, String name, String phone, Set<UserRole> roles) {
}
