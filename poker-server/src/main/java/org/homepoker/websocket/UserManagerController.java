package org.homepoker.websocket;

import org.homepoker.event.user.*;
import org.homepoker.model.user.User;
import org.homepoker.model.user.UserCriteria;
import org.homepoker.model.user.UserInformationUpdate;
import org.homepoker.model.user.UserPasswordChangeRequest;
import org.homepoker.user.UserManager;
import org.springframework.context.annotation.Role;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import static org.homepoker.PokerMessageRoutes.*;

@Controller
public class UserManagerController {

  private final UserManager userManager;

  public UserManagerController(UserManager userManager) {
    this.userManager = userManager;
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
  CurrentUserUpdated updateUser(UserInformationUpdate update) {
    return new CurrentUserUpdated(userManager.updateUserInformation(update));
  }

  @MessageMapping(ROUTE_USER_MANAGER_UPDATE_PASSWORD)
  @SendToUser(USER_QUEUE_DESTINATION)
  UserPasswordChanged updateUserPassword(UserPasswordChangeRequest passwordRequest) {
    userManager.updateUserPassword(passwordRequest);
    return new UserPasswordChanged();
  }

  @MessageMapping(ROUTE_USER_MANAGER_REGISTER_USER)
  @SendToUser(USER_QUEUE_DESTINATION)
  @PreAuthorize("hasRole('ANONYMOUS')")
  UserRegistered registerUser(User user) {
    return new UserRegistered(userManager.registerUser(user));
  }

}
