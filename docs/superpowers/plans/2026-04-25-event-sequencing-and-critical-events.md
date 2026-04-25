# Event Sequencing and Critical Events Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make game/table event streams reliably reconstructable by clients — add sequence numbers, snapshot resume points, blind-posting events, and player connection events.

**Architecture:** Two in-memory monotonic counters per game (one game-stream, one per-table) stamp broadcast events at fan-out time inside `GameManager.processGameTick`. User-targeted events (anything implementing `UserEvent`) carry sequenceNumber=0 and are excluded from gap detection. Snapshot events carry the current stream seq so a client can resume cleanly. Blind posting and connection state become first-class events; connection state is mutated through the MPSC command queue so it serializes with player-action mutations.

**Tech Stack:** Java 25, Spring Boot 4, Gradle (Groovy DSL), JCTools MPSC queue, Jackson, JUnit 5, AssertJ, Mockito, TestContainers (MongoDB).

**Spec:** [`docs/superpowers/specs/2026-04-17-event-sequencing-and-critical-events-design.md`](../specs/2026-04-17-event-sequencing-and-critical-events-design.md)

---

## File Map

### poker-common — new files
- `model/game/BlindType.java` — `enum BlindType { SMALL, BIG }`
- `model/event/table/BlindPosted.java` — table-event record
- `model/event/game/PlayerDisconnected.java` — game-event record
- `model/event/game/PlayerReconnected.java` — game-event record
- `model/command/PlayerConnectedCommand.java` — internal command
- `model/command/PlayerDisconnectedCommand.java` — internal command

### poker-common — modified
- `model/event/GameEvent.java` — add `long sequenceNumber()` and `GameEvent withSequenceNumber(long seq)`
- All concrete `GameEvent` records under `model/event/game/` and `model/event/table/` — add `long sequenceNumber` field and `withSequenceNumber` method
- `model/event/user/GameSnapshot.java` — add `long gameStreamSeq` and `Map<String, Long> tableStreamSeqs`
- `model/event/user/TableSnapshot.java` — add `long streamSeq`

### poker-server — modified
- `game/GameManager.java` — add game-stream counter, ref-count map, internal-command processing, refactor `processGameTick` fan-out to stamp events
- `game/table/TableManager.java` — add per-table counter, expose `currentStreamSeq()`, stamp at fan-out caller's request
- `game/table/TexasHoldemTableManager.java` — emit `BlindPosted` from `postBlind`
- `websocket/PokerWebSocketHandler.java` — call `addGameListener` without removing existing (multi-socket support)

### Tests — new
- `poker-common`: `BlindTypeTest.java`, `PokerEventSerializationTest.java`
- `poker-server`: `SequenceNumbersTest.java`, `BlindPostedTest.java`, `PlayerConnectionEventsTest.java`, `SnapshotResumePointTest.java`

### Tests — updated for new `sequenceNumber` field
- `poker-server`: `GameManagerTest`, table-manager tests, `CashManagerAssert`, `WebSocketGameListenerTest`

---

## Phase 1 — Event sequence number infrastructure

The foundation: add `sequenceNumber` to `GameEvent`, give every event record a wither, then stamp at fan-out using two counters. After this phase, every broadcast event carries a stream seq, user-targeted events carry 0, and existing tests still pass.

### Task 1.1: Add `sequenceNumber` to `GameEvent` interface

**Files:**
- Modify: `poker-common/src/main/java/org/homepoker/model/event/GameEvent.java`

- [ ] **Step 1: Update the interface**

```java
package org.homepoker.model.event;

public interface GameEvent extends PokerEvent {
  String gameId();

  /**
   * Monotonic per-stream sequence number assigned at fan-out. The game stream is one
   * stream; each table has its own stream. {@link UserEvent} instances are excluded
   * from sequence assignment and carry {@code 0}; clients must not use them for gap
   * detection. Set to {@code 0} at construction; the manager stamps the real value
   * by calling {@link #withSequenceNumber(long)}.
   */
  long sequenceNumber();

  /**
   * Return a copy of this event with the given sequence number. Each concrete event
   * record overrides this to return its own type (covariant return).
   */
  GameEvent withSequenceNumber(long sequenceNumber);
}
```

- [ ] **Step 2: Verify compilation fails**

Run: `./gradlew :poker-common:compileJava`
Expected: FAIL — every event record now needs `sequenceNumber()` and `withSequenceNumber()`. Tasks 1.2–1.4 add them.

- [ ] **Step 3: Do not commit yet**

The build is broken until Tasks 1.2–1.4 add `sequenceNumber` to every event record. Commit at the end of Task 1.4.

---

### Task 1.2: Add `sequenceNumber` field to all `TableEvent` records

**Files:**
- Modify: `poker-common/src/main/java/org/homepoker/model/event/table/HandStarted.java`
- Modify: `poker-common/src/main/java/org/homepoker/model/event/table/ActionOnPlayer.java`
- Modify: `poker-common/src/main/java/org/homepoker/model/event/table/PlayerActed.java`
- Modify: `poker-common/src/main/java/org/homepoker/model/event/table/HoleCardsDealt.java`
- Modify: `poker-common/src/main/java/org/homepoker/model/event/table/HandComplete.java`
- Modify: `poker-common/src/main/java/org/homepoker/model/event/table/BettingRoundComplete.java`
- Modify: `poker-common/src/main/java/org/homepoker/model/event/table/HandPhaseChanged.java`
- Modify: `poker-common/src/main/java/org/homepoker/model/event/table/WaitingForPlayers.java`
- Modify: `poker-common/src/main/java/org/homepoker/model/event/table/CommunityCardsDealt.java`
- Modify: `poker-common/src/main/java/org/homepoker/model/event/table/ShowdownResult.java`
- Modify: `poker-common/src/main/java/org/homepoker/model/event/table/TableStatusChanged.java`
- Modify: `poker-common/src/main/java/org/homepoker/model/event/table/PlayerTimedOut.java`

**Convention:** Insert `long sequenceNumber` as the second field, immediately after `Instant timestamp`. Add a `withSequenceNumber` method that returns a new instance with that field replaced.

- [ ] **Step 1: Update `HandStarted.java`**

```java
package org.homepoker.model.event.table;

import org.homepoker.lib.event.EventMarker;
import org.homepoker.model.event.TableEvent;
import org.homepoker.model.game.SeatSummary;

import java.time.Instant;
import java.util.List;

@EventMarker
public record HandStarted(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,
    int handNumber,
    int dealerPosition,
    int smallBlindPosition,
    int bigBlindPosition,
    int smallBlindAmount,
    int bigBlindAmount,
    int currentBet,
    int minimumRaise,
    List<SeatSummary> seats
) implements TableEvent {
  @Override
  public HandStarted withSequenceNumber(long sequenceNumber) {
    return new HandStarted(timestamp, sequenceNumber, gameId, tableId, handNumber,
        dealerPosition, smallBlindPosition, bigBlindPosition, smallBlindAmount,
        bigBlindAmount, currentBet, minimumRaise, seats);
  }
}
```

- [ ] **Step 2: Update `ActionOnPlayer.java`**

```java
@EventMarker
public record ActionOnPlayer(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,
    int seatPosition,
    String userId,
    Instant actionDeadline,
    int currentBet,
    int minimumRaise,
    int callAmount,
    int playerChipCount,
    int potTotal
) implements TableEvent {
  @Override
  public ActionOnPlayer withSequenceNumber(long sequenceNumber) {
    return new ActionOnPlayer(timestamp, sequenceNumber, gameId, tableId, seatPosition,
        userId, actionDeadline, currentBet, minimumRaise, callAmount, playerChipCount, potTotal);
  }
}
```

- [ ] **Step 3: Update `PlayerActed.java`**

```java
@EventMarker
public record PlayerActed(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,
    int seatPosition,
    String userId,
    PlayerAction action,
    int chipCount,
    HandPlayerStatus resultingStatus,
    int currentBet,
    int minimumRaise,
    int potTotal
) implements TableEvent {
  @Override
  public PlayerActed withSequenceNumber(long sequenceNumber) {
    return new PlayerActed(timestamp, sequenceNumber, gameId, tableId, seatPosition,
        userId, action, chipCount, resultingStatus, currentBet, minimumRaise, potTotal);
  }
}
```

- [ ] **Step 4: Update `HoleCardsDealt.java`**

(Implements both `TableEvent` and `UserEvent`. It carries the field but stamping skips it because it is a `UserEvent`.)

```java
@EventMarker
public record HoleCardsDealt(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,
    String userId,
    int seatPosition,
    List<SeatCard> cards,
    List<Integer> seatsWithCards
) implements TableEvent, UserEvent {
  @Override
  public HoleCardsDealt withSequenceNumber(long sequenceNumber) {
    return new HoleCardsDealt(timestamp, sequenceNumber, gameId, tableId, userId,
        seatPosition, cards, seatsWithCards);
  }
}
```

- [ ] **Step 5: Update `HandComplete.java`**

```java
@EventMarker
public record HandComplete(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,
    int handNumber
) implements TableEvent {
  @Override
  public HandComplete withSequenceNumber(long sequenceNumber) {
    return new HandComplete(timestamp, sequenceNumber, gameId, tableId, handNumber);
  }
}
```

- [ ] **Step 6: Update `BettingRoundComplete.java`**

```java
@EventMarker
public record BettingRoundComplete(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,
    HandPhase completedPhase,
    List<Table.Pot> pots,
    List<SeatSummary> seats,
    int potTotal
) implements TableEvent {
  @Override
  public BettingRoundComplete withSequenceNumber(long sequenceNumber) {
    return new BettingRoundComplete(timestamp, sequenceNumber, gameId, tableId,
        completedPhase, pots, seats, potTotal);
  }
}
```

- [ ] **Step 7: Update `HandPhaseChanged.java`**

```java
@EventMarker
public record HandPhaseChanged(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,
    HandPhase oldPhase,
    HandPhase newPhase
) implements TableEvent {
  @Override
  public HandPhaseChanged withSequenceNumber(long sequenceNumber) {
    return new HandPhaseChanged(timestamp, sequenceNumber, gameId, tableId, oldPhase, newPhase);
  }
}
```

- [ ] **Step 8: Update `WaitingForPlayers.java`**

```java
@EventMarker
public record WaitingForPlayers(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,
    int activePlayers,
    int seatedPlayers
) implements TableEvent {
  @Override
  public WaitingForPlayers withSequenceNumber(long sequenceNumber) {
    return new WaitingForPlayers(timestamp, sequenceNumber, gameId, tableId, activePlayers, seatedPlayers);
  }
}
```

- [ ] **Step 9: Update `CommunityCardsDealt.java`**

```java
@EventMarker
public record CommunityCardsDealt(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,
    List<Card> cards,
    HandPhase phase,
    List<Card> allCommunityCards
) implements TableEvent {
  @Override
  public CommunityCardsDealt withSequenceNumber(long sequenceNumber) {
    return new CommunityCardsDealt(timestamp, sequenceNumber, gameId, tableId, cards, phase, allCommunityCards);
  }
}
```

- [ ] **Step 10: Update `ShowdownResult.java`**

(Nested `PotResult` and `Winner` records are unchanged.)

```java
@EventMarker
public record ShowdownResult(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,
    List<PotResult> potResults
) implements TableEvent {
  public record PotResult(int potIndex, int potAmount, List<Winner> winners) {}
  public record Winner(int seatPosition, String userId, int amount, String handDescription) {}

  @Override
  public ShowdownResult withSequenceNumber(long sequenceNumber) {
    return new ShowdownResult(timestamp, sequenceNumber, gameId, tableId, potResults);
  }
}
```

- [ ] **Step 11: Update `TableStatusChanged.java`**

```java
@EventMarker
public record TableStatusChanged(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,
    Table.Status oldStatus,
    Table.Status newStatus
) implements TableEvent {
  @Override
  public TableStatusChanged withSequenceNumber(long sequenceNumber) {
    return new TableStatusChanged(timestamp, sequenceNumber, gameId, tableId, oldStatus, newStatus);
  }
}
```

- [ ] **Step 12: Update `PlayerTimedOut.java`**

```java
@EventMarker
public record PlayerTimedOut(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,
    int seatPosition,
    String userId,
    PlayerAction defaultAction
) implements TableEvent {
  @Override
  public PlayerTimedOut withSequenceNumber(long sequenceNumber) {
    return new PlayerTimedOut(timestamp, sequenceNumber, gameId, tableId, seatPosition, userId, defaultAction);
  }
}
```

- [ ] **Step 13: Verify only emission sites and event-construction calls fail to compile**

Run: `./gradlew :poker-common:compileJava`
Expected: PASS — `poker-common` itself has no event constructors.

Run: `./gradlew :poker-server:compileJava`
Expected: FAIL — every `new HandStarted(...)`, `new ActionOnPlayer(...)`, etc. in server code is missing the `sequenceNumber` argument.

Do not commit yet — Task 1.3 fixes the call sites.

---

### Task 1.3: Add `sequenceNumber` to non-table `GameEvent` records

**Files:**
- Modify: `poker-common/src/main/java/org/homepoker/model/event/game/PlayerJoined.java`
- Modify: `poker-common/src/main/java/org/homepoker/model/event/game/PlayerSeated.java`
- Modify: `poker-common/src/main/java/org/homepoker/model/event/game/PlayerBuyIn.java`
- Modify: `poker-common/src/main/java/org/homepoker/model/event/game/GameStatusChanged.java`
- Modify: `poker-common/src/main/java/org/homepoker/model/event/game/GameMessage.java`
- Modify: `poker-common/src/main/java/org/homepoker/model/event/game/PlayerMovedTables.java`

- [ ] **Step 1: Update `PlayerJoined.java`**

```java
package org.homepoker.model.event.game;

import org.homepoker.lib.event.EventMarker;
import org.homepoker.model.event.GameEvent;

import java.time.Instant;

@EventMarker
public record PlayerJoined(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String userId
) implements GameEvent {
  @Override
  public PlayerJoined withSequenceNumber(long sequenceNumber) {
    return new PlayerJoined(timestamp, sequenceNumber, gameId, userId);
  }
}
```

- [ ] **Step 2: Update `PlayerSeated.java`**

```java
@EventMarker
public record PlayerSeated(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String userId,
    String tableId
) implements GameEvent {
  @Override
  public PlayerSeated withSequenceNumber(long sequenceNumber) {
    return new PlayerSeated(timestamp, sequenceNumber, gameId, userId, tableId);
  }
}
```

- [ ] **Step 3: Update `PlayerBuyIn.java`**

```java
@EventMarker
public record PlayerBuyIn(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String userId,
    int amount,
    int newChipCount
) implements GameEvent {
  @Override
  public PlayerBuyIn withSequenceNumber(long sequenceNumber) {
    return new PlayerBuyIn(timestamp, sequenceNumber, gameId, userId, amount, newChipCount);
  }
}
```

- [ ] **Step 4: Update `GameStatusChanged.java`**

```java
@EventMarker
public record GameStatusChanged(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    GameStatus oldStatus,
    GameStatus newStatus
) implements GameEvent {
  @Override
  public GameStatusChanged withSequenceNumber(long sequenceNumber) {
    return new GameStatusChanged(timestamp, sequenceNumber, gameId, oldStatus, newStatus);
  }
}
```

- [ ] **Step 5: Update `GameMessage.java`**

```java
@EventMarker
public record GameMessage(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String message
) implements GameEvent {
  @Override
  public GameMessage withSequenceNumber(long sequenceNumber) {
    return new GameMessage(timestamp, sequenceNumber, gameId, message);
  }
}
```

- [ ] **Step 6: Update `PlayerMovedTables.java`**

```java
@EventMarker
public record PlayerMovedTables(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String userId,
    String fromTableId,
    String toTableId
) implements GameEvent {
  @Override
  public PlayerMovedTables withSequenceNumber(long sequenceNumber) {
    return new PlayerMovedTables(timestamp, sequenceNumber, gameId, userId, fromTableId, toTableId);
  }
}
```

- [ ] **Step 7: Verify**

Run: `./gradlew :poker-common:build`
Expected: PASS — `poker-common` compiles and its tests still pass (no field-equality assertions live in `poker-common`).

Do not commit; Task 1.4 fixes server call sites.

---

### Task 1.4: Update event-construction call sites in poker-server

**Convention:** every emission of a sequence-numbered event passes literal `0L` as the second argument. The fan-out (Task 1.7) replaces it with the real seq.

**Files:**
- Modify: `poker-server/src/main/java/org/homepoker/game/table/TexasHoldemTableManager.java` — every `new <TableEvent>(...)` call (lines 118, 152, 168, 232, 252, 283, 306, 340, 387, 413, 607, 636, 651 — verify by grep)
- Modify: `poker-server/src/main/java/org/homepoker/game/GameManager.java` — every `new <GameEvent>(...)` call
- Modify: `poker-server/src/main/java/org/homepoker/game/cash/CashGameManager.java` — any local emissions
- Modify: `poker-server/src/main/java/org/homepoker/game/table/TableManager.java` — none yet (TableSnapshot is a UserEvent)

- [ ] **Step 1: Locate every call site in the server**

Run:
```bash
cd /Users/tylervangorder/work/home-poker
grep -rn 'new HandStarted\|new ActionOnPlayer\|new PlayerActed\|new HoleCardsDealt\|new HandComplete\|new BettingRoundComplete\|new HandPhaseChanged\|new WaitingForPlayers\|new CommunityCardsDealt\|new ShowdownResult\|new TableStatusChanged\|new PlayerTimedOut\|new PlayerJoined\|new PlayerSeated\|new PlayerBuyIn\|new GameStatusChanged\|new GameMessage\|new PlayerMovedTables' poker-server/src/main
```

Expected: a list of every emission site to update.

- [ ] **Step 2: At each call site, insert `0L` as the second argument (right after `Instant.now()` or whatever timestamp is passed)**

Example before:
```java
gameContext.queueEvent(new HandStarted(
    Instant.now(),
    game.id(),
    table.id(),
    handNumber,
    ...
));
```

After:
```java
gameContext.queueEvent(new HandStarted(
    Instant.now(),
    0L, // sequenceNumber — stamped at fan-out
    game.id(),
    table.id(),
    handNumber,
    ...
));
```

Apply this transformation at every site identified in Step 1.

- [ ] **Step 3: Verify server compiles**

Run: `./gradlew :poker-server:compileJava`
Expected: PASS.

- [ ] **Step 4: Run the full test suite — many tests will fail because their expected event records used the old (no-seq) constructors**

Run: `./gradlew test`
Expected: Some tests fail with mismatched record arity in test data.

- [ ] **Step 5: Update test event constructions to insert `0L`**

In `poker-server/src/test/java`, run:
```bash
grep -rn 'new HandStarted\|new ActionOnPlayer\|new PlayerActed\|new HoleCardsDealt\|new HandComplete\|new BettingRoundComplete\|new HandPhaseChanged\|new WaitingForPlayers\|new CommunityCardsDealt\|new ShowdownResult\|new TableStatusChanged\|new PlayerTimedOut\|new PlayerJoined\|new PlayerSeated\|new PlayerBuyIn\|new GameStatusChanged\|new GameMessage\|new PlayerMovedTables' poker-server/src/test
```

For each test-file site, insert `0L` as the second constructor argument.

Note: test assertions that check fields like `.gameId()` or `.tableId()` keep working without change. Only constructor calls need updating.

- [ ] **Step 6: Re-run tests**

Run: `./gradlew test`
Expected: PASS.

If any tests still fail with full-record `assertEquals(expected, actual)` style comparisons, prefer field-level assertions (e.g. `assertThat(actual.gameId()).isEqualTo("g1")`) over rebuilding the full expected record. Note any such updates in the commit body.

- [ ] **Step 7: Commit Phase 1's structural change**

```bash
cd /Users/tylervangorder/work/home-poker
git add poker-common/src/main/java/org/homepoker/model/event \
        poker-server/src/main \
        poker-server/src/test
git commit -m "feat(events): add sequenceNumber field to GameEvent and all event records

Adds long sequenceNumber and per-record withSequenceNumber to every
GameEvent / TableEvent record. Emission sites pass 0L as a placeholder;
the fan-out stamps the real value (added in subsequent commit).

Refs: docs/superpowers/specs/2026-04-17-event-sequencing-and-critical-events-design.md"
```

---

### Task 1.5: Add per-table sequence counter to `TableManager`

The counter belongs on the parent `TableManager` (not just `TexasHoldemTableManager`) because `TableManager.applyCommand` builds the `TableSnapshot` (Phase 2 needs `currentStreamSeq()` exposed there). This is a small deviation from the spec wording but keeps Snapshot construction local.

**Files:**
- Modify: `poker-server/src/main/java/org/homepoker/game/table/TableManager.java`

- [ ] **Step 1: Add the counter and accessor**

In `TableManager`, add at the top of the class body:

```java
private final java.util.concurrent.atomic.AtomicLong tableStreamSeq = new java.util.concurrent.atomic.AtomicLong(0);

/** Returns the seq that has already been assigned to the most recent published TableEvent. 0 means none assigned yet. */
public long currentStreamSeq() {
  return tableStreamSeq.get();
}

/** Advance and return the next sequence number for this table's stream. Game-loop thread only. */
public long nextStreamSeq() {
  return tableStreamSeq.incrementAndGet();
}
```

(Imports: `java.util.concurrent.atomic.AtomicLong` may be added at the file top if you prefer FQN-free code.)

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :poker-server:compileJava`
Expected: PASS.

- [ ] **Step 3: Do not commit yet — bundle with Tasks 1.6 and 1.7**

---

### Task 1.6: Add game-stream counter and sequence-stamping helper to `GameManager`

**Files:**
- Modify: `poker-server/src/main/java/org/homepoker/game/GameManager.java`

- [ ] **Step 1: Add the counter field**

Near the other private fields (around lines 35–62), add:

```java
import java.util.concurrent.atomic.AtomicLong;

// ...

/** Game-stream sequence counter. Stamps non-Table GameEvents at fan-out. */
private final AtomicLong gameStreamSeq = new AtomicLong(0);
```

- [ ] **Step 2: Add accessor**

```java
/** Current game-stream seq (the most recent value assigned). 0 means none assigned yet. */
public long currentGameStreamSeq() {
  return gameStreamSeq.get();
}
```

- [ ] **Step 3: Add a helper that takes a list of pending events and yields a list of stamped-and-published-ready events**

The helper assigns seq numbers per the spec rules:

```java
/**
 * Stamps an event with the appropriate sequence number (game stream, the table's own
 * stream, or 0 for UserEvent). Returns the stamped copy. Game-loop thread only.
 */
private PokerEvent stampEvent(PokerEvent event) {
  if (event instanceof UserEvent) {
    return event; // UserEvents (including HoleCardsDealt) are not part of gap detection
  }
  if (event instanceof TableEvent tableEvent) {
    TableManager<T> tm = tableManagers.get(tableEvent.tableId());
    if (tm == null) {
      // Defensive: should not happen for an emitted table event
      return event;
    }
    long seq = tm.nextStreamSeq();
    return tableEvent.withSequenceNumber(seq);
  }
  if (event instanceof GameEvent gameEvent) {
    long seq = gameStreamSeq.incrementAndGet();
    return gameEvent.withSequenceNumber(seq);
  }
  // Plain PokerEvent (e.g., SystemError) is filtered per-user, no seq.
  return event;
}
```

(Imports needed: `org.homepoker.model.event.GameEvent;` — verify it exists at the top; `org.homepoker.model.event.TableEvent;`; `org.homepoker.model.event.UserEvent;`; `org.homepoker.model.event.PokerEvent;`.)

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :poker-server:compileJava`
Expected: PASS.

Do not commit yet — Task 1.7 wires the helper into the fan-out.

---

### Task 1.7: Wire sequence stamping into the fan-out loop

**Files:**
- Modify: `poker-server/src/main/java/org/homepoker/game/GameManager.java` — `processGameTick`, the publish block (currently around lines 185–194)

- [ ] **Step 1: Replace the publish block**

Replace:

```java
// Publish events.
if (!gameListeners.isEmpty()) {
  gameContext.events().forEach(event -> {
    log.debug("Sending event: [{}]", event);
    for (GameListener listener : gameListeners) {
      if (listener.acceptsEvent(event)) {
        listener.onEvent(event);
      }
    }
  });
}
```

with:

```java
// Stamp every accumulated event in deterministic order, then publish.
// Stamping happens at fan-out (rather than at construction) so all listeners observe
// the same sequence number for the same event.
List<PokerEvent> stamped = new ArrayList<>(gameContext.events().size());
for (PokerEvent event : gameContext.events()) {
  stamped.add(stampEvent(event));
}

if (!gameListeners.isEmpty()) {
  for (PokerEvent event : stamped) {
    log.debug("Sending event: [{}]", event);
    for (GameListener listener : gameListeners) {
      if (listener.acceptsEvent(event)) {
        listener.onEvent(event);
      }
    }
  }
}
```

(Notes: stamp regardless of whether listeners are present so the seq advances consistently. This keeps client recovery correct even when a single listener disconnects mid-stream and a snapshot is taken later. `List`/`ArrayList` imports are already present at line 23.)

- [ ] **Step 2: Build and run all tests**

Run: `./gradlew clean test`
Expected: PASS — no behavior change yet for tests that don't assert on `sequenceNumber`. Tests that previously used `event.sequenceNumber() == 0` would now see the real value, but Task 1.4 didn't add such assertions.

- [ ] **Step 3: Commit**

```bash
cd /Users/tylervangorder/work/home-poker
git add poker-server/src/main/java/org/homepoker/game/GameManager.java \
        poker-server/src/main/java/org/homepoker/game/table/TableManager.java
git commit -m "feat(events): stamp broadcast events with monotonic stream seq at fan-out

Adds a game-stream AtomicLong on GameManager and a per-table counter on
TableManager. processGameTick now stamps each accumulated event before
publishing: TableEvents from the table's counter, other GameEvents from
the game counter, UserEvents not at all (sequenceNumber stays 0).

Refs: docs/superpowers/specs/2026-04-17-event-sequencing-and-critical-events-design.md"
```

---

### Task 1.8: SequenceNumbersTest — multi-table monotonicity

**Files:**
- Create: `poker-server/src/test/java/org/homepoker/game/SequenceNumbersTest.java`

This test seats players across two tables, drives one hand on each, and asserts that:
1. Each table's stream is strictly monotonic (1, 2, 3, …).
2. The two table streams are independent.
3. The game stream is independent of either table stream.
4. `HoleCardsDealt` events carry `sequenceNumber == 0`.

- [ ] **Step 1: Write the failing test**

```java
package org.homepoker.game;

import org.homepoker.model.event.GameEvent;
import org.homepoker.model.event.PokerEvent;
import org.homepoker.model.event.TableEvent;
import org.homepoker.model.event.UserEvent;
import org.homepoker.model.event.table.HoleCardsDealt;
import org.homepoker.test.GameManagerTestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the spec's sequence-number contract: per-table monotonic streams,
 * a separate game stream, and UserEvents excluded from numbering.
 */
class SequenceNumbersTest {

  @Test
  void perTableStreamsAreIndependentAndMonotonic() {
    // Use the project's existing test fixture to spin up a two-table game and play a hand on each.
    // (See GameManagerTest for an example of how the fixture is constructed.)
    GameManagerTestFixture fixture = GameManagerTestFixture.twoTablesWithHandPlayed();

    List<PokerEvent> events = fixture.savedEvents();

    // Group TableEvents by tableId
    Map<String, List<Long>> seqByTable = new TreeMap<>();
    for (PokerEvent e : events) {
      if (e instanceof UserEvent) continue;            // skip user-targeted events
      if (e instanceof TableEvent te) {
        seqByTable.computeIfAbsent(te.tableId(), id -> new java.util.ArrayList<>()).add(te.sequenceNumber());
      }
    }

    assertThat(seqByTable).hasSize(2);
    seqByTable.forEach((tableId, seqs) -> {
      assertThat(seqs)
          .as("table " + tableId + " stream is strictly monotonic starting at 1")
          .first().isEqualTo(1L);
      for (int i = 1; i < seqs.size(); i++) {
        assertThat(seqs.get(i))
            .as("table " + tableId + " seq #" + i)
            .isEqualTo(seqs.get(i - 1) + 1);
      }
    });
  }

  @Test
  void gameStreamIsIndependentOfTableStreams() {
    GameManagerTestFixture fixture = GameManagerTestFixture.twoTablesWithHandPlayed();

    List<Long> gameSeqs = fixture.savedEvents().stream()
        .filter(e -> !(e instanceof UserEvent))
        .filter(e -> e instanceof GameEvent && !(e instanceof TableEvent))
        .map(e -> ((GameEvent) e).sequenceNumber())
        .collect(Collectors.toList());

    assertThat(gameSeqs).isNotEmpty();
    assertThat(gameSeqs.get(0)).isEqualTo(1L);
    for (int i = 1; i < gameSeqs.size(); i++) {
      assertThat(gameSeqs.get(i)).isEqualTo(gameSeqs.get(i - 1) + 1);
    }
  }

  @Test
  void holeCardsDealtCarriesZeroSequenceNumber() {
    GameManagerTestFixture fixture = GameManagerTestFixture.twoTablesWithHandPlayed();

    List<HoleCardsDealt> holeCardEvents = fixture.savedEvents().stream()
        .filter(HoleCardsDealt.class::isInstance)
        .map(HoleCardsDealt.class::cast)
        .toList();

    assertThat(holeCardEvents).isNotEmpty();
    assertThat(holeCardEvents).allSatisfy(e ->
        assertThat(e.sequenceNumber())
            .as("HoleCardsDealt is a UserEvent and must not carry a sequence number")
            .isEqualTo(0L));
  }
}
```

**Note on `GameManagerTestFixture`:** The project already has `GameManagerTest.TestableGameManager` and helpers under `poker-server/src/test/java/org/homepoker/game`. Inspect that file when implementing the fixture; if a fixture utility doesn't already exist for a "two tables with one hand each" scenario, build a minimal one as part of this task by extracting the relevant setup from `GameManagerTest`. Use `application-test.yml` deterministic mode (single-thread, `gameLoopIntervalMilliseconds: 0`) so commands and ticks are synchronous.

- [ ] **Step 2: Run the test, expect failures only if behavior is wrong**

Run: `./gradlew :poker-server:test --tests SequenceNumbersTest`
Expected: PASS (the implementation is in place from Tasks 1.5–1.7). If a test fails, the failure is information about what to fix in the implementation.

- [ ] **Step 3: Commit**

```bash
git add poker-server/src/test/java/org/homepoker/game/SequenceNumbersTest.java
git commit -m "test(events): assert per-table and game-stream sequence monotonicity"
```

---

## Phase 2 — Snapshot resume points

Snapshots become the recovery anchor: `GameSnapshot` carries `gameStreamSeq` plus a `tableId → seq` map, `TableSnapshot` carries the per-table `streamSeq`. Construction sites read counters via the accessors added in Phase 1.

### Task 2.1: Extend `GameSnapshot` with resume points

**Files:**
- Modify: `poker-common/src/main/java/org/homepoker/model/event/user/GameSnapshot.java`

- [ ] **Step 1: Add the new fields**

```java
package org.homepoker.model.event.user;

import org.homepoker.lib.event.EventMarker;
import org.homepoker.model.event.UserEvent;
import org.homepoker.model.game.GameStatus;
import org.homepoker.model.game.Player;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@EventMarker
public record GameSnapshot(
    Instant timestamp,
    String userId,
    String gameId,
    String gameName,
    GameStatus status,
    Instant startTime,
    int smallBlind,
    int bigBlind,
    List<Player> players,
    List<String> tableIds,
    long gameStreamSeq,
    Map<String, Long> tableStreamSeqs
) implements UserEvent {
}
```

- [ ] **Step 2: Verify build fails at the construction site**

Run: `./gradlew :poker-server:compileJava`
Expected: FAIL — `GameManager.getGameState` constructs a `GameSnapshot` without the new args.

---

### Task 2.2: Populate resume points in `GameManager.getGameState`

**Files:**
- Modify: `poker-server/src/main/java/org/homepoker/game/GameManager.java` — `getGameState` (lines 588–601)

- [ ] **Step 1: Update the constructor call**

```java
private void getGameState(GetGameState gameCommand, T game, GameContext gameContext) {
  Map<String, Long> tableSeqs = new java.util.HashMap<>();
  for (Map.Entry<String, TableManager<T>> entry : tableManagers.entrySet()) {
    tableSeqs.put(entry.getKey(), entry.getValue().currentStreamSeq());
  }
  gameContext.queueEvent(new GameSnapshot(
      Instant.now(),
      gameCommand.user().id(),
      game.id(),
      game.name(),
      game.status(),
      game.startTime(),
      game.smallBlind(),
      game.bigBlind(),
      List.copyOf(game.players().values()),
      List.copyOf(game.tables().keySet()),
      gameStreamSeq.get(),
      Map.copyOf(tableSeqs)
  ));
}
```

- [ ] **Step 2: Build and test**

Run: `./gradlew :poker-server:compileJava`
Expected: PASS.

Run: `./gradlew test`
Expected: PASS (existing tests do not assert on the new fields; if any did, update to ignore them or assert on `>= 0`).

---

### Task 2.3: Extend `TableSnapshot` with resume point

**Files:**
- Modify: `poker-common/src/main/java/org/homepoker/model/event/user/TableSnapshot.java`

- [ ] **Step 1: Add the field**

```java
@EventMarker
public record TableSnapshot(
    Instant timestamp,
    String userId,
    String gameId,
    Table table,
    long streamSeq
) implements UserEvent {
}
```

- [ ] **Step 2: Update the construction site in `TableManager`**

```java
public final void applyCommand(GameCommand command, Game<T> game, GameContext gameContext) {
  switch (command) {
    case GetTableState c -> gameContext.queueEvent(new TableSnapshot(
        Instant.now(),
        c.user().id(),
        c.gameId(),
        sanitizeTable(table, c.user().id()),
        currentStreamSeq()
    ));
    default -> applySubcommand(command, game, gameContext);
  }
}
```

- [ ] **Step 3: Build and test**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 4: Commit Phase 2**

```bash
git add poker-common/src/main/java/org/homepoker/model/event/user/GameSnapshot.java \
        poker-common/src/main/java/org/homepoker/model/event/user/TableSnapshot.java \
        poker-server/src/main/java/org/homepoker/game/GameManager.java \
        poker-server/src/main/java/org/homepoker/game/table/TableManager.java
git commit -m "feat(events): snapshots carry stream-seq resume points

GameSnapshot adds gameStreamSeq and tableStreamSeqs (tableId -> seq).
TableSnapshot adds streamSeq. Clients use these to resume gap detection
after a snapshot-based recovery.

Refs: docs/superpowers/specs/2026-04-17-event-sequencing-and-critical-events-design.md"
```

---

### Task 2.4: SnapshotResumePointTest

**Files:**
- Create: `poker-server/src/test/java/org/homepoker/game/SnapshotResumePointTest.java`

- [ ] **Step 1: Write the test**

```java
package org.homepoker.game;

import org.homepoker.model.command.GetTableState;
import org.homepoker.model.event.PokerEvent;
import org.homepoker.model.event.TableEvent;
import org.homepoker.model.event.user.TableSnapshot;
import org.homepoker.test.GameManagerTestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotResumePointTest {

  @Test
  void tableSnapshotCarriesCurrentStreamSeqAndNextEventIsSeqPlusOne() {
    GameManagerTestFixture fixture = GameManagerTestFixture.singleTableMidHand();
    String tableId = fixture.tableId();

    // Drive events on the table to advance the stream.
    long seqBeforeSnapshot = fixture.lastTableSeq(tableId);
    assertThat(seqBeforeSnapshot).isGreaterThan(0L);

    // Issue GetTableState as a player.
    fixture.submitCommand(new GetTableState(fixture.gameId(), tableId, fixture.player1()));
    fixture.tick();

    TableSnapshot snapshot = fixture.savedEvents().stream()
        .filter(TableSnapshot.class::isInstance)
        .map(TableSnapshot.class::cast)
        .reduce((first, second) -> second) // pick the most recent
        .orElseThrow();

    assertThat(snapshot.streamSeq()).isEqualTo(seqBeforeSnapshot);

    // Drive one more table event and assert it has seq = N + 1.
    fixture.driveOneMoreTableEvent(tableId);
    PokerEvent next = fixture.lastSavedEvent();
    assertThat(next).isInstanceOf(TableEvent.class);
    assertThat(((TableEvent) next).sequenceNumber()).isEqualTo(seqBeforeSnapshot + 1);
  }
}
```

**Note:** `GameManagerTestFixture` should expose `lastTableSeq(tableId)`, `driveOneMoreTableEvent(tableId)`, and `lastSavedEvent()`. Add these helpers to whatever fixture you built in Task 1.8.

- [ ] **Step 2: Run**

Run: `./gradlew :poker-server:test --tests SnapshotResumePointTest`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add poker-server/src/test/java/org/homepoker/game/SnapshotResumePointTest.java
git commit -m "test(events): TableSnapshot carries current stream seq for resume"
```

---

## Phase 3 — `BlindPosted` event

Add a small enum, a new TableEvent record, and emit from the existing `postBlind` method. The all-in-on-blind case is already handled by `Math.min(stackSize, blindAmount)`; the new event records the actual chips moved.

### Task 3.1: Add `BlindType` enum

**Files:**
- Create: `poker-common/src/main/java/org/homepoker/model/game/BlindType.java`

- [ ] **Step 1: Create the file**

```java
package org.homepoker.model.game;

/**
 * Type of blind being posted. Additively extensible: the wire format includes the
 * enum name, so adding {@code ANTE}, {@code STRADDLE}, or {@code DEAD_BLIND} later
 * is forward-compatible. Only {@link #SMALL} and {@link #BIG} are used today.
 */
public enum BlindType {
  SMALL,
  BIG
}
```

- [ ] **Step 2: Verify**

Run: `./gradlew :poker-common:compileJava`
Expected: PASS.

---

### Task 3.2: BlindTypeTest — Jackson round-trip

**Files:**
- Create: `poker-common/src/test/java/org/homepoker/model/game/BlindTypeTest.java`

- [ ] **Step 1: Write the test (TDD: write before depending on it elsewhere)**

```java
package org.homepoker.model.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlindTypeTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void smallSerializesAsName() throws Exception {
    String json = objectMapper.writeValueAsString(BlindType.SMALL);
    assertThat(json).isEqualTo("\"SMALL\"");
  }

  @Test
  void bigSerializesAsName() throws Exception {
    String json = objectMapper.writeValueAsString(BlindType.BIG);
    assertThat(json).isEqualTo("\"BIG\"");
  }

  @Test
  void smallDeserializesFromName() throws Exception {
    BlindType actual = objectMapper.readValue("\"SMALL\"", BlindType.class);
    assertThat(actual).isEqualTo(BlindType.SMALL);
  }

  @Test
  void bigDeserializesFromName() throws Exception {
    BlindType actual = objectMapper.readValue("\"BIG\"", BlindType.class);
    assertThat(actual).isEqualTo(BlindType.BIG);
  }
}
```

- [ ] **Step 2: Run**

Run: `./gradlew :poker-common:test --tests BlindTypeTest`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add poker-common/src/main/java/org/homepoker/model/game/BlindType.java \
        poker-common/src/test/java/org/homepoker/model/game/BlindTypeTest.java
git commit -m "feat(model): add BlindType enum (SMALL, BIG)"
```

---

### Task 3.3: Add `BlindPosted` table event

**Files:**
- Create: `poker-common/src/main/java/org/homepoker/model/event/table/BlindPosted.java`

- [ ] **Step 1: Create the record**

```java
package org.homepoker.model.event.table;

import org.homepoker.lib.event.EventMarker;
import org.homepoker.model.event.TableEvent;
import org.homepoker.model.game.BlindType;

import java.time.Instant;

@EventMarker
public record BlindPosted(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,
    int seatPosition,   // 1-based
    String userId,
    BlindType blindType,
    long amountPosted
) implements TableEvent {
  @Override
  public BlindPosted withSequenceNumber(long sequenceNumber) {
    return new BlindPosted(timestamp, sequenceNumber, gameId, tableId,
        seatPosition, userId, blindType, amountPosted);
  }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :poker-common:compileJava`
Expected: PASS.

---

### Task 3.4: Emit `BlindPosted` from `TexasHoldemTableManager.postBlind`

**Files:**
- Modify: `poker-server/src/main/java/org/homepoker/game/table/TexasHoldemTableManager.java` — `postBlind` (currently lines 1184–1197)

The current `postBlind` does not have access to `gameContext` or `BlindType`. Pass both to it, and emit immediately after the seat update.

- [ ] **Step 1: Locate the existing method**

```java
private void postBlind(int position, int blindAmount) {
  Seat seat = table.seatAt(position);
  Player player = seat.player();
  if (player == null) return;

  int chips = player.chipCount();
  int actualPost = Math.min(blindAmount, chips);
  player.chipCount(chips - actualPost);
  seat.currentBetAmount(actualPost);

  if (player.chipCount() <= 0) {
    seat.isAllIn(true);
  }
}
```

- [ ] **Step 2: Update the signature and body**

```java
private void postBlind(int position, int blindAmount, BlindType blindType, GameContext gameContext) {
  Seat seat = table.seatAt(position);
  Player player = seat.player();
  if (player == null) return;

  int chips = player.chipCount();
  int actualPost = Math.min(blindAmount, chips);
  player.chipCount(chips - actualPost);
  seat.currentBetAmount(actualPost);

  if (player.chipCount() <= 0) {
    seat.isAllIn(true);
  }

  gameContext.queueEvent(new BlindPosted(
      Instant.now(),
      0L,
      game.id(),
      table.id(),
      position,
      player.userId(),
      blindType,
      actualPost
  ));
}
```

(Imports needed at the top of the file: `org.homepoker.model.event.table.BlindPosted;`, `org.homepoker.model.game.BlindType;`. The existing `game` reference must be accessible; if not, also pass `Game<?> game` or use the existing `gameId` source.)

- [ ] **Step 3: Update the two call sites (currently lines 198–199)**

The `transitionTable` (or wherever blinds are posted) already has `gameContext` in scope.

Before:
```java
postBlind(sbPosition, smallBlind);
postBlind(bbPosition, bigBlind);
```

After:
```java
postBlind(sbPosition, smallBlind, BlindType.SMALL, gameContext);
postBlind(bbPosition, bigBlind, BlindType.BIG, gameContext);
```

If the call sites do not already have `gameContext` in scope, plumb it through; otherwise leave as a single-line change.

- [ ] **Step 4: Verify build**

Run: `./gradlew :poker-server:compileJava`
Expected: PASS.

- [ ] **Step 5: Run all tests**

Run: `./gradlew test`
Expected: PASS — existing tests don't assert on the absence of `BlindPosted`. If any flow test (e.g., `TexasHoldemTableManagerTest`) asserts an exact event-count or exact ordering, it will fail; fix by inserting `BlindPosted(SMALL)` and `BlindPosted(BIG)` between `HandStarted` and `ActionOnPlayer`.

- [ ] **Step 6: Commit**

```bash
git add poker-common/src/main/java/org/homepoker/model/event/table/BlindPosted.java \
        poker-server/src/main/java/org/homepoker/game/table/TexasHoldemTableManager.java \
        poker-server/src/test
git commit -m "feat(events): emit BlindPosted for small and big blinds

postBlind now emits BlindPosted with the actual chips moved (handles
all-in-on-blind via Math.min). The HandStarted -> BlindPosted(SMALL) ->
BlindPosted(BIG) -> ActionOnPlayer ordering is now visible to clients.

Refs: docs/superpowers/specs/2026-04-17-event-sequencing-and-critical-events-design.md"
```

---

### Task 3.5: BlindPostedTest

**Files:**
- Create: `poker-server/src/test/java/org/homepoker/game/table/BlindPostedTest.java`

- [ ] **Step 1: Write the test**

```java
package org.homepoker.game.table;

import org.homepoker.model.event.PokerEvent;
import org.homepoker.model.event.table.ActionOnPlayer;
import org.homepoker.model.event.table.BlindPosted;
import org.homepoker.model.event.table.HandStarted;
import org.homepoker.model.game.BlindType;
import org.homepoker.test.GameManagerTestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlindPostedTest {

  @Test
  void blindPostedFollowsHandStartedAndPrecedesActionOnPlayer() {
    GameManagerTestFixture fixture = GameManagerTestFixture.singleTableHandStartedThroughActionOnPlayer();

    List<PokerEvent> events = fixture.savedEvents();

    int handStartedIdx  = indexOfFirst(events, HandStarted.class);
    int blindSmallIdx   = indexOfFirstBlind(events, BlindType.SMALL);
    int blindBigIdx     = indexOfFirstBlind(events, BlindType.BIG);
    int actionOnIdx     = indexOfFirst(events, ActionOnPlayer.class);

    assertThat(handStartedIdx).isLessThan(blindSmallIdx);
    assertThat(blindSmallIdx).isLessThan(blindBigIdx);
    assertThat(blindBigIdx).isLessThan(actionOnIdx);
  }

  @Test
  void amountPostedEqualsBlindWhenStackIsLargeEnough() {
    GameManagerTestFixture fixture = GameManagerTestFixture
        .singleTableHandStartedThroughActionOnPlayer();

    BlindPosted small = (BlindPosted) fixture.savedEvents().stream()
        .filter(e -> e instanceof BlindPosted bp && bp.blindType() == BlindType.SMALL)
        .findFirst().orElseThrow();
    BlindPosted big = (BlindPosted) fixture.savedEvents().stream()
        .filter(e -> e instanceof BlindPosted bp && bp.blindType() == BlindType.BIG)
        .findFirst().orElseThrow();

    assertThat(small.amountPosted()).isEqualTo(fixture.smallBlindAmount());
    assertThat(big.amountPosted()).isEqualTo(fixture.bigBlindAmount());
  }

  @Test
  void amountPostedEqualsStackWhenPlayerCannotCoverBlind() {
    GameManagerTestFixture fixture = GameManagerTestFixture
        .singleTableSmallBlindPlayerStackBelowBlind(/* stackSize= */ 5, /* smallBlind= */ 10);

    BlindPosted small = (BlindPosted) fixture.savedEvents().stream()
        .filter(e -> e instanceof BlindPosted bp && bp.blindType() == BlindType.SMALL)
        .findFirst().orElseThrow();

    assertThat(small.amountPosted()).isEqualTo(5L);
    assertThat(fixture.seatAt(small.seatPosition()).isAllIn()).isTrue();
  }

  // --- helpers ---

  private static int indexOfFirst(List<PokerEvent> events, Class<?> type) {
    for (int i = 0; i < events.size(); i++) if (type.isInstance(events.get(i))) return i;
    throw new AssertionError("No event of type " + type.getSimpleName() + " in stream");
  }

  private static int indexOfFirstBlind(List<PokerEvent> events, BlindType type) {
    for (int i = 0; i < events.size(); i++) {
      if (events.get(i) instanceof BlindPosted bp && bp.blindType() == type) return i;
    }
    throw new AssertionError("No BlindPosted(" + type + ") in stream");
  }
}
```

**Note:** Add `singleTableHandStartedThroughActionOnPlayer()` and `singleTableSmallBlindPlayerStackBelowBlind(int stackSize, int smallBlind)` and `seatAt(int)` to `GameManagerTestFixture`. The "small stack" variant seats one player with a chip count below the small blind so we can assert all-in-on-blind.

- [ ] **Step 2: Run**

Run: `./gradlew :poker-server:test --tests BlindPostedTest`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add poker-server/src/test/java/org/homepoker/game/table/BlindPostedTest.java \
        poker-server/src/test/java/org/homepoker/test
git commit -m "test(events): assert BlindPosted ordering and all-in-on-blind"
```

---

## Phase 4 — Connection events

Connection state is tracked by a ref-counted `Map<String, Integer>` on `GameManager`. Listener registration/removal pushes internal commands (`PlayerConnectedCommand`, `PlayerDisconnectedCommand`) onto the MPSC queue so the ref-count update and event emission happen on the game-loop thread, serialized with player-action mutations.

### Task 4.1: Remove the "remove existing listener" behavior in `addGameListener`

The existing code removes any prior listener for the same userId, which makes ref counting impossible. Replace it with a plain add.

**Files:**
- Modify: `poker-server/src/main/java/org/homepoker/game/GameManager.java`

- [ ] **Step 1: Update `addGameListener`**

Replace:
```java
public void addGameListener(GameListener listener) {
  // Remove any existing listeners for this user (handles reconnect scenarios)
  removeGameListenersByUserId(listener.userId());
  gameListeners.add(listener);
}
```

with:
```java
public void addGameListener(GameListener listener) {
  gameListeners.add(listener);
  submitCommand(new PlayerConnectedCommand(game.id(), listener.userId()));
}
```

(`PlayerConnectedCommand` is created in Task 4.3.)

- [ ] **Step 2: Update `removeGameListener` and `removeGameListenersByUserId`**

```java
public void removeGameListener(GameListener listener) {
  if (gameListeners.remove(listener)) {
    submitCommand(new PlayerDisconnectedCommand(game.id(), listener.userId()));
  }
}

public void removeGameListenersByUserId(String userId) {
  int beforeCount = (int) gameListeners.stream().filter(l -> userId.equals(l.userId())).count();
  gameListeners.removeIf(listener -> userId.equals(listener.userId()));
  for (int i = 0; i < beforeCount; i++) {
    submitCommand(new PlayerDisconnectedCommand(game.id(), userId));
  }
}
```

(Each removed listener generates one decrement command; the ref-count update in Task 4.4 emits `PlayerDisconnected` only on the 1→0 transition.)

- [ ] **Step 3: Build will fail until commands are added**

Run: `./gradlew :poker-server:compileJava`
Expected: FAIL — `PlayerConnectedCommand` and `PlayerDisconnectedCommand` are unresolved.

Continue to Task 4.2 — do not commit yet.

---

### Task 4.2: Drop the existing reconnect-handling expectations from `WebSocketGameListener` registration

The handler at `PokerWebSocketHandler.afterConnectionEstablished` calls `gameManager.addGameListener(listener)` once. With the new spec, multi-socket-per-user is supported, so no call-site change is needed there. Verify nothing else calls `removeGameListenersByUserId` outside of `removeGameListener` cleanup paths.

**Files:**
- Read: `poker-server/src/main/java/org/homepoker/websocket/PokerWebSocketHandler.java`

- [ ] **Step 1: Audit**

Run:
```bash
grep -rn 'addGameListener\|removeGameListener\|removeGameListenersByUserId' poker-server/src/main
```

Expected: only `PokerWebSocketHandler` plus tests touch these. No additional changes needed.

---

### Task 4.3: Add internal connection commands

**Files:**
- Create: `poker-common/src/main/java/org/homepoker/model/command/PlayerConnectedCommand.java`
- Create: `poker-common/src/main/java/org/homepoker/model/command/PlayerDisconnectedCommand.java`

These are server-internal commands. They implement `GameCommand` but the `user()` method returns a synthetic system user (or, more cleanly, the affected user with no privileges).

- [ ] **Step 1: Determine how to populate `user()`**

Look at `org.homepoker.model.user.User` and check whether a no-privilege synthetic user is feasible. If `User` is a record that doesn't gate behavior on auth claims for these commands, return a minimal `User` instance constructed from the userId only (e.g., `User.builder().id(userId).build()` if a builder exists). If not, add a static factory `User.systemFor(userId)` to `User`.

- [ ] **Step 2: Create `PlayerConnectedCommand.java`**

```java
package org.homepoker.model.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.homepoker.lib.command.GameCommandMarker;
import org.homepoker.model.user.User;

@GameCommandMarker
public record PlayerConnectedCommand(String gameId, String connectedUserId) implements GameCommand {
  @JsonIgnore
  @Override
  public User user() {
    // Internal command — synthetic user carrying just the affected userId.
    return User.builder().id(connectedUserId).build();
  }
}
```

- [ ] **Step 3: Create `PlayerDisconnectedCommand.java`**

```java
package org.homepoker.model.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.homepoker.lib.command.GameCommandMarker;
import org.homepoker.model.user.User;

@GameCommandMarker
public record PlayerDisconnectedCommand(String gameId, String disconnectedUserId) implements GameCommand {
  @JsonIgnore
  @Override
  public User user() {
    return User.builder().id(disconnectedUserId).build();
  }
}
```

- [ ] **Step 4: Verify**

Run: `./gradlew :poker-common:compileJava`
Expected: PASS (assuming `User` has the required builder; if not, add it as part of this step).

---

### Task 4.4: Add ref counting and event emission in `GameManager`

**Files:**
- Modify: `poker-server/src/main/java/org/homepoker/game/GameManager.java`
- Create: `poker-common/src/main/java/org/homepoker/model/event/game/PlayerDisconnected.java`
- Create: `poker-common/src/main/java/org/homepoker/model/event/game/PlayerReconnected.java`

- [ ] **Step 1: Create `PlayerDisconnected.java`**

```java
package org.homepoker.model.event.game;

import org.homepoker.lib.event.EventMarker;
import org.homepoker.model.event.GameEvent;

import java.time.Instant;

@EventMarker
public record PlayerDisconnected(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String userId
) implements GameEvent {
  @Override
  public PlayerDisconnected withSequenceNumber(long sequenceNumber) {
    return new PlayerDisconnected(timestamp, sequenceNumber, gameId, userId);
  }
}
```

- [ ] **Step 2: Create `PlayerReconnected.java`**

```java
package org.homepoker.model.event.game;

import org.homepoker.lib.event.EventMarker;
import org.homepoker.model.event.GameEvent;

import java.time.Instant;

@EventMarker
public record PlayerReconnected(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String userId
) implements GameEvent {
  @Override
  public PlayerReconnected withSequenceNumber(long sequenceNumber) {
    return new PlayerReconnected(timestamp, sequenceNumber, gameId, userId);
  }
}
```

- [ ] **Step 3: Add the ref-count map and command handlers in `GameManager`**

Near the listener-list field:

```java
/**
 * Active-listener ref count keyed by userId. Mutated only on the game-loop thread,
 * via PlayerConnectedCommand / PlayerDisconnectedCommand processing.
 */
private final Map<String, Integer> activeListenerCounts = new HashMap<>();
```

Then in `applyCommand` (or wherever the master switch dispatches), add:

```java
case PlayerConnectedCommand cmd -> handlePlayerConnected(cmd, gameContext);
case PlayerDisconnectedCommand cmd -> handlePlayerDisconnected(cmd, gameContext);
```

Locate the existing dispatch (around line 409 — `case GetGameState gameCommand -> getGameState(...)`) and add the two new cases alongside it.

- [ ] **Step 4: Implement the handlers**

```java
private void handlePlayerConnected(PlayerConnectedCommand cmd, GameContext gameContext) {
  String userId = cmd.connectedUserId();
  int newCount = activeListenerCounts.merge(userId, 1, Integer::sum);

  if (newCount == 1 && game.players().containsKey(userId)) {
    // Player has an existing record — this is a reconnect, not a first-time join.
    gameContext.queueEvent(new PlayerReconnected(
        Instant.now(),
        0L,
        game.id(),
        userId
    ));
  }
  // If no Player record exists yet, the existing PlayerJoined path handles announcement
  // when the JoinGame command runs.
}

private void handlePlayerDisconnected(PlayerDisconnectedCommand cmd, GameContext gameContext) {
  String userId = cmd.disconnectedUserId();
  Integer current = activeListenerCounts.get(userId);
  if (current == null || current <= 0) {
    return; // Defensive: stale decrement
  }
  int newCount = current - 1;
  if (newCount == 0) {
    activeListenerCounts.remove(userId);
    gameContext.queueEvent(new PlayerDisconnected(
        Instant.now(),
        0L,
        game.id(),
        userId
    ));
  } else {
    activeListenerCounts.put(userId, newCount);
  }
}
```

- [ ] **Step 5: Build and run all tests**

Run: `./gradlew test`
Expected: PASS (no behavior change for existing tests — connection commands are submitted automatically by `addGameListener` / `removeGameListener`, but the events are only emitted on the 0→1 / 1→0 transition, and existing tests only register a single listener, so they observe at most one `PlayerReconnected` per registration when a Player exists).

If existing tests DO observe a new event (e.g., `PlayerReconnected` after `addGameListener` for an already-joined player) and assert exact event counts, update the test to allow the new event.

- [ ] **Step 6: Commit**

```bash
git add poker-common/src/main/java/org/homepoker/model/event/game/PlayerDisconnected.java \
        poker-common/src/main/java/org/homepoker/model/event/game/PlayerReconnected.java \
        poker-common/src/main/java/org/homepoker/model/command/PlayerConnectedCommand.java \
        poker-common/src/main/java/org/homepoker/model/command/PlayerDisconnectedCommand.java \
        poker-common/src/main/java/org/homepoker/model/user/User.java \
        poker-server/src/main/java/org/homepoker/game/GameManager.java
git commit -m "feat(events): emit PlayerDisconnected/PlayerReconnected on listener changes

addGameListener and removeGameListener now submit internal commands so
ref counting and event emission happen serialized on the game loop.
Multi-socket per user is supported; the events fire only on the 0->1
and 1->0 transitions. PlayerJoined still covers the first-time-join path.

Refs: docs/superpowers/specs/2026-04-17-event-sequencing-and-critical-events-design.md"
```

---

### Task 4.5: PlayerConnectionEventsTest

**Files:**
- Create: `poker-server/src/test/java/org/homepoker/game/PlayerConnectionEventsTest.java`

- [ ] **Step 1: Write the test**

```java
package org.homepoker.game;

import org.homepoker.model.event.PokerEvent;
import org.homepoker.model.event.game.PlayerDisconnected;
import org.homepoker.model.event.game.PlayerJoined;
import org.homepoker.model.event.game.PlayerReconnected;
import org.homepoker.test.GameManagerTestFixture;
import org.homepoker.model.user.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerConnectionEventsTest {

  @Test
  void firstListenerForNewUserDoesNotEmitReconnected() {
    GameManagerTestFixture fixture = GameManagerTestFixture.emptyGame();
    User alice = fixture.users().alice();

    // Register a listener BEFORE alice has joined the game.
    fixture.registerListener(alice);
    fixture.tick();

    assertThat(fixture.savedEvents())
        .as("PlayerReconnected must NOT fire when no Player record exists yet")
        .noneMatch(PlayerReconnected.class::isInstance);
  }

  @Test
  void reRegistrationAfterRemovalEmitsDisconnectedThenReconnected() {
    GameManagerTestFixture fixture = GameManagerTestFixture.emptyGame();
    User alice = fixture.users().alice();
    fixture.joinGame(alice);
    fixture.registerListener(alice);
    fixture.tick();

    fixture.unregisterListenersFor(alice);
    fixture.tick();
    assertHasEvent(fixture.savedEvents(), PlayerDisconnected.class);

    fixture.registerListener(alice);
    fixture.tick();
    assertHasEvent(fixture.savedEvents(), PlayerReconnected.class);
  }

  @Test
  void twoListenersForSameUserDoNotEmitSecondReconnect() {
    GameManagerTestFixture fixture = GameManagerTestFixture.emptyGame();
    User alice = fixture.users().alice();
    fixture.joinGame(alice);

    fixture.registerListener(alice);
    fixture.tick();
    int reconnectCountAfterFirst = (int) fixture.savedEvents().stream()
        .filter(PlayerReconnected.class::isInstance)
        .count();

    fixture.registerListener(alice);
    fixture.tick();
    int reconnectCountAfterSecond = (int) fixture.savedEvents().stream()
        .filter(PlayerReconnected.class::isInstance)
        .count();

    assertThat(reconnectCountAfterSecond)
        .as("Second listener for the same user must not emit another PlayerReconnected")
        .isEqualTo(reconnectCountAfterFirst);
  }

  @Test
  void removingOneOfTwoListenersDoesNotEmitDisconnected() {
    GameManagerTestFixture fixture = GameManagerTestFixture.emptyGame();
    User alice = fixture.users().alice();
    fixture.joinGame(alice);

    fixture.registerListener(alice); fixture.tick();
    fixture.registerListener(alice); fixture.tick();

    fixture.unregisterOneListenerFor(alice);
    fixture.tick();

    assertThat(fixture.savedEvents())
        .as("PlayerDisconnected must NOT fire when other listeners remain")
        .filteredOn(PlayerDisconnected.class::isInstance)
        .isEmpty();

    fixture.unregisterOneListenerFor(alice);
    fixture.tick();

    assertHasEvent(fixture.savedEvents(), PlayerDisconnected.class);
  }

  // --- helpers ---

  private static void assertHasEvent(java.util.List<PokerEvent> events, Class<? extends PokerEvent> type) {
    assertThat(events)
        .as("expected at least one event of type " + type.getSimpleName())
        .anyMatch(type::isInstance);
  }
}
```

**Note:** This test depends on `GameManagerTestFixture` exposing `registerListener(User)`, `unregisterListenersFor(User)`, `unregisterOneListenerFor(User)`, `joinGame(User)`, `users()`, and `emptyGame()`. Add them as part of this task.

- [ ] **Step 2: Run**

Run: `./gradlew :poker-server:test --tests PlayerConnectionEventsTest`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add poker-server/src/test/java/org/homepoker/game/PlayerConnectionEventsTest.java \
        poker-server/src/test/java/org/homepoker/test
git commit -m "test(events): ref-counted listener registration emits Disconnected/Reconnected"
```

---

## Phase 5 — Cross-cutting tests

### Task 5.1: PokerEventSerializationTest — JSON round-trip with sequenceNumber

**Files:**
- Create: `poker-common/src/test/java/org/homepoker/model/event/PokerEventSerializationTest.java`

- [ ] **Step 1: Write the test**

```java
package org.homepoker.model.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.homepoker.model.event.game.PlayerDisconnected;
import org.homepoker.model.event.game.PlayerReconnected;
import org.homepoker.model.event.table.BlindPosted;
import org.homepoker.model.game.BlindType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PokerEventSerializationTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void blindPostedRoundTripsWithSequenceNumber() throws Exception {
    BlindPosted original = new BlindPosted(
        Instant.parse("2026-04-25T12:00:00Z"),
        42L,
        "g1", "t1",
        3, "alice",
        BlindType.SMALL,
        50L
    );
    String json = objectMapper.writeValueAsString(original);
    BlindPosted parsed = objectMapper.readValue(json, BlindPosted.class);
    assertThat(parsed).isEqualTo(original);
    assertThat(parsed.sequenceNumber()).isEqualTo(42L);
  }

  @Test
  void playerDisconnectedRoundTripsWithSequenceNumber() throws Exception {
    PlayerDisconnected original = new PlayerDisconnected(
        Instant.parse("2026-04-25T12:00:00Z"),
        7L,
        "g1",
        "alice"
    );
    String json = objectMapper.writeValueAsString(original);
    PlayerDisconnected parsed = objectMapper.readValue(json, PlayerDisconnected.class);
    assertThat(parsed).isEqualTo(original);
    assertThat(parsed.sequenceNumber()).isEqualTo(7L);
  }

  @Test
  void playerReconnectedRoundTripsWithSequenceNumber() throws Exception {
    PlayerReconnected original = new PlayerReconnected(
        Instant.parse("2026-04-25T12:00:00Z"),
        8L,
        "g1",
        "alice"
    );
    String json = objectMapper.writeValueAsString(original);
    PlayerReconnected parsed = objectMapper.readValue(json, PlayerReconnected.class);
    assertThat(parsed).isEqualTo(original);
    assertThat(parsed.sequenceNumber()).isEqualTo(8L);
  }
}
```

- [ ] **Step 2: Run**

Run: `./gradlew :poker-common:test --tests PokerEventSerializationTest`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add poker-common/src/test/java/org/homepoker/model/event/PokerEventSerializationTest.java
git commit -m "test(events): JSON round-trip preserves sequenceNumber for new events"
```

---

### Task 5.2: Final integration check

- [ ] **Step 1: Run the full build**

Run: `./gradlew clean build`
Expected: PASS — all unit and integration tests succeed.

- [ ] **Step 2: Skim the diff**

Run:
```bash
git log --oneline main..HEAD
git diff main..HEAD --stat
```

Sanity-check that:
- Every event record has a `sequenceNumber` field as the second component.
- `GameManager.processGameTick` stamps before publishing.
- `GameSnapshot` carries `gameStreamSeq` and `tableStreamSeqs`.
- `TableSnapshot` carries `streamSeq`.
- `BlindPosted` is emitted by `postBlind` for both blinds.
- `PlayerDisconnected` / `PlayerReconnected` fire on 1→0 / 0→1 ref-count transitions.

- [ ] **Step 3: No commit** — this step is verification only.

---

## Self-Review Notes

**Spec coverage map:**
- Sequence numbers on GameEvent → Task 1.1
- Two counters per game → Tasks 1.5, 1.6
- Stamp at fan-out → Task 1.7
- UserEvent excluded from numbering → Task 1.6 (`stampEvent`), asserted in Task 1.8
- Snapshot resume points → Tasks 2.1–2.4
- BlindPosted event → Tasks 3.1–3.5
- BlindType enum → Tasks 3.1, 3.2
- All-in-on-blind handling → Task 3.5 (third test case)
- PlayerDisconnected / PlayerReconnected → Task 4.4
- Ref counting → Task 4.4
- Internal commands serialize through MPSC → Tasks 4.3, 4.4
- Existing tests need updating → handled inline in Task 1.4 step 5
- BlindTypeTest → Task 3.2
- PokerEventSerializationTest → Task 5.1
- SequenceNumbersTest → Task 1.8
- BlindPostedTest → Task 3.5
- PlayerConnectionEventsTest → Task 4.5
- SnapshotResumePointTest → Task 2.4

**Deviations from the spec wording:**
- Per-table counter is on `TableManager` (parent class) rather than `TexasHoldemTableManager` (subclass). This is necessary because `TableSnapshot` is constructed in `TableManager.applyCommand`, which needs `currentStreamSeq()`. Functionally identical; the spec's intent is per-table-manager-instance, and there is one TableManager per table.
- The plan introduces `GameManagerTestFixture` as a helper class hosting the multi-table scenarios used by Tasks 1.8, 2.4, 3.5, and 4.5. The spec doesn't prescribe a fixture; this just keeps test setup DRY.
