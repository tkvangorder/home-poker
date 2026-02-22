package org.homepoker.test;


import org.homepoker.game.cash.CashGameRepository;
import org.homepoker.lib.exception.ValidationException;
import org.homepoker.model.user.User;
import org.homepoker.user.UserManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@SuppressWarnings("NotNullFieldNotInitialized")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Testcontainers
@ActiveProfiles({"test"})
public class BaseIntegrationTest {

  @ServiceConnection
  public static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:latest");

  static {
    mongoDBContainer.start();
  }
  @LocalServerPort
  protected int serverPort;

  @Autowired
  protected CashGameRepository cashGameRepository;

  @Autowired
  protected UserManager userManager;

  protected User createUser(User user) {
    User createdUser;
    try {
      createdUser = userManager.registerUser(user);
    } catch (ValidationException e) {
      // Do not fail if the user already exists.
      if (!e.getCode().equals("EXISTING_USER")) {
        throw e;
      }
      return userManager.getUser(user.loginId());
    }
    return createdUser;
  }

}
