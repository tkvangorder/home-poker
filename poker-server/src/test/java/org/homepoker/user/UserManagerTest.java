package org.homepoker.user;

import org.homepoker.lib.exception.ValidationException;
import org.homepoker.model.user.User;
import org.homepoker.model.user.UserInformationUpdate;
import org.homepoker.model.user.UserPasswordChangeRequest;
import org.homepoker.security.SecurityUtilities;
import org.homepoker.test.TestDataHelper;
import org.homepoker.test.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoOperations;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class UserManagerTest {

  private final UserRepositoryInMemory userRepository = new UserRepositoryInMemory();
  private final SecurityUtilities securityUtilities = TestUtils.testSecurityUtilities();

  @Mock
  MongoOperations mongoOperations;

  private UserManager userManager;

  @BeforeEach
  public void setup() {
    userManager = new UserManager(userRepository, mongoOperations, securityUtilities);
  }

  @Test
  public void createAndGetUser() {
    User user = userManager.registerUser(TestDataHelper.createUser("fred", "password", "Fred"));
    User savedUser = userRepository.findByLoginId("fred");

    // Ensure the user returned from the user manager has its password filtered out.
    assertThat(user.password()).isNull();

    // Make sure the saved user has the correctly encoded password.
    assert savedUser != null;
    assertThat(securityUtilities.getPasswordEncoder().matches("password", savedUser.password())).isTrue();

    user = userManager.getUser("fred");
    assertThat(user).isNotNull();
    assertThat(user.password()).isNull();
  }

  @Test
  public void createUserValidation() {

    //noinspection DataFlowIssue
    assertThatThrownBy(() -> userManager.registerUser(null))
        .isInstanceOf(IllegalArgumentException.class);

    User user = TestDataHelper.createUser("fred", "password", "Fred");

    assertThatThrownBy(() -> userManager.registerUser(user.withId("nonnull")))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> userManager.registerUser(user.withLoginId(null)))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> userManager.registerUser(user.withPassword("")))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> userManager.registerUser(user.withEmail("")))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> userManager.registerUser(user.withName("")))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> userManager.registerUser(user.withPhone("")))
        .isInstanceOf(IllegalArgumentException.class);

  }

  @Test
  public void updatePassword() {
    User user = userManager.registerUser(TestDataHelper.createUser("fred", "password", "Fred"));

    UserPasswordChangeRequest request = new UserPasswordChangeRequest("fred", "password", "newpassword");
    userManager.updateUserPassword(request);

    User updatedUser = userRepository.findByLoginId("fred");
    assert updatedUser != null;
    assertThat(securityUtilities.getPasswordEncoder().matches("newpassword", updatedUser.password())).isTrue();
  }

  @Test
  public void updateUserInformation() {
    User user = userManager.registerUser(TestDataHelper.createUser("fred", "password", "Fred"));

    UserInformationUpdate update = UserInformationUpdate.builder()
        .loginId("fred")
        .email("freddy@fred.com")
        .name("Mr. Fred")
        .alias("Freddy")
        .phone("530-123-FRED")
        .build();

    userManager.updateUserInformation(update);

    User updatedUser = userManager.getUser("fred");
    assertThat(updatedUser).isNotNull();
    assertThat(updatedUser.password()).isNull();

    assertThat(updatedUser.email()).isEqualTo("freddy@fred.com");
    assertThat(updatedUser.name()).isEqualTo("Mr. Fred");
    assertThat(updatedUser.alias()).isEqualTo("Freddy");
    assertThat(updatedUser.phone()).isEqualTo("530-123-FRED");
  }

  @Test
  public void updateUserVaidation() {
    User user = userManager.registerUser(TestDataHelper.createUser("fred", "password", "Fred"));

    UserInformationUpdate update = UserInformationUpdate.builder()
        .loginId("fred")
        .email("freddy@fred.com")
        .name("Mr. Fred")
        .alias("Freddy")
        .phone("530-123-FRED")
        .build();

    assertThatThrownBy(() -> userManager.updateUserInformation(update.withLoginId(null)))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> userManager.updateUserInformation(update.withEmail("")))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> userManager.updateUserInformation(update.withName("")))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> userManager.updateUserInformation(update.withPhone("")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deleteUser() {
    User user = userManager.registerUser(TestDataHelper.createUser("fred", "password", "Fred"));
    assertThat(userManager.getUser("fred")).isNotNull();
    userManager.deleteUser("fred");
    assertThatThrownBy(() -> userManager.getUser("fred")).isInstanceOf(ValidationException.class);
  }
}