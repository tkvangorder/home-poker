package org.homepoker.security;

import lombok.Value;
import org.homepoker.user.User;
import org.homepoker.user.UserManager;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@Value
public class PokerSecurityController {

  UserManager userManager;

  @GetMapping("/user/csrf")
  public CsrfToken csrf(CsrfToken token) {
    return token;
  }

  @PostMapping("/user/register")
  public User registerUser(@RequestBody RegisterUserRequest request) {
    return userManager.registerUser(request.getUser());
  }

  @Value
  private static class RegisterUserRequest {
    String serverPasscode;
    User user;
  }
}