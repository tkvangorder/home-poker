package org.homepoker.model.user;

import lombok.*;
import org.springframework.lang.Nullable;

import java.util.Set;

/**
 * A user registered with the system.
 */
@Builder
@With
public record User(String id, String loginId, String password, String email, String alias, String name, String phone, Set<UserRole> roles) {

  public User(String id, String loginId, String password, String email, String alias, String name, String phone, @Nullable Set<UserRole> roles) {
    this.id = id;
    this.loginId = loginId;
    this.password = password;
    this.email = email;
    this.alias = alias;
    this.name = name;
    this.phone = phone;
    this.roles = roles == null ? Set.of() : roles;
  }
}
