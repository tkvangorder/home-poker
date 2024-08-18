package org.homepoker.test;


import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Testcontainers
@ActiveProfiles({"test"})
public class BaseIntegrationTest {

  @Container
  @ServiceConnection
  public static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:latest");

  @LocalServerPort
  protected int serverPort;



}
