# Game-State Skill

## Description

Guides implementation of game-level state management in the poker server. Covers the `GameStatus` state machine (SCHEDULED -> SEATING -> ACTIVE <-> PAUSED -> COMPLETED), game-level commands, table creation/balancing, and the two-phase pause mechanism.

## Instructions

You are implementing game-level state transitions and commands for the home poker server. Before writing any code, read the full design doc and the existing source files listed below to understand the architecture.

### Design Reference

Read `cash-game-state-management.md` — specifically:
- **Section 2**: Game-Level State Machine (transition table, two-phase pause, commands per state)
- **Section 7**: Table Balancing Algorithm (when to balance, player selection priority, deferred moves, creating/merging tables, grace period)
- **Section 8**: Between-Hand Processing (relevant to game-level interactions with PREDEAL/HAND_COMPLETE)

### Key Files to Read First

| File | Purpose |
|---|---|
| `poker-server/src/main/java/org/homepoker/game/GameManager.java` | Abstract game loop — `transitionGame()` and `applyCommand()` are the main extension points |
| `poker-server/src/main/java/org/homepoker/game/GameStateTransitions.java` | Utility with `resetSeating()` — exists but is **not yet wired in** |
| `poker-server/src/main/java/org/homepoker/game/GameContext.java` | Per-tick event accumulator; use `gameContext.queueEvent()` for all events |
| `poker-server/src/main/java/org/homepoker/game/GameSettings.java` | Timing/sizing constants; `TEXAS_HOLDEM_SETTINGS` is the default |
| `poker-server/src/main/java/org/homepoker/game/table/TableManager.java` | Abstract table manager — game-level code calls `tableManager.transitionTable()` |
| `poker-server/src/main/java/org/homepoker/game/table/TexasHoldemTableManager.java` | Concrete table manager (currently empty `transitionTable()`) |
| `poker-common/src/main/java/org/homepoker/model/game/Table.java` | Table model with `Status` enum (PAUSED, PLAYING, PAUSE_AFTER_HAND) |
| `poker-common/src/main/java/org/homepoker/model/game/Seat.java` | Seat model with `Status` enum (ACTIVE, FOLDED, JOINED_WAITING, EMPTY) |
| `poker-common/src/main/java/org/homepoker/model/game/GameStatus.java` | Game lifecycle enum: SCHEDULED, SEATING, ACTIVE, PAUSED, COMPLETED |
| `poker-common/src/main/java/org/homepoker/model/game/Player.java` | Player model with `PlayerStatus` (ACTIVE, AWAY, BUYING_IN, OUT, REGISTERED) |
| `poker-common/src/main/java/org/homepoker/model/game/cash/CashGame.java` | Cash game entity — has `players()` map and `tables()` navigable map |
| `poker-common/src/main/java/org/homepoker/model/command/GameCommand.java` | Command interface with `@GameCommandMarker` scanning |
| `poker-common/src/main/java/org/homepoker/model/command/TableCommand.java` | Table command sub-interface (adds `tableId()`) |
| `poker-common/src/main/java/org/homepoker/model/command/EndGame.java` | Example command record to follow as a pattern |
| `poker-common/src/main/java/org/homepoker/model/event/GameEvent.java` | Game event interface with `@EventMarker` scanning |
| `poker-common/src/main/java/org/homepoker/model/event/GameMessage.java` | Example event record to follow as a pattern |
| `poker-common/src/main/java/org/homepoker/lib/exception/ValidationException.java` | Thrown for invalid commands; caught in `processGameTick()` and converted to `UserMessage` |
| `poker-server/src/main/java/org/homepoker/game/TableUtils.java` | `assignPlayerToRandomSeat()` utility |

### What Needs to Be Implemented

#### 1. New Command Records (poker-common)

Create these in `org.homepoker.model.command`, annotated with `@GameCommandMarker`:

```java
// Admin commands (GameCommand)
public record StartGame(String gameId, User user) implements GameCommand {}
public record PauseGame(String gameId, User user) implements GameCommand {}
public record ResumeGame(String gameId, User user) implements GameCommand {}

// Player commands (GameCommand)
public record BuyIn(String gameId, User user, int amount) implements GameCommand {}
public record LeaveGame(String gameId, User user) implements GameCommand {}
```

Follow the exact pattern of `EndGame.java` — record with `@GameCommandMarker`, implementing `GameCommand`, with `@JsonIgnore` on the `user` accessor via the interface default.

#### 2. New Event Records (poker-common)

Create these in `org.homepoker.model.event`, annotated with `@EventMarker`:

| Event | Interface | Fields |
|---|---|---|
| `GameStatusChanged` | `GameEvent` | `timestamp, gameId, oldStatus (GameStatus), newStatus (GameStatus)` |
| `PlayerBuyIn` | `GameEvent` | `timestamp, gameId, userId, amount, newChipCount` |
| `PlayerMoved` | `GameEvent` | `timestamp, gameId, userId, fromTableId, toTableId` |

Follow the pattern of `GameMessage.java`.

#### 3. Game-Level State Transitions (GameManager.transitionGame)

The current `transitionGame()` method has partial SCHEDULED -> SEATING logic and TODOs. Implement the full transition table from Section 2 of the design doc:

**SCHEDULED -> SEATING:**
- Guard: `now >= startTime - seatingTimeSeconds`
- Action: Call `GameStateTransitions.resetSeating()` (currently exists but is not called). Set all table statuses to `PAUSED`. Distribute registered players to random seats with `JOINED_WAITING`.

**SEATING -> ACTIVE:**
- Guard: Admin `StartGame` command received (handled as a flag set by applyCommand) AND `countOfSeatedPlayers >= 2` AND `now >= startTime`
- Action: Set all tables to `Table.Status.PLAYING`. Emit `GameMessage("Game is now active")`. Emit `GameStatusChanged`.

**ACTIVE -> PAUSED (two-phase):**
- Signal phase: `PauseGame` command sets all `PLAYING` tables to `PAUSE_AFTER_HAND`.
- Completion phase: In `transitionGame()`, check if all tables are `PAUSED`. If so, set game status to `PAUSED`.

**PAUSED -> ACTIVE:**
- `ResumeGame` command sets all tables to `PLAYING`, game status to `ACTIVE`.

**Any -> COMPLETED:**
- From ACTIVE: Set tables to `PAUSE_AFTER_HAND`, wait for all tables to report `PAUSED`, then set `COMPLETED`.
- From PAUSED/SCHEDULED/SEATING: Set `COMPLETED` immediately.

#### 4. Command Routing in applyCommand

Add cases to the `switch` in `GameManager.applyCommand()`:

```java
case StartGame c    -> startGame(c, game, gameContext);
case PauseGame c    -> pauseGame(c, game, gameContext);
case ResumeGame c   -> resumeGame(c, game, gameContext);
case BuyIn c        -> buyIn(c, game, gameContext);
case LeaveGame c    -> leaveGame(c, game, gameContext);
```

Each handler should:
1. Validate the command is allowed in the current `GameStatus` (see "Commands Accepted Per State" table in Section 2)
2. Validate permissions (admin commands check `securityUtilities`)
3. Mutate game state
4. Queue appropriate events via `gameContext.queueEvent()`
5. Throw `ValidationException` for invalid commands (caught by the tick loop)

#### 5. Table Balancing (Section 7)

Implement in `transitionGame()` when game is `ACTIVE`, after all tables have been transitioned:

- Balance tables to within 1 player of each other
- Player selection priority: JOINED_WAITING first, then next big blind, then clockwise
- Deferred moves for players in active hands (store `PendingMove` on the table)
- Create new table when all tables are full
- Merge tables with grace period (`tableMergeGraceSeconds`)
- Immediate removal for empty tables

This requires adding to models:
- `Table`: `List<PendingMove> pendingMoves` field, `PendingMove` record
- `CashGame`: `@Nullable Instant tableExcessSince` field
- `GameSettings`: `tableMergeGraceSeconds` field (default: 60)

#### 6. Seating New Players During ACTIVE/SEATING

When `RegisterForGame` arrives while game is SEATING or ACTIVE:
- Find the table with the fewest players
- Assign the player to a random empty seat with status `JOINED_WAITING`
- Set `Player.tableId` to the assigned table

#### 7. GameSettings Additions

Add to `GameSettings`:
```java
int predealTimeSeconds       // default: 15
int tableMergeGraceSeconds   // default: 60
boolean allowPostToPlay      // default: true
boolean requireMissedBlindPost // default: false
```

Update `TEXAS_HOLDEM_SETTINGS` with defaults.

### Patterns to Follow

- **Commands**: `@GameCommandMarker` record implementing `GameCommand`. Fields: `gameId`, `user` (with `@JsonIgnore` from interface), plus command-specific fields.
- **Events**: `@EventMarker` record implementing `GameEvent`/`TableEvent`. Fields: `timestamp` (Instant), `gameId`, plus event-specific fields. Events must pass `isValid()`.
- **Validation**: Throw `ValidationException` with a descriptive message. The game loop catches it and emits a `UserMessage` to the user.
- **State mutation**: All state changes happen on the game loop thread. No synchronization needed inside command handlers or transition methods.
- **Event queueing**: Use `gameContext.queueEvent(new SomeEvent(...))`. Events are published to listeners after the tick completes.

### Testing Guidance

**Unit tests** (preferred for game logic):
- Create a `GameContext` with `GameSettings.TEXAS_HOLDEM_SETTINGS`
- Build game/table/seat state directly using builders
- Call `transitionGame()` or command handlers directly
- Assert state changes and queued events
- See `poker-server/src/test/java/org/homepoker/game/GameManagerTest.java` (currently empty, add tests here)

**Integration tests** (for full stack with MongoDB):
- Extend `BaseIntegrationTest` (uses TestContainers for MongoDB)
- See `poker-server/src/test/java/org/homepoker/test/BaseIntegrationTest.java`
- The `test` profile uses `SINGLE_THREAD` mode and `gameLoopIntervalMilliseconds: 0` for deterministic behavior
- Call `processGameTick()` explicitly to advance game state

**Test configuration**:
- `poker-server/src/test/resources/application-test.yml` — activates single-thread mode

### Implementation Order

1. Create new command records (`StartGame`, `PauseGame`, `ResumeGame`, `BuyIn`, `LeaveGame`)
2. Create new event records (`GameStatusChanged`, `PlayerBuyIn`, `PlayerMoved`)
3. Add new fields to `GameSettings` and update `TEXAS_HOLDEM_SETTINGS`
4. Wire `GameStateTransitions.resetSeating()` into SCHEDULED -> SEATING transition
5. Implement command handlers in `GameManager` (with state validation)
6. Implement full `transitionGame()` logic (all transitions + two-phase pause)
7. Add model fields for table balancing (`PendingMove`, `tableExcessSince`)
8. Implement table balancing algorithm
9. Implement seating of new players during ACTIVE
10. Write unit tests for each transition and command
11. Run `./gradlew clean build` to verify