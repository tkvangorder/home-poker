package org.homepoker.websocket;

import org.homepoker.event.user.*;
import org.homepoker.model.user.User;
import org.homepoker.model.user.UserCriteria;
import org.homepoker.model.user.UserInformationUpdate;
import org.homepoker.model.user.UserPasswordChangeRequest;
import org.homepoker.security.PokerUserDetails;
import org.homepoker.security.SecurityUtilities;
import org.homepoker.user.UserManager;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import static org.homepoker.PokerMessageRoutes.*;

@Controller
public class UserManagerController {

  private final UserManager userManager;

  public UserManagerController(UserManager userManager) {
    this.userManager = userManager;
  }

  @MessageMapping(ROUTE_USER_MANAGER_REGISTER_USER)
  @SendToUser(USER_QUEUE_DESTINATION)
  UserRegistered registerUser(User user) {
    return new UserRegistered(userManager.registerUser(user));
  }

  @MessageMapping(ROUTE_USER_MANAGER_GET_CURRENT_USER)
  @SendToUser(USER_QUEUE_DESTINATION)
  CurrentUserRetrieved getCurrentUser(@AuthenticationPrincipal User user) {
    return new CurrentUserRetrieved(userManager.getUser(user.getLoginId()));
  }

  @MessageMapping(ROUTE_USER_MANAGER_USER_SEARCH)
  @SendToUser(USER_QUEUE_DESTINATION)
  UserSearchCompleted userSearch(UserCriteria criteria) {
    return new UserSearchCompleted(userManager.findUsers(criteria));
  }

  @MessageMapping(ROUTE_USER_MANAGER_UPDATE_USER)
  @SendToUser(USER_QUEUE_DESTINATION)
  CurrentUserUpdated updateUser(@AuthenticationPrincipal PokerUserDetails user, UserInformationUpdate update) {
    //Only admin users or the user themselves can update their information.
    if (!SecurityUtilities.userIsAdmin(user) &&
        (update == null || update.getLoginId() == null || !update.getLoginId().equals(user.getLoginId()))) {
      throw new SecurityException("Access Denied");
    }
    return new CurrentUserUpdated(userManager.updateUserInformation(update));
  }

  @MessageMapping(ROUTE_USER_MANAGER_UPDATE_PASSWORD)
  @SendToUser(USER_QUEUE_DESTINATION)
  UserPasswordChanged updateUserPassword(@AuthenticationPrincipal PokerUserDetails user, UserPasswordChangeRequest passwordRequest) {
    if (!SecurityUtilities.userIsAdmin(user) &&
        (passwordRequest == null || passwordRequest.getLoginId() == null || !passwordRequest.getLoginId().equals(user.getLoginId()))) {
      throw new SecurityException("Access Denied");
    }
    userManager.updateUserPassword(passwordRequest);
    return new UserPasswordChanged();
  }

}
