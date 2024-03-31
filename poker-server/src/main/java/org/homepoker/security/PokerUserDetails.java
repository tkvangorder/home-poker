package org.homepoker.security;

import org.homepoker.model.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extension of the poker user that implements Spring'g UserDetails interface.
 *
 * @author tyler.vangorder
 */
public class PokerUserDetails implements UserDetails {

  private final User user;
  private final Set<GrantedAuthority> authorities;

  PokerUserDetails(User user) {
    this.user = user;
    this.authorities = user.roles().stream()
        .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
        .collect(Collectors.toSet());
  }

  public User toUser() {
    return user;
  }
  
  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public String getUsername() {
    return user.loginId();
  }

  @Override
  public String getPassword() {
    return user.password();
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

}
