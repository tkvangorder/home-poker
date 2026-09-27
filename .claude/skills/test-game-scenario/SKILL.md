---
name: test-game-scenario
description: Use when writing or fixing a test that exercises game-loop behavior (command handling, game/table state transitions, event emission, multi-hand or split-pot scenarios, bug reproductions). Drives the loop synchronously with the in-memory test fixtures; no sleeps, no Spring, no Mongo.
---

# Test-Game-Scenario Skill

Game-loop tests are deterministic: `processGameTick()` drains queued commands and runs one transition pass on the calling thread. Submit commands, tick, then assert on state and captured events.

## Step 1: Clarify the scenario

1. **What's under test?** A command, a state transition, a hand scenario, or a bug repro?
2. **Starting state** — empty game, mid-hand, heads-up, or a stacked deck for a showdown?
3. **What to assert** — emitted events, game/table/seat state, or both?

## Step 2: Pick the harness

All harness code lives in `poker-server/src/test/java/org/homepoker/test/`.

| Need | Use |
|---|---|
| Most game-loop tests | `GameManagerTestFixture` — in-memory `CashGameManager`, no Spring/DB, captures every event |
| Specific hole cards/board, side pots, split pots | `SplitPotScenarioFixture.builder().stacks(...).deck(DeckBuilder.holdem(n)...).build()` |
| Showdown assertions | `ShowdownAssert` |
| Test users | `TestDataHelper.user(...)`, `TestDataHelper.adminUser()`, `fixture.users().alice()` |
| Real repositories / Spring context | Extend `BaseIntegrationTest` (TestContainers Mongo). Rarely needed for loop logic. |

`GameManagerTestFixture` starting points:
- `emptyGame()` — SEATING, no tables or players (connection/join flows)
- `singleTableMidHand()` — 5 players, PRE_FLOP_BETTING, action on UTG
- `singleTableSmallBlindPlayerStackBelowBlind(stack, sb)` — heads-up, SB all-in on the blind
- `twoTablesWithHandPlayed()` — two 5-player tables, one hand completed on each

If none of these fits, add a new static factory to the fixture instead of hand-building a manager inside the test.

Read one existing test before writing yours:
- `poker-server/src/test/java/org/homepoker/game/DisconnectGraceEvictionTest.java` — fixture-driven game-level flows
- `poker-server/src/test/java/org/homepoker/game/table/SplitPotScenariosTest.java` — stacked-deck hand scenarios

## Step 3: Scenario pattern

```java
@Test
void foldingLastOpponentAwardsPotToBigBlind() {
  GameManagerTestFixture fixture = GameManagerTestFixture.singleTableMidHand();
  Table table = fixture.manager().getGame().tables().get(fixture.tableId());

  // when: everyone before the big blind folds
  while (table.actionPosition() != null && table.handPhase() == HandPhase.PRE_FLOP_BETTING) {
    Seat seat = fixture.seatAt(table.actionPosition());
    fixture.submitCommand(new PlayerActionCommand(
        fixture.gameId(), fixture.tableId(), seat.player().user(), new PlayerAction.Fold()));
    fixture.tick();
  }

  // then
  assertThat(fixture.savedEvents()).hasAtLeastOneElementOfType(HandComplete.class);
}
```

Rules:
- **No `Thread.sleep`, Awaitility, or latches.** Needing one means you've left the deterministic path; stop and ask.
- **Tick after submitting.** A command does nothing until `tick()`. Note that `StartGame` takes two ticks before cards are dealt: SEATING→ACTIVE, then deal.
- **Assert on `fixture.savedEvents()`** (or `lastSavedEvent()`), not on internal queues.
- **Hole-card invariant.** If the scenario sends seat/table data to a user (for example `GetTableState` → `TableSnapshot`), assert that seats the user doesn't own have `cards() == null` and `pendingIntent() == null`. If the admin debug-view flag is involved, also assert the broadcast warning was emitted.

## Step 4: Name and place the file

Put it next to the production class's package (`game/`, `game/table/`, `game/cash/`) and name it after the behavior: `BlindPostedTest`, `ShowdownWinningCardsTest`. Prefer a focused new class over growing a giant one.

## Step 5: Run it

```bash
./gradlew :poker-server:test --tests "org.homepoker.game.table.YourTest"
```

It should pass or fail consistently. In deterministic mode a flaky test is a bug, not noise.
