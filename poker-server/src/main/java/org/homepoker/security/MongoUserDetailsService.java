package org.homepoker.security;

import org.homepoker.user.User;
import org.homepoker.user.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Set;

/**
 * An implementation of Spring Security's UserDetailsService which is used to validate a user during authentication.
 *
 * @author tyler.vangorder
 */
public class MongoUserDetailsService implements UserDetailsService {

  private static final Set<GrantedAuthority> adminAuthorities = Set.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"));
  private static final Set<GrantedAuthority> userAuthorities = Set.of(new SimpleGrantedAuthority("ROLE_USER"));

  private final UserRepository userRepository;
  private final PokerSecurityProperties securitySettings;


  public MongoUserDetailsService(UserRepository userRepository, PokerSecurityProperties securitySettings) {
    this.userRepository = userRepository;
    this.securitySettings = securitySettings;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    return userToUserDetails(userRepository.findByLoginId(username));
  }


  private PokerUserDetails userToUserDetails(User user) {

    Set<GrantedAuthority> roles = userAuthorities;
    if (securitySettings.getAdminUsers().contains(user.getLoginId())) {
      roles = adminAuthorities;
    }
    return new PokerUserDetails(user, roles);
  }

}
