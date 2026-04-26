package org.homepoker.game;

import org.homepoker.model.event.PokerEvent;
import org.homepoker.model.event.game.PlayerDisconnected;
import org.homepoker.model.event.game.PlayerReconnected;
import org.homepoker.model.user.User;
import org.homepoker.test.GameManagerTestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the ref-counted listener tracking introduced for the connect/disconnect
 * event surface. The contract under test:
 * <ul>
 *   <li>The first listener for a user with no Player record yet does not emit
 *       {@code PlayerReconnected} — the {@code JoinGame} path owns the initial
 *       {@code PlayerJoined} signal.</li>
 *   <li>Once a Player record exists, removing all listeners and re-registering
 *       emits exactly one {@code PlayerDisconnected} and then one
 *       {@code PlayerReconnected}.</li>
 *   <li>A second listener for the same user does not emit a second
 *       {@code PlayerReconnected}; ref counts collapse multi-socket connections
 *       into a single logical "connected" state.</li>
 *   <li>Removing one of two listeners does not emit {@code PlayerDisconnected};
 *       only the transition from 1 to 0 active listeners does.</li>
 * </ul>
 */
class PlayerConnectionEventsTest {

  @Test
  void firstListenerForUserWithoutPlayerRecordDoesNotEmitReconnected() {
    GameManagerTestFixture fixture = GameManagerTestFixture.emptyGame();
    User alice = fixture.users().alice();

    int sizeBefore = fixture.savedEvents().size();
    fixture.registerListener(alice);
    fixture.tick();

    List<PokerEvent> emitted = newEvents(fixture, sizeBefore);
    assertThat(emitted)
        .as("no Player record yet for Alice — registerListener must not fire PlayerReconnected")
        .noneMatch(PlayerReconnected.class::isInstance);
  }

  @Test
  void reRegisterAfterRemovalEmitsDisconnectedThenReconnected() {
    GameManagerTestFixture fixture = GameManagerTestFixture.emptyGame();
    User alice = fixture.users().alice();

    // Establish a Player record for Alice and an active listener (count = 1).
    fixture.joinGame(alice);
    fixture.registerListener(alice);
    fixture.tick();

    // Drop all listeners — count goes 1 -> 0.
    int beforeRemoval = fixture.savedEvents().size();
    fixture.unregisterListenersFor(alice);
    fixture.tick();

    List<PokerEvent> afterRemoval = newEvents(fixture, beforeRemoval);
    assertThat(afterRemoval)
        .as("removing the sole listener must emit exactly one PlayerDisconnected for Alice")
        .filteredOn(PlayerDisconnected.class::isInstance)
        .extracting(e -> ((PlayerDisconnected) e).userId())
        .containsExactly(alice.id());
    assertThat(afterRemoval)
        .as("no PlayerReconnected expected on the disconnect step")
        .noneMatch(PlayerReconnected.class::isInstance);

    // Re-register — count goes 0 -> 1, Player record exists, so PlayerReconnected fires.
    int beforeReconnect = fixture.savedEvents().size();
    fixture.registerListener(alice);
    fixture.tick();

    List<PokerEvent> afterReconnect = newEvents(fixture, beforeReconnect);
    assertThat(afterReconnect)
        .as("re-registering must emit exactly one PlayerReconnected for Alice")
        .filteredOn(PlayerReconnected.class::isInstance)
        .extracting(e -> ((PlayerReconnected) e).userId())
        .containsExactly(alice.id());
    assertThat(afterReconnect)
        .as("no PlayerDisconnected expected on the reconnect step")
        .noneMatch(PlayerDisconnected.class::isInstance);
  }

  @Test
  void twoListenersForSameUserEmitOnlyOneReconnected() {
    GameManagerTestFixture fixture = GameManagerTestFixture.emptyGame();
    User alice = fixture.users().alice();

    fixture.joinGame(alice);
    // Cycle once so we are starting from a known "no listeners" state for Alice.
    fixture.registerListener(alice);
    fixture.tick();
    fixture.unregisterListenersFor(alice);
    fixture.tick();

    int sizeBefore = fixture.savedEvents().size();
    fixture.registerListener(alice); // 0 -> 1: emits PlayerReconnected
    fixture.registerListener(alice); // 1 -> 2: must NOT emit
    fixture.tick();

    List<PokerEvent> emitted = newEvents(fixture, sizeBefore);
    assertThat(emitted)
        .as("two registrations from 0 must produce exactly one PlayerReconnected for Alice")
        .filteredOn(PlayerReconnected.class::isInstance)
        .extracting(e -> ((PlayerReconnected) e).userId())
        .containsExactly(alice.id());
    assertThat(emitted)
        .as("no PlayerDisconnected expected while incrementing from 0 to 2")
        .noneMatch(PlayerDisconnected.class::isInstance);
  }

  @Test
  void removingOneOfTwoListenersDoesNotEmitDisconnectedRemovingSecondDoes() {
    GameManagerTestFixture fixture = GameManagerTestFixture.emptyGame();
    User alice = fixture.users().alice();

    fixture.joinGame(alice);
    fixture.registerListener(alice);
    fixture.registerListener(alice);
    fixture.tick();

    // Remove one of two — count goes 2 -> 1; no PlayerDisconnected.
    int beforeFirstRemoval = fixture.savedEvents().size();
    fixture.unregisterOneListenerFor(alice);
    fixture.tick();

    List<PokerEvent> afterFirst = newEvents(fixture, beforeFirstRemoval);
    assertThat(afterFirst)
        .as("removing one of two listeners must NOT emit PlayerDisconnected for Alice")
        .noneMatch(PlayerDisconnected.class::isInstance);

    // Remove the second — count goes 1 -> 0; PlayerDisconnected fires.
    int beforeSecondRemoval = fixture.savedEvents().size();
    fixture.unregisterOneListenerFor(alice);
    fixture.tick();

    List<PokerEvent> afterSecond = newEvents(fixture, beforeSecondRemoval);
    assertThat(afterSecond)
        .as("removing the last remaining listener must emit one PlayerDisconnected for Alice")
        .filteredOn(PlayerDisconnected.class::isInstance)
        .extracting(e -> ((PlayerDisconnected) e).userId())
        .containsExactly(alice.id());
  }

  // -------------------------------------------------------------------------
  // helpers
  // -------------------------------------------------------------------------

  /** Slice of {@code savedEvents} since {@code from}, as an immutable copy. */
  private static List<PokerEvent> newEvents(GameManagerTestFixture fixture, int from) {
    List<PokerEvent> events = fixture.savedEvents();
    return List.copyOf(events.subList(from, events.size()));
  }
}
