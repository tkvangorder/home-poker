package org.homepoker.model.user;

import lombok.*;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * A user registered with the system.
 */
@Builder
@With
public record User(@Nullable String id, String loginId, @Nullable String password, String email, String alias, String name, String phone, Set<UserRole> roles) {

  public User(@Nullable String id, String loginId, @Nullable String password, String email, @Nullable String alias, String name, String phone, @Nullable Set<UserRole> roles) {
    this.id = id;
    this.loginId = loginId;
    this.password = password;
    this.email = email;
    this.alias = alias == null ? name : alias;
    this.name = name;
    this.phone = phone;
    this.roles = roles == null ? Set.of() : roles;
  }

  public static class UserBuilder {

    public UserBuilder role(UserRole role) {
      if (this.roles != null && this.roles.contains(role)) {
        return this;
      }
      HashSet<UserRole> roles = this.roles == null ? new HashSet<>() : new HashSet<>(this.roles);
      roles.add(role);
      this.roles = Set.copyOf(roles);
      return this;
    }
  }
}
