package org.homepoker.websocket;

import org.homepoker.event.user.CurrentUserRetrieved;
import org.homepoker.model.user.User;
import org.homepoker.model.user.UserCriteria;
import org.homepoker.model.user.UserInformationUpdate;
import org.homepoker.model.user.UserPasswordChangeRequest;
import org.homepoker.user.UserManager;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import static org.homepoker.PokerMessageRoutes.*;
import java.util.List;

@Controller
public class UserManagerController {

  private final UserManager userManager;

  public UserManagerController(UserManager userManager) {
    this.userManager = userManager;
  }

  @MessageMapping("/user/register")
  User registerUser(User user) {
    return userManager.registerUser(user);
  }

  @MessageMapping(ROUTE_USER_MANAGER_GET_CURRENT_USER)
  @SendToUser("/queue/events")
  CurrentUserRetrieved getCurrentUser(@AuthenticationPrincipal User user) {
    return new CurrentUserRetrieved(userManager.getUser(user.getLoginId()));
  }

  @MessageMapping("/user/find")
  List<User> findUsers(UserCriteria criteria) {
    return userManager.findUsers(criteria);
  }

  @MessageMapping("/user/update")
  User updateUser(UserInformationUpdate update) {
    return userManager.updateUserInformation(update);
  }

  @MessageMapping("/user/update-password")
  void updateUserPassword(UserPasswordChangeRequest passwordRequest) {
    userManager.updateUserPassword(passwordRequest);
  }

  @MessageMapping("/user/delete")
  void deleteUser(String loginId) {
    userManager.deleteUser(loginId);
  }
}
