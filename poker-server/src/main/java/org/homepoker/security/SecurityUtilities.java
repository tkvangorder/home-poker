package org.homepoker.security;

import org.homepoker.model.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class SecurityUtilities {

  private final PokerSecurityProperties securitySettings;

  public SecurityUtilities(PokerSecurityProperties securitySettings) {
    this.securitySettings = securitySettings;
  }

  private static final Set<GrantedAuthority> adminAuthorities = Set.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"));
  private static final Set<GrantedAuthority> userAuthorities = Set.of(new SimpleGrantedAuthority("ROLE_USER"));
  private static final Set<GrantedAuthority> anonymousAuthorities = Set.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"));

  public static boolean userIsAdmin(UserDetails user) {
    return user != null && user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
  }

  public PokerUserDetails userToUserDetails(User user) {

    if (user == null) {
      throw new UsernameNotFoundException("User not found");
    }

    Set<GrantedAuthority> roles = userAuthorities;
    if ("anonymous".equals(user.getLoginId())) {
      roles = anonymousAuthorities;
    } else if (securitySettings.getAdminUsers().contains(user.getLoginId())) {
      roles = adminAuthorities;
    }
    return new PokerUserDetails(user, roles);
  }
}
