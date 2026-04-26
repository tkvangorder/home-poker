package org.homepoker.rest;

import org.homepoker.game.GameSettings;
import org.homepoker.game.cash.CashGameManager;
import org.homepoker.game.cash.CashGameService;
import org.homepoker.model.command.StartGame;
import org.homepoker.model.event.PokerEvent;
import org.homepoker.model.event.game.AdminViewingReplay;
import org.homepoker.model.game.GameStatus;
import org.homepoker.model.game.GameType;
import org.homepoker.model.game.Player;
import org.homepoker.model.game.PlayerStatus;
import org.homepoker.model.game.Seat;
import org.homepoker.model.game.Table;
import org.homepoker.model.game.cash.CashGame;
import org.homepoker.model.user.User;
import org.homepoker.recording.EventRecorderRepository;
import org.homepoker.recording.EventRecorderService;
import org.homepoker.recording.RecordedEvent;
import org.homepoker.security.JwtTokenService;
import org.homepoker.test.BaseIntegrationTest;
import org.homepoker.test.TestDataHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ReplayControllerIntegrationTest extends BaseIntegrationTest {

  @Autowired CashGameService cashGameService;
  @Autowired EventRecorderRepository eventRecorderRepository;
  @Autowired EventRecorderService eventRecorderService;
  @Autowired JwtTokenService jwtTokenService;

  /**
   * Spring Boot 4 removed {@code TestRestTemplate}; we use a plain {@link RestTemplate} with
   * a no-op error handler so non-2xx responses (e.g., 403) are surfaced as {@code ResponseEntity}
   * instead of throwing.
   */
  private final RestTemplate restTemplate = createRestTemplate();

  private static RestTemplate createRestTemplate() {
    RestTemplate template = new RestTemplate();
    // Treat all responses as success so non-2xx (e.g., 403) surface as ResponseEntity rather than throwing.
    template.setErrorHandler(new ResponseErrorHandler() {
      @Override
      public boolean hasError(ClientHttpResponse response) throws IOException {
        return false;
      }
    });
    return template;
  }

  @Test
  void adminGetForExistingHandReturnsEventsInOrder() {
    String gameId = setupOneHandPlayed();

    String adminToken = adminToken();
    ResponseEntity<List<RecordedEvent>> response = restTemplate.exchange(
        url(gameId, "TABLE-0", 1),
        HttpMethod.GET,
        new HttpEntity<>(authHeaders(adminToken)),
        new ParameterizedTypeReference<>() {});

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<RecordedEvent> body = response.getBody();
    assertThat(body).isNotNull().isNotEmpty();

    // Strictly non-decreasing sequence numbers (UserEvents are 0; broadcast events climb).
    long lastSeq = -1L;
    for (RecordedEvent ev : body) {
      assertThat(ev.sequenceNumber()).isGreaterThanOrEqualTo(lastSeq);
      lastSeq = Math.max(lastSeq, ev.sequenceNumber());
    }
  }

  @Test
  void nonAdminGetIsForbidden() {
    String gameId = setupOneHandPlayed();
    String userToken = nonAdminToken();

    ResponseEntity<String> response = restTemplate.exchange(
        url(gameId, "TABLE-0", 1),
        HttpMethod.GET,
        new HttpEntity<>(authHeaders(userToken)),
        String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void getForNonexistentHandReturnsEmptyArray() {
    String gameId = setupOneHandPlayed();
    String adminToken = adminToken();

    ResponseEntity<List<RecordedEvent>> response = restTemplate.exchange(
        url(gameId, "TABLE-0", 999),
        HttpMethod.GET,
        new HttpEntity<>(authHeaders(adminToken)),
        new ParameterizedTypeReference<>() {});

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEmpty();
  }

  @Test
  void getOnInProgressGameEmitsAdminViewingReplay() {
    String gameId = setupOneHandPlayed(); // Game ends ACTIVE/SEATING, not COMPLETED

    // Attach a capturing listener so we can observe AdminViewingReplay.
    CashGameManager manager = cashGameService.getGameManger(gameId);
    List<PokerEvent> captured = new CopyOnWriteArrayList<>();
    manager.addGameListener(new org.homepoker.game.GameListener() {
      @Override public String userId() { return "test-observer"; }
      @Override public boolean acceptsEvent(PokerEvent event) { return true; }
      @Override public void onEvent(PokerEvent event) { captured.add(event); }
    });

    String adminToken = adminToken();
    restTemplate.exchange(
        url(gameId, "TABLE-0", 1),
        HttpMethod.GET,
        new HttpEntity<>(authHeaders(adminToken)),
        new ParameterizedTypeReference<List<RecordedEvent>>() {});

    // The warning command was queued — give the loop a tick and assert.
    manager.processGameTick();
    await().atMost(Duration.ofSeconds(5))
        .until(() -> captured.stream().anyMatch(e -> e instanceof AdminViewingReplay));
  }

  @Test
  void getOnCompletedGameDoesNotEmitWarning() {
    String gameId = setupOneHandPlayed();

    // Force the in-memory game to COMPLETED.
    CashGameManager manager = cashGameService.getGameManger(gameId);
    List<PokerEvent> captured = new CopyOnWriteArrayList<>();
    manager.addGameListener(new org.homepoker.game.GameListener() {
      @Override public String userId() { return "test-observer-2"; }
      @Override public boolean acceptsEvent(PokerEvent event) { return true; }
      @Override public void onEvent(PokerEvent event) { captured.add(event); }
    });
    // Mutate the underlying game's status — for an integration test we accept this thin abstraction break.
    manager.getGameForTest().status(GameStatus.COMPLETED);

    String adminToken = adminToken();
    restTemplate.exchange(
        url(gameId, "TABLE-0", 1),
        HttpMethod.GET,
        new HttpEntity<>(authHeaders(adminToken)),
        new ParameterizedTypeReference<List<RecordedEvent>>() {});

    manager.processGameTick();
    // No AdminViewingReplay should appear.
    assertThat(captured).noneMatch(e -> e instanceof AdminViewingReplay);
  }

  // -----------------------
  // Test setup helpers
  // -----------------------

  private String url(String gameId, String tableId, int handNumber) {
    return "http://localhost:" + serverPort + "/admin/replay/games/" + gameId
        + "/tables/" + tableId + "/hands/" + handNumber;
  }

  private String setupOneHandPlayed() {
    // The id "admin" is in adminUsers in application-test.yml, so UserManager.registerUser
    // (via SecurityUtilities.assignRolesToUser) will assign ADMIN+USER roles automatically.
    User owner = createUser(TestDataHelper.user("admin", "password", "Admin"));
    String gameId = "replay-it-" + System.currentTimeMillis() + "-" + System.nanoTime();

    CashGame game = CashGame.builder()
        .id(gameId)
        .name("Replay IT")
        .type(GameType.TEXAS_HOLDEM)
        .status(GameStatus.SEATING)
        .startTime(Instant.now())
        .maxBuyIn(10000)
        .smallBlind(25)
        .bigBlind(50)
        .owner(owner)
        .build();

    Table table = Table.builder()
        .id("TABLE-0")
        .emptySeats(GameSettings.TEXAS_HOLDEM_SETTINGS.numberOfSeats())
        .status(Table.Status.PAUSED)
        .build();
    table.dealerPosition(5);
    game.tables().put(table.id(), table);

    for (int i = 0; i < 5; i++) {
      String uid = "rcit-player-" + i;
      User u = createUser(TestDataHelper.user(uid, "password", "Player " + uid));
      Player p = Player.builder()
          .user(u).status(PlayerStatus.ACTIVE)
          .chipCount(10000).buyInTotal(10000).reBuys(0).addOns(0).build();
      game.addPlayer(p);
      Seat s = table.seats().get(i);
      s.status(Seat.Status.JOINED_WAITING);
      s.player(p);
      p.tableId(table.id());
    }

    cashGameRepository.save(game);

    CashGameManager manager = cashGameService.getGameManger(gameId);
    manager.submitCommand(new StartGame(gameId, owner));
    manager.processGameTick();
    manager.processGameTick();

    // Wait for the async recorder worker to flush — scope to this game's events because
    // BaseIntegrationTest shares its TestContainers Mongo across subclasses.
    await().atMost(Duration.ofSeconds(5)).until(() -> {
      long writtenForGame = eventRecorderRepository.findAll().stream()
          .filter(r -> gameId.equals(r.gameId()))
          .count();
      return writtenForGame > 0
          && eventRecorderService.writtenEventCount() >= eventRecorderRepository.count();
    });

    return gameId;
  }

  private String adminToken() {
    User admin = userManager.getUser("admin");
    return jwtTokenService.generateToken(admin);
  }

  private String nonAdminToken() {
    User u = createUser(TestDataHelper.user("plainuser", "password", "Plain User"));
    return jwtTokenService.generateToken(u);
  }

  private HttpHeaders authHeaders(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }
}
