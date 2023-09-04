package org.homepoker.client.user;

import lombok.extern.slf4j.Slf4j;
import org.homepoker.client.ClientConnectionManager;
import org.homepoker.client.NotificationService;
import org.homepoker.event.user.UserPasswordChanged;
import org.homepoker.event.user.UserRegistered;
import org.homepoker.event.user.UserSearchCompleted;
import org.homepoker.lib.util.JsonUtils;
import org.homepoker.model.user.User;
import org.homepoker.model.user.UserCriteria;
import org.homepoker.model.user.UserInformationUpdate;
import org.homepoker.model.user.UserPasswordChangeRequest;
import org.springframework.context.event.EventListener;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;
import org.springframework.shell.standard.ShellOption;
import org.springframework.web.client.RestClient;

import static org.homepoker.PokerMessageRoutes.*;

@Slf4j
@ShellComponent
public class UserCommands {

  private final ClientConnectionManager connectionManager;

  public UserCommands(ClientConnectionManager connectionManager, RestClient.Builder restClientBuilder) {
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

    connectionManager.send(ROUTE_USER_MANAGER_REGISTER_USER, user);

  }

  @EventListener
  public void userRegistered(UserRegistered userRegistered) {
    NotificationService.info("User registered successfully. " + JsonUtils.toJson(userRegistered.user()));
  }

  @ShellMethod("This will display the current user information.")
  public void user() {
    User user = connectionManager.getCurrentUser();

    if (user == null && !connectionManager.connectionAvailability().isAvailable()) {
      log.info("You are not connected to a server.");
    } else {
      log.info("Current User:\n" + JsonUtils.toFormattedJson(user));
    }
  }

  @ShellMethod("Update user's contact information [loginId, email, name, phone].")
  public void updateUser(String loginId, String email, String name, String phone) {
    connectionManager.send(ROUTE_USER_MANAGER_UPDATE_USER, UserInformationUpdate.builder()
        .loginId(loginId)
        .email(email)
        .name(name)
        .phone(phone)
        .build()
    );
  }

  @ShellMethod("Change user's password [loginId, oldPassword, newPassword].")
  public void userPasswordChange(String loginId, String oldPassword, String newPassword) {
    connectionManager.send(ROUTE_USER_MANAGER_UPDATE_PASSWORD, new UserPasswordChangeRequest(loginId, oldPassword, newPassword));
  }

  @EventListener
  public void userPasswordChanged(UserPasswordChanged event) {
    log.info("Password has been changed.");
  }

  @ShellMethod("Find users registered with the server [loginId, email].")
  public void findUsers(@ShellOption(defaultValue = ShellOption.NULL) String loginId, @ShellOption(defaultValue = ShellOption.NULL) String email) {
    connectionManager.send(ROUTE_USER_MANAGER_USER_SEARCH, new UserCriteria(loginId, email));
  }

  @EventListener
  public void userPasswordChanged(UserSearchCompleted searchCompleted) {
    NotificationService.info("Search Complete. Found [" + searchCompleted.users().size() + "] users.");
  }

  @ShellMethodAvailability({"register-default-user", "register-user", "find-users", "delete-user", "update-user", "user-password-change"})
  private Availability validConnection() {
    return this.connectionManager.connectionAvailability();
  }
}
