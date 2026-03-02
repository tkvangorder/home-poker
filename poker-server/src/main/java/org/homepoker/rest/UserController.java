package org.homepoker.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.homepoker.model.user.*;
import org.homepoker.security.PokerUserDetails;
import org.homepoker.security.SecurityUtilities;
import org.homepoker.user.UserManager;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "User management operations")
public class UserController {

  private final UserManager userManager;

  public UserController(UserManager userManager) {
    this.userManager = userManager;
  }

  @PostMapping("")
  @Operation(summary = "Find users", description = "Search for users by login ID or email.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Users matching the criteria"),
      @ApiResponse(responseCode = "401", description = "Not authenticated")
  })
  List<User> findUsers(UserCriteria criteria) {
    return userManager.findUsers(criteria);
  }

  @PostMapping("/{userId}/update")
  @Operation(summary = "Update user information", description = "Update a user's profile information (email, alias, name, phone). Login ID cannot be changed.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "User updated successfully"),
      @ApiResponse(responseCode = "400", description = "Invalid update data"),
      @ApiResponse(responseCode = "401", description = "Not authenticated"),
      @ApiResponse(responseCode = "404", description = "User not found")
  })
  User updateUserInformation(
      @Parameter(description = "ID of the user to update") @RequestParam String userId,
      @RequestBody UserInformationUpdate update,
      @AuthenticationPrincipal PokerUserDetails user) {

    if (!SecurityUtilities.userIsAdmin(user) && !user.getUsername().equals(update.loginId())) {
      throw new SecurityException("Access Denied");
    }
    return userManager.updateUserInformation(update.withLoginId(userId));
  }

  @PostMapping("/{userId}/password")
  @Operation(summary = "Change user password", description = "Change a user's password. Requires the existing password or a time-sensitive email code. Admins can change any user's password.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Password changed successfully"),
      @ApiResponse(responseCode = "400", description = "Invalid password change request"),
      @ApiResponse(responseCode = "401", description = "Not authenticated"),
      @ApiResponse(responseCode = "403", description = "Not authorized to change this user's password")
  })
  void updateUserPassword(
      @AuthenticationPrincipal PokerUserDetails user,
      UserPasswordChangeRequest passwordRequest) {
    if (!SecurityUtilities.userIsAdmin(user) && !user.getUsername().equals(passwordRequest.loginId())) {
      throw new SecurityException("Access Denied");
    }
    userManager.updateUserPassword(passwordRequest);
  }

}
