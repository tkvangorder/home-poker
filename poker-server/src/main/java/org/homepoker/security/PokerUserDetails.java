package org.homepoker.security;

import org.homepoker.model.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;

/**
 * Extension of the poker user that implements Spring'g UserDetails interface.
 *
 * @author tyler.vangorder
 */
public class PokerUserDetails implements UserDetails {

  private final User user;
  private final Set<GrantedAuthority> authorities;

  PokerUserDetails(User user, Set<GrantedAuthority> authorities) {
    this.user = user;
    this.authorities = authorities;
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
