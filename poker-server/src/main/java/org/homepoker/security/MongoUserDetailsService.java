package org.homepoker.security;

import org.homepoker.model.user.User;
import org.homepoker.user.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import java.util.Set;

/**
 * An implementation of Spring Security's UserDetailsService which is used to validate a user during authentication.
 *
 * @author tyler.vangorder
 */
public class MongoUserDetailsService implements UserDetailsService {

  public static final User anonymousUser = User.builder()
      .loginId("anonymous")
      .password(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("anonymous"))
      .name("guest")
      .build();

  private final UserRepository userRepository;
  private final SecurityUtilities securityUtilities;


  public MongoUserDetailsService(UserRepository userRepository, SecurityUtilities securityUtilities) {
    this.userRepository = userRepository;
    this.securityUtilities = securityUtilities;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    return "anonymous".equals(username) ?
        securityUtilities.userToUserDetails(anonymousUser) :
        securityUtilities.userToUserDetails(userRepository.findByLoginId(username));
  }

}
