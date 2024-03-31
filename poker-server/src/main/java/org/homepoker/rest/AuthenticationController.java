package org.homepoker.rest;

import org.homepoker.model.user.RegisterUserRequest;
import org.homepoker.model.user.UserLogin;
import org.homepoker.security.AuthenticationResponse;
import org.homepoker.security.AuthenticationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthenticationController {

  private final AuthenticationService authenticationService;

  public AuthenticationController(AuthenticationService authenticationService) {
    this.authenticationService = authenticationService;
  }

  @PostMapping("/login")
  public AuthenticationResponse login(@RequestBody UserLogin userLogin  ) {
    return authenticationService.login(userLogin);
  }

  @PostMapping("/register")
  public AuthenticationResponse registerUser(@RequestBody RegisterUserRequest userRequest) {
    return authenticationService.registerUser(userRequest);
  }
}
