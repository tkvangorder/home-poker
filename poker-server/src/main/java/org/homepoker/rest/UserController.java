package org.homepoker.rest;

import org.homepoker.event.user.UserPasswordChanged;
import org.homepoker.model.user.*;
import org.homepoker.security.PokerSecurityProperties;
import org.homepoker.security.PokerUserDetails;
import org.homepoker.security.SecurityUtilities;
import org.homepoker.user.UserManager;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

  private final UserManager userManager;
  private final PokerSecurityProperties securityProperties;

  public UserController(UserManager userManager, PokerSecurityProperties securityProperties) {
    this.userManager = userManager;
    this.securityProperties = securityProperties;
  }

  @PostMapping("/register")
  User registerUser(RegisterUserRequest userRequest) {
    if (!securityProperties.getPasscode().equals(userRequest.serverPasscode())) {
      throw new SecurityException("Access Denied");
    }
    return userManager.registerUser(userRequest.user());
  }

  @PostMapping("/search")
  List<User> findUsers(UserCriteria criteria) {
    return userManager.findUsers(criteria);
  }

  @PostMapping("/{userId}/update")
  User updateUserInformation(@RequestParam String userId, @RequestBody UserInformationUpdate update, @AuthenticationPrincipal PokerUserDetails user) {
    return userManager.updateUserInformation(update);
  }

  @PostMapping("/{userId}/password")
  UserPasswordChanged updateUserPassword(@AuthenticationPrincipal PokerUserDetails user, UserPasswordChangeRequest passwordRequest) {
    if (!SecurityUtilities.userIsAdmin(user) && !user.getUsername().equals(passwordRequest.loginId())) {
      throw new SecurityException("Access Denied");
    }
    userManager.updateUserPassword(passwordRequest);
    return new UserPasswordChanged();
  }

}
