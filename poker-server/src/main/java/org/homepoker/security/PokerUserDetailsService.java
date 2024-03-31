package org.homepoker.security;

import org.homepoker.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * An implementation of Spring Security's UserDetailsService which is used to validate a user during authentication.
 */
public class PokerUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;
  private final SecurityUtilities securityUtilities;


  public PokerUserDetailsService(UserRepository userRepository, SecurityUtilities securityUtilities) {
    this.userRepository = userRepository;
    this.securityUtilities = securityUtilities;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    return securityUtilities.userToUserDetails(userRepository.findByLoginId(username));
  }

}
