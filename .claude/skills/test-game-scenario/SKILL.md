# Test-Game-Scenario Skill

## Description

Scaffolds a deterministic game-loop test. Tests in this project do not need threads, sleeps, or awaits — `application-test.yml` sets `threadModel: SINGLE_THREAD` and `gameLoopIntervalMilliseconds: 0`, so commands submitted to a `GameManager` are processed synchronously on the test thread.

Use this skill whenever the user asks for a test that exercises game-loop behavior: command handling, state transitions, event emission, multi-hand scenarios, or bug reproductions.

## Instructions

### Step 1: Clarify the scenario

Ask (conversationally — don't over-formalize):
1. **What's under test?** A command (`JoinGame`, `PlayerActionCommand`, etc.), a state transition (`SEATING → ACTIVE`), or a multi-step scenario (e.g. "3 players, one goes all-in pre-flop")?
2. **Level** — `GameManager` (game-level) or a specific `TableManager` (table/hand-level)?
3. **Starting state** — fresh game, mid-hand, paused, etc.?
4. **What to assert** — emitted events, seat/table state, or both?

### Step 2: Read these first

| File | Purpose |
|---|---|
| `poker-server/src/test/resources/application-test.yml` | Confirms single-thread + zero interval |
| `poker-server/src/test/java/org/homepoker/BaseIntegrationTest.java` | Base class for tests that need Mongo (TestContainers) |
| An existing nearby test | Mirror its style — imports, helpers, assertion library |

### Step 3: Pick the base class

- **Pure game-loop logic (no persistence)** — plain JUnit test; build a `GameManager` directly and pump commands.
- **Needs repositories / Spring context** — extend `BaseIntegrationTest`.

### Step 4: Scenario pattern

Typical shape:

```java
@Test
void playerJoiningMidHandDoesNotReceiveCards() {
    // given
    var game = newCashGame(/* 3 seated players */);
    advanceToHandPhase(game, HandPhase.FLOP);

    // when
    game.submit(new JoinGame(gameId, latecomer));
    game.tick();

    // then
    assertThat(game.seatFor(latecomer).status()).isEqualTo(JOINED_WAITING);
    assertThat(events(game)).extracting(Event::type)
        .doesNotContain(HOLE_CARDS_DEALT);
}
```

Key rules:
- **No `Thread.sleep`, no `Awaitility`, no `CountDownLatch`.** If the test wants you to reach for these, you're bypassing the deterministic mode — stop and ask.
- **One tick per command batch.** Submit all commands for a step, then call `tick()` once.
- **Assert events via the listener the test registers**, not by reflecting into queues.
- **Hole-card invariant** — any test that produces user-scoped events should assert foreign seats have null `cards`/`pendingIntent`. If the admin debug-view flag is on, assert the broadcast warning event was emitted.

### Step 5: Name the test file

Match the production class under test: `TexasHoldemTableManagerSidePotTest`, `CashGameManagerJoinLeaveTest`, etc. Prefer focused files over one giant test class.

### Step 6: Run it

```bash
./gradlew :poker-server:test --tests "<fully.qualified.TestClass>"
```

Expect it to pass on the first run — deterministic mode means flakes are bugs, not noise.