package org.homepoker.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Authentication", description = "User login and registration (no authentication required)")
@SecurityRequirement(name = "")
public class AuthenticationController {

  private final AuthenticationService authenticationService;

  public AuthenticationController(AuthenticationService authenticationService) {
    this.authenticationService = authenticationService;
  }

  @PostMapping("/login")
  @Operation(summary = "Login", description = "Authenticate with login ID and password. Returns a JWT token and user details.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Login successful"),
      @ApiResponse(responseCode = "401", description = "Invalid credentials")
  })
  public AuthenticationResponse login(@RequestBody UserLogin userLogin) {
    return authenticationService.login(userLogin);
  }

  @PostMapping("/register")
  @Operation(summary = "Register a new user", description = "Create a new user account. A valid server passcode is required. Returns a JWT token and user details.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Registration successful"),
      @ApiResponse(responseCode = "400", description = "Invalid registration data")
  })
  public AuthenticationResponse registerUser(@RequestBody RegisterUserRequest userRequest) {
    return authenticationService.registerUser(userRequest);
  }
}
