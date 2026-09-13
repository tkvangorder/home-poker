package org.homepoker.rest;

import org.homepoker.model.user.User;
import org.homepoker.security.JwtTokenService;
import org.homepoker.test.BaseIntegrationTest;
import org.homepoker.test.TestDataHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Angular shell is served unauthenticated for client routes,
 * that API paths are still protected, and that unknown paths are not swallowed
 * by the SPA fallback. A stub {@code static/index.html} lives on the test
 * classpath so this test does not depend on the real client build.
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

  @Test
  void homeRouteForwardsToIndexWithoutAuthentication() {
    client.get().uri("/home")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(body -> assertThat(body).contains("SPA-TEST-SHELL"));
  }

  @Test
  void gameRouteForwardsToIndexWithoutAuthentication() {
    client.get().uri("/game/abc-123")
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
    // The important assertion here is that an unmapped, authenticated GET is NOT swallowed by
    // the SPA fallback (i.e. it must never come back 200 with the index.html shell). Spring MVC's
    // static resource resolution raises NoResourceFoundException for "/no-such-route", which would
    // normally surface as a 404 -- but RestExceptionHandler has a catch-all
    // `@ExceptionHandler(Exception.class)` mapped to 500 INTERNAL_SERVER_ERROR (used to turn
    // otherwise-unhandled exceptions into a JSON error body), and that catch-all has no more
    // specific sibling for NoResourceFoundException, so it intercepts before Spring's default
    // 404 handling applies. 500 is this app's genuine status for an unmapped GET today, so we
    // assert that rather than loosening the check to merely "not 200".
    client.get().uri("/no-such-route")
        .headers(h -> h.setBearerAuth(adminToken()))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private String adminToken() {
    User admin = createUser(TestDataHelper.user("admin", "password", "Admin"));
    return jwtTokenService.generateToken(admin);
  }
}
