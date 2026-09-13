package org.homepoker.rest;

import org.homepoker.model.user.User;
import org.homepoker.security.JwtTokenService;
import org.homepoker.test.BaseIntegrationTest;
import org.homepoker.test.TestDataHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Angular shell is served unauthenticated for client routes,
 * that API paths are still protected, and that unknown paths are not swallowed
 * by the SPA fallback. A stub {@code static/index.html} lives on the test
 * classpath so this test does not depend on the real client build.
 * <p>
 * The route list exercised here must match both
 * {@code poker-client-angular/src/app/app.routes.ts} and
 * {@code SpaForwardingController}.
 */
class SpaForwardingControllerIntegrationTest extends BaseIntegrationTest {

  @Autowired JwtTokenService jwtTokenService;

  private RestTestClient client;

  @BeforeEach
  void setUpClient() {
    client = RestTestClient.bindToServer()
        .baseUrl("http://localhost:" + serverPort)
        .build();
  }

  @Test
  void rootServesIndexWithoutAuthentication() {
    client.get().uri("/")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(body -> assertThat(body).contains("SPA-TEST-SHELL"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/home", "/game/abc-123"})
  void clientRouteForwardsToIndexWithoutAuthentication(String path) {
    client.get().uri(path)
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(body -> assertThat(body).contains("SPA-TEST-SHELL"));
  }

  @Test
  void apiPathsStillRequireAuthentication() {
    client.get().uri("/cash-games/some-game")
        .exchange()
        .expectStatus().isForbidden();
  }

  @Test
  void unknownPathIsNotSwallowedBySpaFallback() {
    client.get().uri("/no-such-route")
        .headers(h -> h.setBearerAuth(adminToken()))
        .exchange()
        .expectStatus().isNotFound();
  }

  private String adminToken() {
    User admin = createUser(TestDataHelper.user("admin", "password", "Admin"));
    return jwtTokenService.generateToken(admin);
  }
}
