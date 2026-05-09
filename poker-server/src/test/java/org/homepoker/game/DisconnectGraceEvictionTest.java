package org.homepoker.game;

import org.homepoker.model.game.Player;
import org.homepoker.model.user.User;
import org.homepoker.test.GameManagerTestFixture;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the disconnect-grace-period eviction feature: a player whose last
 * WebSocket has been gone for longer than {@code GameSettings.disconnectGraceSeconds}
 * is automatically removed from the game on the next tick, using the same vacate
 * path as the explicit {@code LeaveGame} command.
 */
class DisconnectGraceEvictionTest {

  @Test
  void disconnectStampsDisconnectedAtOnPlayer() {
    GameManagerTestFixture fixture = GameManagerTestFixture.emptyGame();
    User alice = fixture.users().alice();

    fixture.joinGame(alice);
    fixture.registerListener(alice);
    fixture.tick();

    Instant before = Instant.now();
    fixture.unregisterListenersFor(alice);
    fixture.tick();
    Instant after = Instant.now();

    Player player = fixture.manager().getGame().players().get(alice.id());
    assertThat(player.disconnectedAt())
        .as("disconnect must stamp Player.disconnectedAt on the 1→0 ref-count transition")
        .isNotNull()
        .isBetween(before, after);
  }

  @Test
  void reconnectClearsDisconnectedAt() {
    GameManagerTestFixture fixture = GameManagerTestFixture.emptyGame();
    User alice = fixture.users().alice();

    fixture.joinGame(alice);
    fixture.registerListener(alice);
    fixture.tick();
    fixture.unregisterListenersFor(alice);
    fixture.tick();

    // Sanity: stamp is set.
    assertThat(fixture.manager().getGame().players().get(alice.id()).disconnectedAt())
        .isNotNull();

    fixture.registerListener(alice);
    fixture.tick();

    Player player = fixture.manager().getGame().players().get(alice.id());
    assertThat(player.disconnectedAt())
        .as("reconnect must clear Player.disconnectedAt on the 0→1 ref-count transition")
        .isNull();
  }

  @Test
  void secondListenerDoesNotChangeDisconnectedAt() {
    GameManagerTestFixture fixture = GameManagerTestFixture.emptyGame();
    User alice = fixture.users().alice();

    fixture.joinGame(alice);
    fixture.registerListener(alice);
    fixture.registerListener(alice);
    fixture.tick();

    Player player = fixture.manager().getGame().players().get(alice.id());
    assertThat(player.disconnectedAt())
        .as("disconnectedAt is null while at least one listener is connected")
        .isNull();

    // Drop one of two — count goes 2 → 1; must NOT stamp.
    fixture.unregisterOneListenerFor(alice);
    fixture.tick();

    assertThat(player.disconnectedAt())
        .as("ref-count 2→1 must NOT stamp disconnectedAt — only 1→0 does")
        .isNull();
  }

  @Test
  void constructorResetsDisconnectedAtForAllLoadedPlayers() {
    // Build a game where two players already have a stale disconnectedAt — simulates
    // loading the game from MongoDB after a server restart.
    GameManagerTestFixture seed = GameManagerTestFixture.emptyGame();
    User alice = seed.users().alice();
    User bob = seed.users().bob();
    seed.joinGame(alice);
    seed.joinGame(bob);

    Instant fakePast = Instant.now().minusSeconds(3600);
    seed.manager().getGame().players().get(alice.id()).disconnectedAt(fakePast);
    seed.manager().getGame().players().get(bob.id()).disconnectedAt(fakePast);

    // Hand the underlying CashGame to a fresh manager (the "after restart" case).
    GameManagerTestFixture.TestableGameManager reloaded =
        new GameManagerTestFixture.TestableGameManager(seed.manager().getGame());

    assertThat(reloaded.getGame().players().get(alice.id()).disconnectedAt())
        .as("constructor must clear disconnectedAt for Alice after reload")
        .isNull();
    assertThat(reloaded.getGame().players().get(bob.id()).disconnectedAt())
        .as("constructor must clear disconnectedAt for Bob after reload")
        .isNull();
  }

  @Test
  void freshDisconnectDoesNotEvict() {
    // singleTableMidHand puts the game in ACTIVE with 5 seated players in PRE_FLOP_BETTING.
    // Pick a non-action seat to disconnect so the action timeout does not interfere.
    GameManagerTestFixture fixture = GameManagerTestFixture.singleTableMidHand();

    Player target = pickNonActionPlayer(fixture);
    fixture.registerListener(target.user());
    fixture.tick();
    fixture.unregisterListenersFor(target.user());
    fixture.tick();

    // disconnectedAt is just now (within seconds), well under the 120s grace window.
    fixture.tick();

    Player after = fixture.manager().getGame().players().get(target.userId());
    assertThat(after.status())
        .as("a freshly disconnected player must NOT be evicted before the grace period elapses")
        .isNotEqualTo(org.homepoker.model.game.PlayerStatus.OUT);
    assertThat(after.tableId())
        .as("the seat must remain assigned to the player while the grace period is unexpired")
        .isNotNull();
  }

  @Test
  void staleDisconnectMidHandMarksOutAndRetainsSeat() {
    GameManagerTestFixture fixture = GameManagerTestFixture.singleTableMidHand();

    Player target = pickNonActionPlayer(fixture);
    String tableId = target.tableId();
    fixture.registerListener(target.user());
    fixture.tick();
    fixture.unregisterListenersFor(target.user());
    fixture.tick();

    // Force the timestamp into the past, beyond the default 120s grace.
    target.disconnectedAt(Instant.now().minusSeconds(200));
    fixture.tick();

    Player after = fixture.manager().getGame().players().get(target.userId());
    assertThat(after.status())
        .as("expired grace period during an active hand marks the player OUT")
        .isEqualTo(org.homepoker.model.game.PlayerStatus.OUT);

    // Seat must be retained — the hand is in progress (seat status ACTIVE).
    org.homepoker.model.game.Table table = fixture.manager().getGame().tables().get(tableId);
    boolean stillSeated = table.seats().stream()
        .anyMatch(s -> s.player() != null && s.player().userId().equals(target.userId()));
    assertThat(stillSeated)
        .as("during a hand, the seat must remain occupied; it is freed when the hand ends")
        .isTrue();
  }

  @Test
  void pausedGameDoesNotEvictDespiteStaleDisconnect() {
    GameManagerTestFixture fixture = GameManagerTestFixture.singleTableMidHand();

    Player target = pickNonActionPlayer(fixture);
    target.disconnectedAt(Instant.now().minusSeconds(3600));
    // Force the game to PAUSED directly. The sweep is gated on ACTIVE/BALANCING; PAUSED
    // is a stable state where admins have explicitly paused play and should not lose
    // seats.
    fixture.manager().gameForTestOnly().status(org.homepoker.model.game.GameStatus.PAUSED);

    fixture.tick();

    Player after = fixture.manager().getGame().players().get(target.userId());
    assertThat(after.status())
        .as("sweep is gated to ACTIVE/BALANCING; a PAUSED game must not evict anyone")
        .isNotEqualTo(org.homepoker.model.game.PlayerStatus.OUT);
    assertThat(after.disconnectedAt())
        .as("disconnectedAt is left intact while the sweep is gated off")
        .isNotNull();
  }

  @Test
  void resumeFromPausedRefreshesDisconnectedAtSoSweepGivesFreshGrace() {
    // singleTableMidHand puts the game in ACTIVE with 5 seated players. Force PAUSED to
    // simulate an admin-paused game where one player has been disconnected for far longer
    // than the grace period. On resume, the refresh must reset the stamp so the player
    // is NOT instantly evicted on the next tick.
    GameManagerTestFixture fixture = GameManagerTestFixture.singleTableMidHand();
    Player target = pickNonActionPlayer(fixture);

    Instant stale = Instant.now().minusSeconds(300);
    target.disconnectedAt(stale);

    // Force the state directly — we're testing the refresh on resume, not the two-phase
    // pause flow. Tables must also be PAUSED for ResumeGame's table-status loop.
    fixture.manager().gameForTestOnly().status(org.homepoker.model.game.GameStatus.PAUSED);
    for (org.homepoker.model.game.Table t : fixture.manager().getGame().tables().values()) {
      t.status(org.homepoker.model.game.Table.Status.PAUSED);
    }

    // Resume via the public command path. The admin user is the game owner — the same
    // user the singleTableMidHand fixture uses to create and start the game.
    User admin = org.homepoker.test.TestDataHelper.adminUser();
    fixture.submitCommand(new org.homepoker.model.command.ResumeGame(fixture.gameId(), admin));
    fixture.tick();

    Player after = fixture.manager().getGame().players().get(target.userId());
    assertThat(after.disconnectedAt())
        .as("PAUSED → ACTIVE must refresh the stale stamp so the player gets a fresh window")
        .isNotNull()
        .isAfter(stale);
    assertThat(after.status())
        .as("with a refreshed stamp, the same-tick sweep must NOT evict the player")
        .isNotEqualTo(org.homepoker.model.game.PlayerStatus.OUT);
  }

  // ------------------------------------------------------------------
  // helpers
  // ------------------------------------------------------------------

  /**
   * Pick a seated player whose seat is NOT the current action position. This keeps the
   * action timeout in {@code transitionFromBetting} from auto-folding the player we're
   * trying to test against.
   */
  private static Player pickNonActionPlayer(GameManagerTestFixture fixture) {
    org.homepoker.model.game.Table table =
        fixture.manager().getGame().tables().firstEntry().getValue();
    Integer actionPos = table.actionPosition(); // 1-based, may be null
    java.util.List<org.homepoker.model.game.Seat> seats = table.seats();
    for (int i = 0; i < seats.size(); i++) {
      int oneBasedPosition = i + 1;
      org.homepoker.model.game.Seat seat = seats.get(i);
      if (seat.player() == null) continue;
      if (actionPos != null && oneBasedPosition == actionPos) continue;
      return seat.player();
    }
    throw new IllegalStateException("No non-action seated player found");
  }
}
