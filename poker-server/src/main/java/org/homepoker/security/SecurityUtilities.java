package org.homepoker.security;

import org.homepoker.model.user.User;
import org.homepoker.model.user.UserRole;
import org.springframework.lang.Nullable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class SecurityUtilities {

  private final PokerSecurityProperties securitySettings;

  private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

  public SecurityUtilities(PokerSecurityProperties securitySettings) {
    this.securitySettings = securitySettings;
  }

  public static boolean userIsAdmin(@Nullable UserDetails user) {
    return user != null && user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
  }
  public static boolean userIsAdmin(@Nullable User user) {
    return user != null && user.roles().contains(UserRole.ADMIN);
  }

  /**
   * Converts a User object to a UserDetails object.
   *
   * @param user The user to convert
   * @return The UserDetails object
   */
  public PokerUserDetails userToUserDetails(@Nullable User user) {

    if (user == null) {
      throw new UsernameNotFoundException("User not found");
    }

    return new PokerUserDetails(user);
  }

  public User assignRolesToUser(User user) {
    if (securitySettings.getAdminUsers().contains(user.loginId())) {
      return user.withRoles(Set.of(UserRole.ADMIN, UserRole.USER));
    } else {
      return  user.withRoles(Set.of(UserRole.USER));
    }
  }

  public String encodePassword(String password) {
    return passwordEncoder.encode(password);
  }

  public PasswordEncoder getPasswordEncoder() {
    return passwordEncoder;
  }

}
