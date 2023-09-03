package org.homepoker.client.user;

import lombok.extern.slf4j.Slf4j;
import org.homepoker.client.ClientConnectionManager;
import org.homepoker.lib.util.JsonUtils;
import org.homepoker.model.user.User;
import org.homepoker.model.user.UserInformationUpdate;
import org.homepoker.model.user.UserPasswordChangeRequest;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;
import org.springframework.shell.standard.ShellOption;

@Slf4j
@ShellComponent
public class UserCommands {

  private final ClientConnectionManager connectionManager;

  public UserCommands(ClientConnectionManager connectionManager) {
    this.connectionManager = connectionManager;
  }

  @ShellMethod("Register a default user.")
  public void registerDefaultUser() {
    registerUser("admin", "admin", "admin@test.com", "Mr Admin", "123 123 1234");
  }

  @ShellMethod("Register a user [loginId, password, email, name, phone].")
  public void registerUser(String login, String password, String email, String name, String phone) {

    User user = User.builder()
        .loginId(login)
        .email(email)
        .password(password)
        .name(name)
        .phone(phone)
        .build();

    //Print user information.
    log.info("Registered User:\n" + JsonUtils.toFormattedJson(user));
  }

  @ShellMethod("This will display the current user information.")
  public void user() {
    User user = connectionManager.getCurrentUser();

    if (user == null && !connectionManager.connectionAvailability().isAvailable()) {
      log.info("You are not connected to a server.");
    } else if (user == null) {
      log.info("You are logged in as a guest.");
    } else {
      log.info("Current User:\n" + JsonUtils.toFormattedJson(user));
    }
  }

  @ShellMethod("Update user's contact information [loginId, email, name, phone].")
  public void updateUser(String loginId, String email, String name, String phone) {

    connectionManager.updateUser(UserInformationUpdate.builder()
        .loginId(loginId)
        .email(email)
        .name(name)
        .phone(phone)
        .build());
  }

  @ShellMethod("Change user's password [loginId, oldPassword, newPassword].")
  public void userPasswordChange(String loginId, String oldPassword, String newPassword) {
    UserPasswordChangeRequest request = new UserPasswordChangeRequest(loginId, oldPassword, newPassword);
    log.info("Password has been changed.");
  }

  @ShellMethod("Find users registered with the server [loginId, email].")
  public void findUsers(@ShellOption(defaultValue = ShellOption.NULL) String loginId, @ShellOption(defaultValue = ShellOption.NULL) String email) {
    log.info("Search Complete. Found [" + 0 + "] users.");
  }

  @ShellMethod("Delete a user [loginId].")
  public void deleteUser(String loginId) {

    log.info("\nUser [{}] has been deleted.", loginId);
  }

  @ShellMethodAvailability({"register-default-user", "register-user", "find-users", "delete-user", "update-user", "user-password-change"})
  private Availability validConnection() {
    return this.connectionManager.connectionAvailability();
  }
}
