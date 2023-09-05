package org.homepoker.security;

import lombok.Value;
import org.homepoker.event.MessageReceived;
import org.homepoker.lib.exception.ValidationException;
import org.homepoker.model.Message;
import org.homepoker.model.user.User;
import org.homepoker.user.UserManager;
import org.springframework.http.HttpStatus;
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

  @ExceptionHandler(ValidationException.class)
  @ResponseBody
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Message handleException(ValidationException e) {
    return Message.error(e.getMessage());
  }

  @ExceptionHandler(Exception.class)
  @ResponseBody
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public Message handleException(Exception e) {
    return Message.error(e.getMessage());
  }

}