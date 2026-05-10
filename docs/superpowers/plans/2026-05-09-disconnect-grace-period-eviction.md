# Disconnect Grace-Period Eviction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a player's WebSocket has been disconnected for longer than a configurable grace period (default 120 seconds), automatically remove them from the game using the same vacate path as the explicit `LeaveGame` command. Reconnecting before expiry cancels the timer; explicit `LeaveGame` still vacates immediately.

**Architecture:** Add a transient `disconnectedAt` field to `Player`. Stamp it in `GameManager.handlePlayerDisconnected` on the 1→0 ref-count transition; clear it in `handlePlayerConnected` on the 0→1 transition. Each tick, while the game is `ACTIVE` or `BALANCING`, sweep `game.players()` for stale `disconnectedAt` timestamps and run the same vacate path as `LeaveGame` (extracted into a shared helper so the two paths can't diverge). The timer is **not** persisted across server restarts — `GameManager`'s constructor clears `disconnectedAt` on every loaded player, giving everyone a fresh window after a restart.

**Tech Stack:** Java 25, Spring Boot 4, Lombok, JUnit 5 + AssertJ. Game-loop tests use the deterministic `GameManagerTestFixture` (single-thread, manual ticks).

---

## File Structure

**Modify:**
- `poker-common/src/main/java/org/homepoker/model/game/Player.java` — add `disconnectedAt` field.
- `poker-server/src/main/java/org/homepoker/game/GameSettings.java` — add `disconnectGraceSeconds` field with default 120; update `TEXAS_HOLDEM_SETTINGS`.
- `poker-server/src/main/java/org/homepoker/game/GameManager.java`:
  - Constructor: reset `disconnectedAt` on every loaded player.
  - `handlePlayerConnected`: clear `disconnectedAt` on 0→1 transition (when Player record exists).
  - `handlePlayerDisconnected`: stamp `disconnectedAt` on 1→0 transition (when Player record exists).
  - Extract `removePlayerFromGame(Player, T, GameContext, String, String)` helper from `leaveGame`.
  - Add `sweepDisconnectedPlayers(T, GameContext)`.
  - Wire sweep into `processGameTick` between command processing and `transitionGame`.

**Create:**
- `poker-server/src/test/java/org/homepoker/game/DisconnectGraceEvictionTest.java` — focused unit tests.

The implementation is small enough to fit in one cohesive plan; no further decomposition needed.

---

## Task 1: Add `disconnectedAt` field to `Player`

**Files:**
- Modify: `poker-common/src/main/java/org/homepoker/model/game/Player.java`

- [ ] **Step 1: Add the field**

Open `poker-common/src/main/java/org/homepoker/model/game/Player.java` and add a new optional `Instant disconnectedAt` field next to `tableId`. Add the `java.time.Instant` import.

The full file should look like this:

```java
package org.homepoker.model.game;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;
import org.homepoker.model.user.User;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * This class represents the state of a player in the game and is always linked with a user.
 *
 * @author tyler.vangorder
 */
@Data
@Builder
@Accessors(fluent = true)
public final class Player {
  @JsonProperty
  private final User user;
  @JsonProperty
  private PlayerStatus status;
  @JsonProperty
  private int chipCount;
  @JsonProperty
  private int buyInTotal;
  @JsonProperty
  private int reBuys;
  @JsonProperty
  private int addOns;

  @Nullable
  @JsonProperty
  private String tableId;

  /**
   * When the last active WebSocket listener for this player went away. {@code null} when
   * the player is connected. Set on the 1→0 ref-count transition in
   * {@code GameManager.handlePlayerDisconnected}; cleared on the 0→1 transition in
   * {@code handlePlayerConnected} and on every tick where the player is removed from the
   * game. Reset to {@code null} for all players when {@code GameManager} is constructed,
   * so a server restart gives everyone a fresh grace window.
   */
  @Nullable
  @JsonProperty
  private Instant disconnectedAt;

  public String userId() {
    return user.id();
  }

}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :poker-common:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add poker-common/src/main/java/org/homepoker/model/game/Player.java
git commit -m "feat: add disconnectedAt field to Player"
```

---

## Task 2: Add `disconnectGraceSeconds` to `GameSettings`

**Files:**
- Modify: `poker-server/src/main/java/org/homepoker/game/GameSettings.java`

- [ ] **Step 1: Add the field and update the default**

Open `poker-server/src/main/java/org/homepoker/game/GameSettings.java`. Add a new `int disconnectGraceSeconds` field after `tableMergeGraceSeconds`, with a Javadoc entry, and set it to `120` in `TEXAS_HOLDEM_SETTINGS`.

Replace the current record body with:

```java
@Builder
public record GameSettings(
    boolean isTwoBoardGame,
    int numberOfSeats,
    int saveIntervalSeconds,
    int seatingTimeSeconds,
    int actionTimeSeconds,
    int reviewHandTimeSeconds,
    int predealTimeSeconds,
    int tableMergeGraceSeconds,
    int disconnectGraceSeconds,
    boolean allowPostToPlay,
    boolean requireMissedBlindPost
) {

  public final static GameSettings TEXAS_HOLDEM_SETTINGS = GameSettings.builder()
      .isTwoBoardGame(false)
      .numberOfSeats(9)
      .saveIntervalSeconds(5)
      .seatingTimeSeconds(60)
      .actionTimeSeconds(30)
      .reviewHandTimeSeconds(8)
      .predealTimeSeconds(15)
      .tableMergeGraceSeconds(60)
      .disconnectGraceSeconds(120)
      .allowPostToPlay(true)
      .requireMissedBlindPost(false)
      .build();

}
```

Also update the Javadoc on the record to include a line for the new field, e.g.:

```
 * @param disconnectGraceSeconds Seconds a player can be disconnected before being auto-removed from the game
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :poker-server:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add poker-server/src/main/java/org/homepoker/game/GameSettings.java
git commit -m "feat: add disconnectGraceSeconds to GameSettings (default 120)"
```

---

## Task 3: Stamp/clear `disconnectedAt` on connect & disconnect

**Files:**
- Modify: `poker-server/src/main/java/org/homepoker/game/GameManager.java:557-585`
- Test: `poker-server/src/test/java/org/homepoker/game/DisconnectGraceEvictionTest.java`

- [ ] **Step 1: Write the failing tests (stamp + clear)**

Create `poker-server/src/test/java/org/homepoker/game/DisconnectGraceEvictionTest.java`:

```java
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
}
```

- [ ] **Step 2: Run the new tests to verify they fail**

Run: `./gradlew :poker-server:test --tests "org.homepoker.game.DisconnectGraceEvictionTest"`
Expected: All three tests FAIL — `disconnectedAt` is never set, so `isNotNull()` fails on the first test.

- [ ] **Step 3: Update `handlePlayerConnected` and `handlePlayerDisconnected`**

In `poker-server/src/main/java/org/homepoker/game/GameManager.java`, replace these two methods (currently at lines ~557 and ~571):

```java
  /**
   * Increment the active-listener ref count for the user. The transition from
   * absent (or 0) to 1 emits {@link PlayerReconnected} when a Player record already
   * exists for the user. If no Player record exists yet (e.g., admin observer or a
   * brand-new connection that has not yet submitted JoinGame) no event is emitted —
   * the {@code JoinGame} path is responsible for {@code PlayerJoined}. The same 0→1
   * transition clears any pending {@code disconnectedAt} timestamp on the Player so
   * the grace-period sweep does not evict a reconnected user.
   */
  private void handlePlayerConnected(PlayerConnectedCommand cmd, GameContext gameContext) {
    String userId = cmd.connectedUserId();
    int newCount = activeListenerCounts.merge(userId, 1, Integer::sum);
    if (newCount == 1) {
      Player player = game.players().get(userId);
      if (player != null) {
        player.disconnectedAt(null);
        gameContext.queueEvent(new PlayerReconnected(
            Instant.now(), 0L, game.id(), userId));
      }
    }
  }

  /**
   * Decrement the active-listener ref count for the user. The transition from 1 to 0
   * emits {@link PlayerDisconnected} and stamps {@code Player.disconnectedAt} so the
   * grace-period sweep can evict the player after {@code disconnectGraceSeconds}
   * elapse without a reconnect. Stale decrements (no entry, or non-positive count)
   * are ignored defensively.
   */
  private void handlePlayerDisconnected(PlayerDisconnectedCommand cmd, GameContext gameContext) {
    String userId = cmd.disconnectedUserId();
    Integer current = activeListenerCounts.get(userId);
    if (current == null || current <= 0) {
      return;
    }
    int newCount = current - 1;
    if (newCount == 0) {
      activeListenerCounts.remove(userId);
      Player player = game.players().get(userId);
      if (player != null) {
        player.disconnectedAt(Instant.now());
      }
      gameContext.queueEvent(new PlayerDisconnected(
          Instant.now(), 0L, game.id(), userId));
    } else {
      activeListenerCounts.put(userId, newCount);
    }
  }
```

(`Player` is already imported via the `org.homepoker.model.game.*` wildcard at the top of the file.)

- [ ] **Step 4: Run the new tests to verify they pass**

Run: `./gradlew :poker-server:test --tests "org.homepoker.game.DisconnectGraceEvictionTest"`
Expected: All three tests PASS.

- [ ] **Step 5: Run the existing connection-events tests to confirm no regression**

Run: `./gradlew :poker-server:test --tests "org.homepoker.game.PlayerConnectionEventsTest"`
Expected: All four tests PASS (these tests do not look at `disconnectedAt`, so they should be unaffected).

- [ ] **Step 6: Commit**

```bash
git add poker-server/src/main/java/org/homepoker/game/GameManager.java poker-server/src/test/java/org/homepoker/game/DisconnectGraceEvictionTest.java
git commit -m "feat: stamp Player.disconnectedAt on disconnect, clear on reconnect"
```

---

## Task 4: Reset `disconnectedAt` on `GameManager` construction

**Files:**
- Modify: `poker-server/src/main/java/org/homepoker/game/GameManager.java:101-112`
- Test: `poker-server/src/test/java/org/homepoker/game/DisconnectGraceEvictionTest.java`

- [ ] **Step 1: Write the failing test**

Append to `DisconnectGraceEvictionTest`:

```java
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
```

- [ ] **Step 2: Run the new test to verify it fails**

Run: `./gradlew :poker-server:test --tests "org.homepoker.game.DisconnectGraceEvictionTest.constructorResetsDisconnectedAtForAllLoadedPlayers"`
Expected: FAIL — `disconnectedAt` is still the stale `fakePast` value.

- [ ] **Step 3: Reset `disconnectedAt` in the constructor**

In `poker-server/src/main/java/org/homepoker/game/GameManager.java`, replace the constructor (lines ~101-112):

```java
  public GameManager(T game, UserManager userManager, SecurityUtilities securityUtilities) {
    this.game = game;
    this.userManager = userManager;
    this.securityUtilities = securityUtilities;
    // TODO, as we add other game types, we can switch on game.type() to determine which table manager to use.
    this.gameSettings = GameSettings.TEXAS_HOLDEM_SETTINGS;

    // The disconnect grace timer lives only in memory — after a server restart every
    // player gets a fresh window. (We never persist activeListenerCounts either, so on
    // restart all players look "connected"; clearing disconnectedAt keeps the model
    // consistent with that.)
    for (Player player : game.players().values()) {
      player.disconnectedAt(null);
    }

    // Create table managers for any existing tables (handles persistence reload + deck recovery)
    for (Table table : game.tables().values()) {
      tableManagers.put(table.id(), createTableManagerForExistingTable(table));
    }
  }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :poker-server:test --tests "org.homepoker.game.DisconnectGraceEvictionTest.constructorResetsDisconnectedAtForAllLoadedPlayers"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add poker-server/src/main/java/org/homepoker/game/GameManager.java poker-server/src/test/java/org/homepoker/game/DisconnectGraceEvictionTest.java
git commit -m "feat: clear Player.disconnectedAt on GameManager construction"
```

---

## Task 5: Extract `removePlayerFromGame` helper

**Files:**
- Modify: `poker-server/src/main/java/org/homepoker/game/GameManager.java:731-778`

This task is a pure refactor — no behavior change. Existing `LeaveGame` tests must continue to pass.

- [ ] **Step 1: Confirm baseline tests pass before refactoring**

Run: `./gradlew :poker-server:test --tests "*LeaveGame*" --tests "org.homepoker.game.GameManagerTest" --tests "org.homepoker.game.PlayerConnectionEventsTest" --tests "org.homepoker.game.DisconnectGraceEvictionTest"`
Expected: BUILD SUCCESSFUL, all tests pass. (If any do not, fix or note before continuing.)

- [ ] **Step 2: Replace `leaveGame` and add `removePlayerFromGame`**

In `poker-server/src/main/java/org/homepoker/game/GameManager.java`, replace the entire `leaveGame` method (currently lines ~731-778) with:

```java
  private void leaveGame(LeaveGame gameCommand, T game, GameContext gameContext) {
    GameStatus status = game.status();
    if (status == GameStatus.COMPLETED) {
      throw new ValidationException("This game has already completed.");
    }

    Player player = game.players().get(gameCommand.user().id());
    if (player == null) {
      throw new ValidationException("You have not joined this game.");
    }

    String alias = player.user().alias();
    removePlayerFromGame(player, game, gameContext,
        alias + " will leave after the current hand.",
        alias + " has left the game.");
  }

  /**
   * Remove a player from the game, vacating their seat if possible. If the player is in
   * an active hand (seat status {@code ACTIVE} or {@code FOLDED}), the seat is retained
   * and the player is marked {@code OUT} — the seat is freed when the hand ends. Otherwise
   * the seat is vacated immediately. In all cases the player's status is set to
   * {@code OUT} and a {@link GameMessage} is emitted; {@code disconnectedAt} is cleared
   * so a re-joined player gets a fresh grace window. Used by the explicit
   * {@code LeaveGame} command and by the disconnect-grace-period sweep.
   *
   * @param midHandMessage   message text emitted when the player is in an active hand
   *                         (e.g., "Alice will leave after the current hand.")
   * @param departureMessage message text emitted on immediate vacate or when the player
   *                         was not seated (e.g., "Alice has left the game.")
   */
  private void removePlayerFromGame(Player player, T game, GameContext gameContext,
                                    String midHandMessage, String departureMessage) {
    if (player.tableId() != null) {
      Table table = game.tables().get(player.tableId());
      if (table != null) {
        for (Seat seat : table.seats()) {
          if (seat.player() != null && seat.player().userId().equals(player.userId())) {
            if (seat.status() == Seat.Status.ACTIVE || seat.status() == Seat.Status.FOLDED) {
              // Player is in an active hand (playing or folded), mark them for removal after the hand
              player.status(PlayerStatus.OUT);
              player.disconnectedAt(null);
              gameContext.queueEvent(new GameMessage(Instant.now(), 0L, game.id(), midHandMessage));
              gameContext.forceUpdate(true);
              return;
            }
            // Not in an active hand, remove from seat immediately
            seat.status(Seat.Status.EMPTY);
            seat.player(null);
            break;
          }
        }
      }
      player.tableId(null);
    }

    player.status(PlayerStatus.OUT);
    player.disconnectedAt(null);
    gameContext.queueEvent(new GameMessage(Instant.now(), 0L, game.id(), departureMessage));
    gameContext.forceUpdate(true);
  }
```

Note: the original `leaveGame` had a special `SCHEDULED` early-return. That branch did the same work as the fall-through (no table to vacate, set OUT, emit "has left the game.", forceUpdate) — so the consolidated helper preserves behavior for `SCHEDULED` too.

- [ ] **Step 3: Run the LeaveGame and existing-coverage tests**

Run: `./gradlew :poker-server:test --tests "*LeaveGame*" --tests "org.homepoker.game.GameManagerTest" --tests "org.homepoker.game.PlayerConnectionEventsTest" --tests "org.homepoker.game.DisconnectGraceEvictionTest"`
Expected: BUILD SUCCESSFUL — same set as Step 1, no regressions.

- [ ] **Step 4: Run the full server test suite as a regression sweep**

Run: `./gradlew :poker-server:test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add poker-server/src/main/java/org/homepoker/game/GameManager.java
git commit -m "refactor: extract removePlayerFromGame helper from leaveGame"
```

---

## Task 6: Add `sweepDisconnectedPlayers` and wire into `processGameTick`

**Files:**
- Modify: `poker-server/src/main/java/org/homepoker/game/GameManager.java`
- Test: `poker-server/src/test/java/org/homepoker/game/DisconnectGraceEvictionTest.java`

- [ ] **Step 1: Write the failing tests**

Append to `DisconnectGraceEvictionTest`:

```java
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
```

Note: `Seat` does not expose its own position. Seat positions are 1-based indices into `table.seats()`, and `Table.actionPosition` is the same 1-based number used by `Table.seatAt(int)`. The helper above iterates by index and compares `i + 1` to `actionPosition`.

- [ ] **Step 2: Run the new tests to verify they fail**

Run: `./gradlew :poker-server:test --tests "org.homepoker.game.DisconnectGraceEvictionTest.freshDisconnectDoesNotEvict" --tests "org.homepoker.game.DisconnectGraceEvictionTest.staleDisconnectMidHandMarksOutAndRetainsSeat" --tests "org.homepoker.game.DisconnectGraceEvictionTest.pausedGameDoesNotEvictDespiteStaleDisconnect"`
Expected:
- `freshDisconnectDoesNotEvict` — PASS already (no sweep yet, nothing evicts anyone).
- `staleDisconnectMidHandMarksOutAndRetainsSeat` — FAIL: status is not `OUT`.
- `pausedGameDoesNotEvictDespiteStaleDisconnect` — PASS already (no sweep yet).

The mid-hand test is the failing one that drives the implementation.

- [ ] **Step 3: Implement `sweepDisconnectedPlayers`**

In `poker-server/src/main/java/org/homepoker/game/GameManager.java`, add the following method. A natural location is near `removePlayerFromGame` (added in Task 5):

```java
  /**
   * Walk every player and evict any whose {@code disconnectedAt} is older than
   * {@code disconnectGraceSeconds}. Gated to {@link GameStatus#ACTIVE} and
   * {@link GameStatus#BALANCING}: while the game is {@code SCHEDULED}, {@code SEATING},
   * {@code PAUSED}, or {@code COMPLETED}, the timer does not advance and stamps survive
   * in case play resumes (or, for {@code COMPLETED}, are simply ignored). Eviction
   * delegates to {@link #removePlayerFromGame} so the mid-hand-vs-immediate vacate
   * behavior matches an explicit {@code LeaveGame}.
   */
  private void sweepDisconnectedPlayers(T game, GameContext gameContext) {
    GameStatus status = game.status();
    if (status != GameStatus.ACTIVE && status != GameStatus.BALANCING) {
      return;
    }
    Instant cutoff = Instant.now().minusSeconds(gameSettings.disconnectGraceSeconds());
    // Snapshot the players collection to avoid surprises if removePlayerFromGame is
    // ever extended to mutate game.players() (today it does not).
    for (Player player : new ArrayList<>(game.players().values())) {
      if (player.status() == PlayerStatus.OUT) {
        continue;
      }
      Instant disconnectedAt = player.disconnectedAt();
      if (disconnectedAt == null || !disconnectedAt.isBefore(cutoff)) {
        continue;
      }
      String alias = player.user().alias();
      removePlayerFromGame(player, game, gameContext,
          alias + " disconnected; will be removed after the current hand.",
          alias + " was removed after the disconnect grace period expired.");
    }
  }
```

- [ ] **Step 4: Wire the sweep into `processGameTick`**

Still in `GameManager.java`, locate `processGameTick` (around line 202). Insert a call to `sweepDisconnectedPlayers` between the command-processing loop and `transitionGame`. The block becomes:

```java
      for (GameCommand command : commands) {
        log.debug("Processing command: [{}]", command);
        try {
          applyCommand(command, game, gameContext);
        } catch (ValidationException e) {
          gameContext.queueEvent(UserMessage.builder()
              .timestamp(Instant.now())
              .userId(command.user().id())
              .severity(MessageSeverity.ERROR)
              .message(e.getMessage())
              .build());
        } catch (RuntimeException e) {
          log.error("An error occurred while while processing command [{}].\n{}", command, e.getMessage(), e);
          SystemError.SystemErrorBuilder builder = SystemError.builder()
              .timestamp(Instant.now())
              .gameId(command.gameId())
              .userId(command.user().id())
              .exception(e);
          if (command instanceof TableCommand tableCommand) {
            builder.tableId(tableCommand.tableId());
          }
          gameContext.queueEvent(builder.build());
        }
      }

      // Evict players whose disconnect grace period has elapsed. Runs after commands
      // (so a same-tick LeaveGame wins over an eviction) and before transitionGame
      // (so the rebalancing logic sees the post-eviction seat layout).
      sweepDisconnectedPlayers(game, gameContext);

      transitionGame(game, gameContext);
```

- [ ] **Step 5: Run the new tests to verify they pass**

Run: `./gradlew :poker-server:test --tests "org.homepoker.game.DisconnectGraceEvictionTest"`
Expected: All `DisconnectGraceEvictionTest` tests PASS.

- [ ] **Step 6: Run the full server test suite as a regression sweep**

Run: `./gradlew :poker-server:test`
Expected: BUILD SUCCESSFUL — no other tests should regress.

- [ ] **Step 7: Commit**

```bash
git add poker-server/src/main/java/org/homepoker/game/GameManager.java poker-server/src/test/java/org/homepoker/game/DisconnectGraceEvictionTest.java
git commit -m "feat: evict players whose disconnect grace period has elapsed"
```

---

## Task 7: Final verification

- [ ] **Step 1: Full build (compile + test all modules)**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Manual smoke (optional — only if a dev environment is wired up)**

If the user has MongoDB + Docker Compose running and time to do a manual check:

```
docker-compose up -d
./gradlew :poker-server:bootRun
```

Connect a WebSocket as a seated player, then drop the connection without sending `LeaveGame`. Watch the server log for the eviction `GameMessage` after ~120 s. Reconnect within the grace period to confirm the timer is cleared (no eviction).

If a manual smoke is not feasible, that's fine — the deterministic unit tests cover the critical logic.

---

## Notes for the implementer

- **Run a single test class** with `./gradlew :poker-server:test --tests "org.homepoker.game.DisconnectGraceEvictionTest"`. Run a single method by appending `.methodName`.
- **Single-thread test mode** is implicit via `application-test.yml`; commands submitted to a `GameManager` are processed deterministically when you call `tick()`.
- **Do not introduce a `Clock` abstraction** for time control. The tests work by directly setting `disconnectedAt` to `Instant.now().minusSeconds(N)`, which is sufficient to exercise the cutoff comparison without a refactor.
- **Don't add a new event type for "evicted-after-disconnect"**. The existing `PlayerDisconnected` event already broadcasts the disconnect, and `LeaveGame` already uses a `GameMessage` for departure. Mirroring that pattern keeps the surface small.
- **The `tableMergeGraceSeconds` field is unrelated** to this work, despite the similar name. It governs the table-merge grace during balancing.
