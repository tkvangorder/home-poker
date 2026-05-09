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
}
