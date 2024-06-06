package org.homepoker.security;

import org.homepoker.lib.exception.SecurityException;
import org.homepoker.model.user.RegisterUserRequest;
import org.homepoker.model.user.User;
import org.homepoker.model.user.UserLogin;
import org.homepoker.user.UserManager;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

/**
 * This service handles login and registration of users.
 */
@Service
public class AuthenticationService {

  private final UserManager userManager;
  private final PokerSecurityProperties securityProperties;
  private final AuthenticationManager authenticationManager;
  private final JwtTokenService jwtTokenService;

  public AuthenticationService(UserManager userManager, PokerSecurityProperties securityProperties, AuthenticationManager authenticationManager, JwtTokenService jwtTokenService) {
    this.userManager = userManager;
    this.securityProperties = securityProperties;
    this.authenticationManager = authenticationManager;
    this.jwtTokenService = jwtTokenService;
  }

  public AuthenticationResponse login(UserLogin userLogin) {
    // Login is implemented via Spring Security's authentication manager.
    authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(userLogin.loginId(), userLogin.password()));
    User user = userManager.getUser(userLogin.loginId());
    // If we get here, we have successfully authenticated and generate a new token for the user.
    return new AuthenticationResponse(jwtTokenService.generateToken(user), user);
  }

  /**
   * This method registers a new user if the server passcode matches that of the configured passcode.
   *
   * @param userRequest The user to register
   * @return The authentication response that included a new token to represent the user.
   */
  public AuthenticationResponse registerUser(RegisterUserRequest userRequest) {
    if (!securityProperties.getPasscode().equals(userRequest.serverPasscode())) {
      throw new SecurityException("Access Denied");
    }
    User user = userManager.registerUser(User.builder()
        .loginId(userRequest.loginId())
        .password(userRequest.password())
        .name(userRequest.name())
        .alias(userRequest.alias())
        .phone(userRequest.phone())
        .email(userRequest.email())
        .build()
    );
    return new AuthenticationResponse(jwtTokenService.generateToken(user), user);
  }

}
