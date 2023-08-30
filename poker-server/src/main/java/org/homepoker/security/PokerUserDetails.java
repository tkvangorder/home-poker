package org.homepoker.security;

import org.homepoker.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;

/**
 * Extension of the poker user that implements Spring'g UserDetails interface.
 *
 * @author tyler.vangorder
 */
public class PokerUserDetails extends User implements UserDetails {
  private final Set<GrantedAuthority> authorities;

  PokerUserDetails(User user, Set<GrantedAuthority> authorities) {
    super(
        user.getId(),
        user.getLoginId(),
        user.getPassword(),
        user.getEmail(),
        user.getAlias(),
        user.getName(),
        user.getPhone());
    this.authorities = authorities;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public String getUsername() {
    return getLoginId();
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

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((authorities == null) ? 0 : authorities.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (!super.equals(obj))
      return false;
    if (getClass() != obj.getClass())
      return false;
    PokerUserDetails other = (PokerUserDetails) obj;
    if (authorities == null) {
      return other.authorities == null;
    } else {
      return authorities.equals(other.authorities);
    }
  }
}
