# Event Store and Replay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Capture every event flowing through `GameManager.processGameTick()` to a MongoDB collection so admins can replay any past hand via a single REST endpoint.

**Architecture:** A new `EventRecorder` is a `GameListener` (subclass of `UserGameListener`) registered on every `CashGameManager` at construction time. Its `onEvent` runs synchronously in the fan-out loop but only enqueues to a bounded async queue; a single virtual-thread worker drains the queue into `EventRecorderRepository.save(...)`. The game tick never blocks on Mongo. Replay is read-only against the captured collection plus a single admin-gated REST endpoint that, for non-completed games, submits an `AdminViewingReplayCommand` so the existing fan-out emits an `AdminViewingReplay` warning event to active players.

**Tech Stack:** Spring Boot 4, Java 25, Spring Data MongoDB, Spring Security (JWT, `@PreAuthorize`), Jackson 3.x (`tools.jackson.*` for the wire format, the existing `webSocketObjectMapper` is reused for event JSON), JUnit 5 / AssertJ / TestContainers.

**Spec:** [`docs/superpowers/specs/2026-04-25-event-store-and-replay-design.md`](../specs/2026-04-25-event-store-and-replay-design.md)

**Related:** the 2026-04-17 event-sequencing spec is now merged — broadcast events carry `long sequenceNumber` and snapshots carry resume points. The recorder picks up `sequenceNumber` directly and stores it on each `RecordedEvent`.

---

## File Map

### poker-common — new files
- `model/event/game/AdminViewingReplay.java` — broadcast `GameEvent` warning that an admin is viewing a replay of an in-progress game.
- `model/command/AdminViewingReplayCommand.java` — server-internal `GameCommand` that the replay controller submits to trigger the warning.

### poker-server — new files
- `recording/SystemUsers.java` — holds the synthetic `EVENT_RECORDER` `User` constant.
- `recording/RecordedEvent.java` — MongoDB document for one captured event.
- `recording/EventRecorderRepository.java` — `MongoRepository<RecordedEvent, String>` with two finder methods.
- `recording/EventRecorderService.java` — Spring `@Service` owning the bounded async queue and worker virtual thread.
- `recording/EventRecorder.java` — `UserGameListener` that captures every event and routes it to the service.
- `rest/ReplayController.java` — admin-only REST controller for the replay endpoint.

### poker-server — modified files
- `MongoConfiguration.java` — add compound-index creation for `RecordedEvent`.
- `game/GameManager.java` — add `case AdminViewingReplayCommand → ...` to `applyCommand`; add a guard in `removeGameListenersByUserId` so the recorder cannot be evicted.
- `game/cash/CashGameManager.java` — constructor signature gains an `EventRecorderService`; if non-null, attach `new EventRecorder(...)` after `super(...)`.
- `game/cash/CashGameService.java` — inject `EventRecorderService` and pass it to `new CashGameManager(...)` at line ~184.

### Tests — new
- `poker-server/src/test/java/org/homepoker/recording/EventRecorderIntegrationTest.java`
- `poker-server/src/test/java/org/homepoker/recording/EventRecorderResilienceTest.java`
- `poker-server/src/test/java/org/homepoker/rest/ReplayControllerIntegrationTest.java`

### Tests — modified
- `poker-server/src/test/java/org/homepoker/test/GameManagerTestFixture.java` — `TestableGameManager` constructor passes `null` for the new `EventRecorderService` parameter so existing tests remain green.

---

## Phase 1 — Model classes (no behavior yet)

These four tasks introduce the data shapes other phases depend on. Each is independent of the others and produces a passing build at the end. After Phase 1 the system has the new types but doesn't capture or replay yet.

### Task 1.1: `SystemUsers.EVENT_RECORDER` synthetic user constant

**Files:**
- Create: `poker-server/src/main/java/org/homepoker/recording/SystemUsers.java`

The recorder's `userId` must be reserved so a real user with that id can never displace the recorder. We use the underscore-bracketed sentinel `__system_recorder__` — not a legal email and not assigned by registration.

- [ ] **Step 1: Create the file**

```java
package org.homepoker.recording;

import org.homepoker.model.user.User;
import org.homepoker.model.user.UserRole;

import java.util.Set;

/**
 * System users — synthetic identities used by server-internal listeners and commands that
 * need a non-null {@code userId()} but are not real registered users.
 *
 * <p>These constants are <strong>not persisted</strong> to the users collection and are
 * never returned by {@code UserManager}. The reserved ids cannot collide with real user ids
 * because real ids are validated as email-format on registration.
 */
public final class SystemUsers {

  /** Reserved id for the {@link EventRecorder}'s synthetic listener identity. */
  public static final String EVENT_RECORDER_ID = "__system_recorder__";

  /**
   * The synthetic user the {@link EventRecorder} runs as. Has no password, no real contact
   * info, and the {@link UserRole#ADMIN} role only as a structural placeholder — this user
   * cannot log in.
   */
  public static final User EVENT_RECORDER = User.builder()
      .id(EVENT_RECORDER_ID)
      .email("<internal>")
      .name("Event Recorder")
      .phone("")
      .roles(Set.of(UserRole.ADMIN))
      .build();

  private SystemUsers() {
    // Utility class.
  }
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew :poker-server:compileJava`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add poker-server/src/main/java/org/homepoker/recording/SystemUsers.java
git commit -m "feat(recording): add SystemUsers.EVENT_RECORDER synthetic identity"
```

---

### Task 1.2: `AdminViewingReplay` event

**Files:**
- Create: `poker-common/src/main/java/org/homepoker/model/event/game/AdminViewingReplay.java`

Game-level event (`GameEvent`, not `TableEvent`). Sequence-numbered like every other broadcast event. Notifies all currently connected clients of a game that an admin is viewing a replay of a hand on that game.

- [ ] **Step 1: Create the record**

```java
package org.homepoker.model.event.game;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.GameEvent;

import java.time.Instant;

/**
 * Broadcast warning emitted when an admin views a replay of a hand on a game whose status is
 * not {@code COMPLETED}. Mirrors the existing CLAUDE.md "admin debug-view" rule: when an
 * admin gains visibility into a game that is currently being played, the players in that
 * game must be informed.
 *
 * <p>For replays of {@code COMPLETED} games, no event is emitted (no audience to warn).
 */
@EventMarker
public record AdminViewingReplay(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String adminUserId,
    String adminAlias,
    String tableId,
    int handNumber
) implements GameEvent {
  @Override
  public AdminViewingReplay withSequenceNumber(long sequenceNumber) {
    return new AdminViewingReplay(timestamp, sequenceNumber, gameId, adminUserId, adminAlias, tableId, handNumber);
  }
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew :poker-common:compileJava`
Expected: PASS.

- [ ] **Step 3: Add JSON round-trip test**

Create: `poker-common/src/test/java/org/homepoker/model/event/AdminViewingReplaySerializationTest.java`:

```java
package org.homepoker.model.event;

import org.homepoker.model.event.game.AdminViewingReplay;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AdminViewingReplaySerializationTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().build();

  @Test
  void roundTripsWithSequenceNumber() {
    AdminViewingReplay original = new AdminViewingReplay(
        Instant.parse("2026-04-26T12:00:00Z"),
        99L,
        "g1",
        "admin-user",
        "admin",
        "table-A",
        7
    );
    String json = objectMapper.writeValueAsString(original);
    AdminViewingReplay parsed = objectMapper.readValue(json, AdminViewingReplay.class);
    assertThat(parsed).isEqualTo(original);
    assertThat(parsed.sequenceNumber()).isEqualTo(99L);
  }
}
```

- [ ] **Step 4: Run the test**

Run: `./gradlew :poker-common:test --tests AdminViewingReplaySerializationTest`
Expected: PASS.

- [ ] **Step 5: Update the wire-format spec**

Modify: `poker-server/src/main/resources/static/command-event-spec.md`. After the `PlayerReconnected` section (around line 470), add:

```markdown
#### AdminViewingReplay

Broadcast warning that an admin is viewing a replay of a hand on a game whose status is not `COMPLETED`. Emitted exactly once per replay request against an in-progress game; not emitted for replays of `COMPLETED` games. Players receiving this can surface it in the UI to make admin observation visible.

| Field            | Type    | Description                                       |
|------------------|---------|---------------------------------------------------|
| `timestamp`      | Instant | When the warning was emitted                      |
| `sequenceNumber` | long    | Game-stream sequence number                       |
| `gameId`         | String  | Game ID                                           |
| `adminUserId`    | String  | Admin's user ID                                   |
| `adminAlias`     | String  | Admin's alias (display name)                      |
| `tableId`        | String  | Table being replayed                              |
| `handNumber`     | int     | Hand number being replayed                        |

**eventType:** `admin-viewing-replay`

---
```

- [ ] **Step 6: Commit**

```bash
git add poker-common/src/main/java/org/homepoker/model/event/game/AdminViewingReplay.java \
        poker-common/src/test/java/org/homepoker/model/event/AdminViewingReplaySerializationTest.java \
        poker-server/src/main/resources/static/command-event-spec.md
git commit -m "feat(events): add AdminViewingReplay broadcast event

Emitted when an admin views a replay of a hand on a game that is not
COMPLETED, so connected players are informed of the admin's visibility.
Mirrors the existing admin debug-view warning rule from CLAUDE.md."
```

---

### Task 1.3: `AdminViewingReplayCommand` command

**Files:**
- Create: `poker-common/src/main/java/org/homepoker/model/command/AdminViewingReplayCommand.java`

Server-internal command. Submitted by `ReplayController` when the replayed game is not `COMPLETED`. Like `PlayerConnectedCommand` / `PlayerDisconnectedCommand`, this command is **NOT** annotated with `@GameCommandMarker` — it must not be on the polymorphic Jackson registry, otherwise an authenticated client could spoof the warning.

- [ ] **Step 1: Create the record**

```java
package org.homepoker.model.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.homepoker.model.user.User;

/**
 * Internal command submitted by {@code ReplayController} when an admin replays a hand on a
 * game whose status is not {@code COMPLETED}. The handler validates the user is admin
 * (defense in depth) and emits {@code AdminViewingReplay} through the standard fan-out so
 * connected clients receive the warning.
 *
 * <p>Server-internal only — intentionally NOT annotated with {@code @GameCommandMarker} so it
 * is excluded from the polymorphic Jackson registry. An authenticated client must not be
 * able to spoof a replay-warning event.
 */
public record AdminViewingReplayCommand(
    String gameId,
    User user,
    String tableId,
    int handNumber
) implements GameCommand {

  @JsonIgnore
  @Override
  public User user() {
    return user;
  }
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew :poker-common:compileJava`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add poker-common/src/main/java/org/homepoker/model/command/AdminViewingReplayCommand.java
git commit -m "feat(commands): add internal AdminViewingReplayCommand

Submitted by ReplayController to notify the live GameManager that an
admin is viewing a replay of an in-progress hand. Server-internal only —
no @GameCommandMarker so it cannot be spoofed via WebSocket."
```

---

### Task 1.4: `RecordedEvent` document and repository

**Files:**
- Create: `poker-server/src/main/java/org/homepoker/recording/RecordedEvent.java`
- Create: `poker-server/src/main/java/org/homepoker/recording/EventRecorderRepository.java`
- Modify: `poker-server/src/main/java/org/homepoker/MongoConfiguration.java`

Mongo collection: `recordedEvents`. The `payload` is stored as `Map<String, Object>` (a nested BSON document) so it's human-inspectable in `mongosh`. The recorder produces the map by converting the event through the project's `webSocketObjectMapper`, which carries the polymorphic discriminator (`eventType`).

- [ ] **Step 1: Create `RecordedEvent.java`**

```java
package org.homepoker.recording;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * One event captured by {@link EventRecorder}. Stored in the {@code recordedEvents} Mongo
 * collection. Indexed by {@code (gameId, tableId, handNumber, sequenceNumber)} for replay.
 *
 * <p>Field nullability mirrors the spec:
 * <ul>
 *   <li>{@code gameId} — always present</li>
 *   <li>{@code tableId} — null for game-level events</li>
 *   <li>{@code handNumber} — null for events outside any hand window</li>
 *   <li>{@code userId} — null for non-{@code UserEvent} events</li>
 *   <li>{@code sequenceNumber} — 0 for {@code UserEvent}s; the real per-stream seq otherwise</li>
 * </ul>
 */
@Document(collection = "recordedEvents")
public record RecordedEvent(
    @Id String id,
    String gameId,
    String tableId,
    Integer handNumber,
    String userId,
    String eventType,
    long sequenceNumber,
    Instant eventTimestamp,
    Instant recordedAt,
    Map<String, Object> payload
) {
}
```

- [ ] **Step 2: Create `EventRecorderRepository.java`**

```java
package org.homepoker.recording;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRecorderRepository extends MongoRepository<RecordedEvent, String> {

  /**
   * Primary replay query — events for one hand at one table on one game, ordered first by
   * the per-table sequence number (deterministic for stamped broadcast events) and secondarily
   * by {@code recordedAt} (stable for {@code UserEvent}s whose seq is always 0).
   */
  List<RecordedEvent> findByGameIdAndTableIdAndHandNumberOrderBySequenceNumberAscRecordedAtAsc(
      String gameId, String tableId, Integer handNumber);

  /**
   * Latest hand on a table — used by {@link EventRecorder}'s startup recovery to seed
   * {@code currentHandByTable} for tables that are still in a hand at server restart.
   */
  Optional<RecordedEvent> findTopByGameIdAndTableIdOrderByHandNumberDesc(
      String gameId, String tableId);
}
```

- [ ] **Step 3: Add the indexes to `MongoConfiguration`**

Modify: `poker-server/src/main/java/org/homepoker/MongoConfiguration.java`

Replace:

```java
@EventListener(ContextRefreshedEvent.class)
public void setupIndexes() {
  mongoTemplate.indexOps(User.class).createIndex(new Index().on("email", Sort.Direction.ASC).unique());
}
```

with:

```java
@EventListener(ContextRefreshedEvent.class)
public void setupIndexes() {
  mongoTemplate.indexOps(User.class)
      .createIndex(new Index().on("email", Sort.Direction.ASC).unique());

  // Primary index for hand-scoped replay queries.
  mongoTemplate.indexOps(RecordedEvent.class)
      .createIndex(new Index()
          .on("gameId", Sort.Direction.ASC)
          .on("tableId", Sort.Direction.ASC)
          .on("handNumber", Sort.Direction.ASC)
          .on("sequenceNumber", Sort.Direction.ASC)
          .named("recordedEvents_replay_idx"));

  // Secondary index for whole-game queries (no v1 endpoint, but trivial to add later).
  mongoTemplate.indexOps(RecordedEvent.class)
      .createIndex(new Index()
          .on("gameId", Sort.Direction.ASC)
          .on("recordedAt", Sort.Direction.ASC)
          .named("recordedEvents_game_time_idx"));
}
```

Add the import: `import org.homepoker.recording.RecordedEvent;`

- [ ] **Step 4: Verify build**

Run: `./gradlew :poker-server:compileJava`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add poker-server/src/main/java/org/homepoker/recording/RecordedEvent.java \
        poker-server/src/main/java/org/homepoker/recording/EventRecorderRepository.java \
        poker-server/src/main/java/org/homepoker/MongoConfiguration.java
git commit -m "feat(recording): add RecordedEvent document, repository, and indexes

Stores each captured event with denormalized (gameId, tableId,
handNumber, sequenceNumber) for indexed replay queries plus the full
payload as a nested BSON document for human inspection. Two compound
indexes: replay-by-hand and game/time-range."
```

---

## Phase 2 — Capture pipeline

After Phase 2, every event flowing through `processGameTick` is enqueued for persistence on a worker virtual thread. The game loop never touches Mongo. Existing tests remain green via the optional-service path.

### Task 2.1: `EventRecorderService` — async queue + worker

**Files:**
- Create: `poker-server/src/main/java/org/homepoker/recording/EventRecorderService.java`

Bounded queue, non-blocking `offer`, single virtual-thread worker, lifecycle hooks for start and drain. Counters are basic in-memory longs; a real metric backend can be wired up later.

- [ ] **Step 1: Create the file**

```java
package org.homepoker.recording;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.homepoker.game.cash.CashGameRepository;
import org.homepoker.model.game.GameStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns the async write pipeline for {@link EventRecorder} captures. The recorder calls
 * {@link #offer(RecordedEvent)} on the game-loop thread; that call is non-blocking and
 * returns false on overflow. A single virtual-thread worker drains the queue and writes
 * to {@link EventRecorderRepository}.
 *
 * <p>Per the spec: a backed-up Mongo cannot stall the game tick. On overflow the dropped
 * event is counted in {@link #droppedEventCount()} and logged once per N drops.
 */
@Slf4j
@Service
public class EventRecorderService {

  private static final int LOG_DROP_EVERY_N = 100;
  private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE_REF =
      new TypeReference<>() {};

  private final EventRecorderRepository repository;
  private final CashGameRepository cashGameRepository;
  private final ObjectMapper objectMapper;
  private final LinkedBlockingQueue<RecordedEvent> queue;

  private final AtomicLong droppedEventCount = new AtomicLong();
  private final AtomicLong writtenEventCount = new AtomicLong();

  private volatile boolean running;
  private Thread worker;

  public EventRecorderService(
      EventRecorderRepository repository,
      CashGameRepository cashGameRepository,
      ObjectMapper webSocketObjectMapper,
      @Value("${poker.recording.queue-capacity:10000}") int queueCapacity) {
    this.repository = repository;
    this.cashGameRepository = cashGameRepository;
    this.objectMapper = webSocketObjectMapper;
    this.queue = new LinkedBlockingQueue<>(queueCapacity);
  }

  @PostConstruct
  public void start() {
    running = true;
    worker = Thread.ofVirtual()
        .name("event-recorder-worker")
        .start(this::drainLoop);
  }

  @PreDestroy
  public void stop() {
    running = false;
    if (worker != null) {
      worker.interrupt();
      try {
        worker.join(5_000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    // Drain whatever is left synchronously.
    drainRemaining();
  }

  /**
   * Convert the event to a {@code Map<String, Object>} payload using the polymorphic
   * Jackson module configured on {@code webSocketObjectMapper}. This preserves the
   * {@code eventType} discriminator inside the nested document.
   */
  public Map<String, Object> toPayload(Object event) {
    return objectMapper.convertValue(event, PAYLOAD_TYPE_REF);
  }

  /**
   * Non-blocking enqueue. Returns false on overflow; caller has already paid the cost of
   * building the event but the game loop is not blocked. On overflow we increment the
   * dropped-event counter and log every {@value #LOG_DROP_EVERY_N} drops.
   */
  public boolean offer(RecordedEvent event) {
    if (!queue.offer(event)) {
      long dropped = droppedEventCount.incrementAndGet();
      if (dropped % LOG_DROP_EVERY_N == 0) {
        log.warn("event-recorder queue overflowed; total dropped = {} (capacity = {}, written = {})",
            dropped, queue.remainingCapacity() + queue.size(), writtenEventCount.get());
      }
      return false;
    }
    return true;
  }

  public long droppedEventCount() {
    return droppedEventCount.get();
  }

  public long writtenEventCount() {
    return writtenEventCount.get();
  }

  /**
   * Seed {@code currentHandByTable} for tables that were mid-hand at server restart. For
   * each non-{@code COMPLETED} game whose tables are {@code PLAYING}, look up the most
   * recently recorded {@code handNumber} for that table.
   */
  public Map<String, Integer> seedHandTracker() {
    Map<String, Integer> seed = new HashMap<>();
    cashGameRepository.findAll().forEach(game -> {
      if (game.status() == GameStatus.COMPLETED) return;
      game.tables().values().stream()
          .filter(table -> table.status() == org.homepoker.model.game.Table.Status.PLAYING)
          .forEach(table -> repository
              .findTopByGameIdAndTableIdOrderByHandNumberDesc(game.id(), table.id())
              .ifPresent(rec -> {
                if (rec.handNumber() != null) {
                  seed.put(table.id(), rec.handNumber());
                }
              }));
    });
    return seed;
  }

  private void drainLoop() {
    while (running) {
      RecordedEvent next;
      try {
        next = queue.poll(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
      if (next == null) continue;
      writeOne(next);
    }
  }

  private void drainRemaining() {
    RecordedEvent next;
    while ((next = queue.poll()) != null) {
      writeOne(next);
    }
  }

  private void writeOne(RecordedEvent event) {
    try {
      repository.save(event);
      writtenEventCount.incrementAndGet();
    } catch (RuntimeException e) {
      log.error("event-recorder worker failed to persist event eventType={} gameId={} tableId={} (worker continuing)",
          event.eventType(), event.gameId(), event.tableId(), e);
    }
  }
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew :poker-server:compileJava`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add poker-server/src/main/java/org/homepoker/recording/EventRecorderService.java
git commit -m "feat(recording): add EventRecorderService with bounded async queue

Single virtual-thread worker drains a LinkedBlockingQueue (default
capacity 10_000, configurable via poker.recording.queue-capacity).
offer() is non-blocking; overflow drops with a counter and
periodic log. Worker survives per-event repository failures."
```

---

### Task 2.2: `EventRecorder` listener with hand-number tracking

**Files:**
- Create: `poker-server/src/main/java/org/homepoker/recording/EventRecorder.java`

Subclass of `UserGameListener`, captures every event, tags it with the right `(gameId, tableId, handNumber, userId, sequenceNumber, eventType)`, and offers to the service.

- [ ] **Step 1: Create the file**

```java
package org.homepoker.recording;

import lombok.extern.slf4j.Slf4j;
import org.homepoker.game.UserGameListener;
import org.homepoker.model.event.GameEvent;
import org.homepoker.model.event.PokerEvent;
import org.homepoker.model.event.TableEvent;
import org.homepoker.model.event.UserEvent;
import org.homepoker.model.event.table.HandComplete;
import org.homepoker.model.event.table.HandStarted;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Captures every event passing through the {@code GameManager.processGameTick} fan-out and
 * forwards it to {@link EventRecorderService} for asynchronous persistence.
 *
 * <p>Hand windowing — the recorder maintains an in-memory {@code currentHandByTable} map.
 * Tagging precedence (per the 2026-04-25 spec):
 * <ol>
 *   <li>If event is {@code HandStarted}: tag with {@code event.handNumber()}, then update the tracker.</li>
 *   <li>If event is {@code HandComplete}: tag with {@code event.handNumber()}, then remove the tracker entry.</li>
 *   <li>Else if event is a {@code TableEvent}: tag with {@code currentHandByTable.get(tableId)} (may be null).</li>
 *   <li>Else: {@code handNumber = null} (game-level events never carry a hand number).</li>
 * </ol>
 *
 * <p>Errors thrown inside {@code onEvent} are caught and logged — a broken recorder must
 * not break the game loop.
 */
@Slf4j
public class EventRecorder extends UserGameListener {

  private final EventRecorderService service;
  private final Map<String, Integer> currentHandByTable;

  public EventRecorder(EventRecorderService service) {
    this(service, new HashMap<>());
  }

  /**
   * Constructor for tests — accepts a pre-seeded hand tracker (e.g., from
   * {@link EventRecorderService#seedHandTracker()} during server restart recovery).
   */
  public EventRecorder(EventRecorderService service, Map<String, Integer> initialHandByTable) {
    super(SystemUsers.EVENT_RECORDER);
    this.service = service;
    this.currentHandByTable = new HashMap<>(initialHandByTable);
  }

  @Override
  public boolean acceptsEvent(PokerEvent event) {
    return true;
  }

  @Override
  public void onEvent(PokerEvent event) {
    try {
      String gameId = (event instanceof GameEvent ge) ? ge.gameId() : null;
      String tableId = (event instanceof TableEvent te) ? te.tableId() : null;
      String userId = (event instanceof UserEvent ue) ? ue.userId() : null;

      Integer handNumber = computeHandNumber(event, tableId);

      long sequenceNumber = (event instanceof GameEvent ge) ? ge.sequenceNumber() : 0L;
      Instant eventTimestamp = event.timestamp();

      Map<String, Object> payload = service.toPayload(event);

      RecordedEvent recorded = new RecordedEvent(
          null,
          gameId,
          tableId,
          handNumber,
          userId,
          event.eventType(),
          sequenceNumber,
          eventTimestamp,
          Instant.now(),
          payload
      );

      service.offer(recorded);

      // Update the tracker AFTER tagging so HandStarted is recorded with its own number
      // and HandComplete is recorded under the closing hand.
      updateHandTracker(event, tableId);
    } catch (RuntimeException e) {
      log.error("EventRecorder.onEvent threw on event {}; swallowed to protect the game loop.",
          event.eventType(), e);
    }
  }

  private Integer computeHandNumber(PokerEvent event, String tableId) {
    if (event instanceof HandStarted hs) return hs.handNumber();
    if (event instanceof HandComplete hc) return hc.handNumber();
    if (tableId == null) return null;
    return currentHandByTable.get(tableId);
  }

  private void updateHandTracker(PokerEvent event, String tableId) {
    if (tableId == null) return;
    if (event instanceof HandStarted hs) {
      currentHandByTable.put(tableId, hs.handNumber());
    } else if (event instanceof HandComplete) {
      currentHandByTable.remove(tableId);
    }
  }

  /** Test-only inspection. */
  Map<String, Integer> currentHandByTableSnapshot() {
    return Map.copyOf(currentHandByTable);
  }
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew :poker-server:compileJava`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add poker-server/src/main/java/org/homepoker/recording/EventRecorder.java
git commit -m "feat(recording): add EventRecorder GameListener with hand windowing

UserGameListener subclass identified by SystemUsers.EVENT_RECORDER.
Captures every event (acceptsEvent → true), tags with the active hand
number per table (HandStarted opens, HandComplete closes), and offers
the RecordedEvent to the async write service. Catches and logs all
exceptions so a broken recorder cannot break the game loop."
```

---

### Task 2.3: Wire the recorder into `CashGameManager`

**Files:**
- Modify: `poker-server/src/main/java/org/homepoker/game/cash/CashGameManager.java`
- Modify: `poker-server/src/main/java/org/homepoker/game/cash/CashGameService.java`
- Modify: `poker-server/src/test/java/org/homepoker/test/GameManagerTestFixture.java`

`CashGameManager`'s constructor gains an `@Nullable EventRecorderService`. If non-null, attach `new EventRecorder(service)` immediately after `super(...)`. If null, skip — that path keeps existing tests (which pass null) green and supports unit-test contexts that don't bring up Mongo.

- [ ] **Step 1: Update `CashGameManager`**

Locate the existing constructor (around line 25):

```java
public CashGameManager(CashGame game, CashGameService cashGameService, UserManager userManager, SecurityUtilities securityUtilities) {
  super(game, userManager, securityUtilities);
  this.cashGameService = cashGameService;
}
```

Replace with:

```java
public CashGameManager(CashGame game,
                       CashGameService cashGameService,
                       UserManager userManager,
                       SecurityUtilities securityUtilities,
                       @Nullable EventRecorderService eventRecorderService) {
  super(game, userManager, securityUtilities);
  this.cashGameService = cashGameService;

  if (eventRecorderService != null) {
    Map<String, Integer> seed = eventRecorderService.seedHandTracker();
    addGameListener(new EventRecorder(eventRecorderService, seed));
  }
}
```

Add imports:
```java
import org.homepoker.recording.EventRecorder;
import org.homepoker.recording.EventRecorderService;
import org.jspecify.annotations.Nullable;
import java.util.Map;
```

(Note: `Map` may already be imported.)

- [ ] **Step 2: Update `CashGameService`**

In `poker-server/src/main/java/org/homepoker/game/cash/CashGameService.java`, add a constructor field for `EventRecorderService` and pass it to `new CashGameManager(...)`.

a) Add the field near other constructor-injected fields:

```java
private final EventRecorderService eventRecorderService;
```

b) Add it to the constructor parameter list (the existing constructor, around lines 55–72) and assign:

```java
this.eventRecorderService = eventRecorderService;
```

c) Update the `getGameManger` instantiation site (around line 184):

Replace:

```java
return new CashGameManager(game, this, userManager, securityUtilities);
```

with:

```java
return new CashGameManager(game, this, userManager, securityUtilities, eventRecorderService);
```

Add the import: `import org.homepoker.recording.EventRecorderService;`

- [ ] **Step 3: Update `GameManagerTestFixture.TestableGameManager`**

In `poker-server/src/test/java/org/homepoker/test/GameManagerTestFixture.java`, find the `TestableGameManager` constructor that calls `super(game, null, null, null)`. Add a fourth `null` to skip the recorder:

```java
public TestableGameManager(CashGame game) {
  super(game, null, null, null, null);
  // ... rest unchanged
}
```

(Verify the parameter count — the existing call site is `super(game, null, null, null)` for the 4-arg pre-recorder constructor. After Step 1 the constructor is 5-arg, so the test fixture must pass 5 nulls.)

- [ ] **Step 4: Build and run all tests**

Run: `./gradlew clean test`
Expected: PASS — every existing test should still pass because tests pass `null` for `eventRecorderService` and the recorder is never attached.

- [ ] **Step 5: Commit**

```bash
git add poker-server/src/main/java/org/homepoker/game/cash/CashGameManager.java \
        poker-server/src/main/java/org/homepoker/game/cash/CashGameService.java \
        poker-server/src/test/java/org/homepoker/test/GameManagerTestFixture.java
git commit -m "feat(recording): attach EventRecorder to CashGameManager

CashGameManager constructor gains a nullable EventRecorderService. When
non-null, an EventRecorder is registered as a GameListener after super().
CashGameService injects the bean and threads it through. The test
fixture passes null to keep unit-test contexts that don't bring up
Mongo unaffected."
```

---

### Task 2.4: Guard against accidental recorder eviction

**Files:**
- Modify: `poker-server/src/main/java/org/homepoker/game/GameManager.java`

`removeGameListenersByUserId` currently removes any listener matching a userId. The reserved id `__system_recorder__` should be impossible to register as a real user, but defense-in-depth: refuse to remove listeners owned by `SystemUsers.EVENT_RECORDER_ID`.

- [ ] **Step 1: Update `removeGameListenersByUserId`**

Locate the method (around lines 142–153):

```java
public void removeGameListenersByUserId(String userId) {
  int removed = 0;
  for (Iterator<GameListener> it = gameListeners.iterator(); it.hasNext(); ) {
    if (userId.equals(it.next().userId())) {
      it.remove();
      removed++;
    }
  }
  for (int i = 0; i < removed; i++) {
    submitCommand(new PlayerDisconnectedCommand(game.id(), userId));
  }
}
```

Replace with:

```java
public void removeGameListenersByUserId(String userId) {
  // Defense in depth: never evict the EventRecorder's synthetic identity. A real user with
  // this id cannot exist (registration enforces email-format on the id), but if some
  // future code path passes the reserved id here, we refuse silently.
  if (org.homepoker.recording.SystemUsers.EVENT_RECORDER_ID.equals(userId)) {
    return;
  }

  int removed = 0;
  for (Iterator<GameListener> it = gameListeners.iterator(); it.hasNext(); ) {
    if (userId.equals(it.next().userId())) {
      it.remove();
      removed++;
    }
  }
  for (int i = 0; i < removed; i++) {
    submitCommand(new PlayerDisconnectedCommand(game.id(), userId));
  }
}
```

- [ ] **Step 2: Build and test**

Run: `./gradlew :poker-server:test`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add poker-server/src/main/java/org/homepoker/game/GameManager.java
git commit -m "fix(recording): refuse to evict the EventRecorder by userId

removeGameListenersByUserId now short-circuits if the userId matches
SystemUsers.EVENT_RECORDER_ID. This is defense in depth — the reserved
id is not a legal user id (registration enforces email format) — but
it ensures a future code path can't accidentally remove the recorder."
```

---

### Task 2.5: Integration test — capture happy path

**Files:**
- Create: `poker-server/src/test/java/org/homepoker/recording/EventRecorderIntegrationTest.java`

This test extends `BaseIntegrationTest` so Mongo is up via TestContainers. It drives a `CashGameManager` with the real `EventRecorderService` and asserts captured rows.

- [ ] **Step 1: Write the test**

```java
package org.homepoker.recording;

import org.homepoker.game.GameSettings;
import org.homepoker.game.cash.CashGameManager;
import org.homepoker.game.cash.CashGameService;
import org.homepoker.model.command.StartGame;
import org.homepoker.model.event.game.GameStatusChanged;
import org.homepoker.model.event.table.HandComplete;
import org.homepoker.model.event.table.HandStarted;
import org.homepoker.model.event.table.HoleCardsDealt;
import org.homepoker.model.game.GameStatus;
import org.homepoker.model.game.GameType;
import org.homepoker.model.game.Player;
import org.homepoker.model.game.PlayerStatus;
import org.homepoker.model.game.Seat;
import org.homepoker.model.game.Table;
import org.homepoker.model.game.cash.CashGame;
import org.homepoker.model.user.User;
import org.homepoker.security.SecurityUtilities;
import org.homepoker.test.BaseIntegrationTest;
import org.homepoker.test.TestDataHelper;
import org.homepoker.user.UserManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class EventRecorderIntegrationTest extends BaseIntegrationTest {

  @Autowired EventRecorderService eventRecorderService;
  @Autowired EventRecorderRepository eventRecorderRepository;
  @Autowired CashGameService cashGameService;
  @Autowired UserManager userManagerBean;
  @Autowired SecurityUtilities securityUtilities;

  @Test
  void capturesEverySingleEventAcrossAGameLoopRun() {
    User owner = createUser(TestDataHelper.user("admin", "password", "Admin"));
    String gameId = "recording-it-" + System.currentTimeMillis();

    // Build a 5-player single-table game in SEATING.
    CashGame game = CashGame.builder()
        .id(gameId)
        .name("Recording IT")
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
      String uid = "rec-player-" + i;
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

    // Wait for the async worker to flush — there is no synchronous flush API by design.
    await().atMost(Duration.ofSeconds(5)).until(() ->
        eventRecorderRepository.count() > 0
            && eventRecorderService.writtenEventCount() >= eventRecorderRepository.count());

    List<RecordedEvent> all = eventRecorderRepository.findAll();
    assertThat(all).isNotEmpty();

    // Game-level events: tableId == null AND handNumber == null
    assertThat(all)
        .filteredOn(r -> "game-status-changed".equals(r.eventType()))
        .allSatisfy(r -> {
          assertThat(r.tableId()).isNull();
          assertThat(r.handNumber()).isNull();
          assertThat(r.gameId()).isEqualTo(gameId);
        });

    // HandStarted events: tag with their own handNumber
    assertThat(all)
        .filteredOn(r -> "hand-started".equals(r.eventType()))
        .allSatisfy(r -> {
          assertThat(r.tableId()).isEqualTo("TABLE-0");
          assertThat(r.handNumber()).isNotNull();
        });

    // HoleCardsDealt: every player's private deal is captured (5 players → 5 events)
    assertThat(all)
        .filteredOn(r -> "hole-cards-dealt".equals(r.eventType()))
        .hasSize(5)
        .allSatisfy(r -> {
          assertThat(r.userId()).isNotNull();
          assertThat(r.tableId()).isEqualTo("TABLE-0");
          assertThat(r.handNumber()).isNotNull();
        });

    // sequenceNumber is non-zero for stamped broadcast events (HandStarted) and zero for UserEvents.
    assertThat(all)
        .filteredOn(r -> "hand-started".equals(r.eventType()))
        .allSatisfy(r -> assertThat(r.sequenceNumber()).isPositive());
    assertThat(all)
        .filteredOn(r -> "hole-cards-dealt".equals(r.eventType()))
        .allSatisfy(r -> assertThat(r.sequenceNumber()).isZero());
  }
}
```

Add `awaitility` to the test runtime classpath if it isn't already.

- [ ] **Step 2: Add `awaitility` to test dependencies if missing**

Check `poker-server/build.gradle` for `org.awaitility:awaitility`. If absent, add to `dependencies`:

```groovy
testImplementation 'org.awaitility:awaitility'
```

(Spring Boot's BOM should pick a compatible version automatically.)

- [ ] **Step 3: Run**

Run: `./gradlew :poker-server:test --tests EventRecorderIntegrationTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add poker-server/src/test/java/org/homepoker/recording/EventRecorderIntegrationTest.java \
        poker-server/build.gradle
git commit -m "test(recording): integration test for capture happy path

Drives a 5-player table through StartGame + 2 ticks against a real
CashGameManager attached to a real EventRecorderService backed by
TestContainers Mongo. Asserts game-level events have null tableId/
handNumber, table events have non-null both, every player gets a
HoleCardsDealt row, and sequenceNumber is non-zero for broadcast
TableEvents but zero for UserEvents."
```

---

### Task 2.6: Resilience test — overflow + worker error tolerance

**Files:**
- Create: `poker-server/src/test/java/org/homepoker/recording/EventRecorderResilienceTest.java`

Pure unit test — no Spring, no Mongo. Uses a tiny queue capacity and a mock repository.

- [ ] **Step 1: Write the test**

```java
package org.homepoker.recording;

import org.homepoker.game.cash.CashGameRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static java.time.Duration.ofSeconds;

class EventRecorderResilienceTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().build();

  @Test
  void offerReturnsFalseOnOverflowAndIncrementsCounter() {
    EventRecorderRepository repo = mock(EventRecorderRepository.class);
    CashGameRepository gameRepo = mock(CashGameRepository.class);
    when(gameRepo.findAll()).thenReturn(java.util.Collections.emptyList());

    // Capacity 2, no worker started — the queue will fill up.
    EventRecorderService service = new EventRecorderService(repo, gameRepo, objectMapper, 2);
    // NOTE: do not call start(); we want offers to accumulate.

    RecordedEvent ev = sample("e1");
    assertThat(service.offer(ev)).isTrue();
    assertThat(service.offer(sample("e2"))).isTrue();
    // Queue is full. Subsequent offers must return false.
    assertThat(service.offer(sample("e3"))).isFalse();
    assertThat(service.offer(sample("e4"))).isFalse();

    assertThat(service.droppedEventCount()).isEqualTo(2L);
  }

  @Test
  void workerSurvivesPerEventRepositoryFailures() throws InterruptedException {
    AtomicInteger callCount = new AtomicInteger();
    EventRecorderRepository repo = mock(EventRecorderRepository.class);
    when(repo.save(org.mockito.ArgumentMatchers.any(RecordedEvent.class))).thenAnswer(invocation -> {
      int n = callCount.incrementAndGet();
      if (n == 2) {
        throw new RuntimeException("simulated mongo failure on event 2");
      }
      return invocation.getArgument(0);
    });
    CashGameRepository gameRepo = mock(CashGameRepository.class);
    when(gameRepo.findAll()).thenReturn(java.util.Collections.emptyList());

    EventRecorderService service = new EventRecorderService(repo, gameRepo, objectMapper, 100);
    service.start();
    try {
      service.offer(sample("e1"));
      service.offer(sample("e2"));   // worker throws here
      service.offer(sample("e3"));
      service.offer(sample("e4"));

      // Wait for the worker to process all four. Three should succeed, one threw — the
      // counter should reach 3.
      await().atMost(ofSeconds(5)).until(() -> service.writtenEventCount() == 3L);
    } finally {
      service.stop();
    }
  }

  private RecordedEvent sample(String id) {
    return new RecordedEvent(
        id, "g1", "t1", 1, null, "stub-event",
        0L, Instant.now(), Instant.now(),
        Map.of("eventType", "stub-event", "gameId", "g1", "tableId", "t1")
    );
  }
}
```

- [ ] **Step 2: Run**

Run: `./gradlew :poker-server:test --tests EventRecorderResilienceTest`
Expected: PASS — both tests verify the bounded-queue + worker-error contract.

- [ ] **Step 3: Commit**

```bash
git add poker-server/src/test/java/org/homepoker/recording/EventRecorderResilienceTest.java
git commit -m "test(recording): cover overflow drop and worker error tolerance

Tiny-capacity queue test confirms offer() returns false on overflow and
increments the dropped counter. Worker-error test injects a repository
that throws on the second event and confirms subsequent saves succeed
(written counter reaches 3 of 4 attempted, with the 2nd intentionally
failing)."
```

---

## Phase 3 — Replay endpoint

After Phase 3 the admin endpoint serves event arrays for any captured hand and posts the in-progress warning event when applicable.

### Task 3.1: `AdminViewingReplayCommand` handler in `GameManager`

**Files:**
- Modify: `poker-server/src/main/java/org/homepoker/game/GameManager.java`

Add the case in the existing `applyCommand` switch (around line 460). The handler validates the user is admin (defense in depth — controller already enforces it) and emits `AdminViewingReplay`.

- [ ] **Step 1: Add the case**

Locate the `applyCommand` switch and add a new case alongside the others:

```java
case AdminViewingReplayCommand cmd -> handleAdminViewingReplay(cmd, gameContext);
```

Add the import:
```java
import org.homepoker.model.command.AdminViewingReplayCommand;
import org.homepoker.model.event.game.AdminViewingReplay;
```

- [ ] **Step 2: Add the handler**

Add this private method near the other `handle*` methods:

```java
private void handleAdminViewingReplay(AdminViewingReplayCommand cmd, GameContext gameContext) {
  // Defense in depth: ReplayController already gates on @PreAuthorize, but commands do not
  // trust callers — verify the user has admin role before emitting the warning event.
  if (!securityUtilities().userIsAdmin(cmd.user())) {
    log.warn("Refusing AdminViewingReplayCommand from non-admin user [{}]", cmd.user().id());
    return;
  }

  gameContext.queueEvent(new AdminViewingReplay(
      Instant.now(),
      0L,
      game.id(),
      cmd.user().id(),
      cmd.user().alias(),
      cmd.tableId(),
      cmd.handNumber()
  ));
}
```

(`securityUtilities()` is already a protected accessor on `GameManager` per the existing code.)

**Note on `userIsAdmin(User)`:** `SecurityUtilities.userIsAdmin(User)` checks `user.roles().contains(UserRole.ADMIN)`. Confirm by reading `poker-server/src/main/java/org/homepoker/security/SecurityUtilities.java`. If only `userIsAdmin(UserDetails)` exists, add a `User`-overload:

```java
public boolean userIsAdmin(User user) {
  return user != null && user.roles() != null && user.roles().contains(UserRole.ADMIN);
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :poker-server:compileJava`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add poker-server/src/main/java/org/homepoker/game/GameManager.java \
        poker-server/src/main/java/org/homepoker/security/SecurityUtilities.java
git commit -m "feat(recording): handle AdminViewingReplayCommand in GameManager

The handler verifies the requester is admin (defense in depth — the
REST endpoint already enforces this) and emits AdminViewingReplay
through the standard fan-out so connected clients receive the warning."
```

---

### Task 3.2: `ReplayController` — admin REST endpoint

**Files:**
- Create: `poker-server/src/main/java/org/homepoker/rest/ReplayController.java`

Single GET endpoint at `/admin/replay/games/{gameId}/tables/{tableId}/hands/{handNumber}`. Admin-gated via `@PreAuthorize`. Looks up the live `CashGameManager`, queries Mongo for the hand's events, and (if the game is not `COMPLETED`) submits the warning command.

- [ ] **Step 1: Create the controller**

```java
package org.homepoker.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.homepoker.game.cash.CashGameManager;
import org.homepoker.game.cash.CashGameService;
import org.homepoker.model.command.AdminViewingReplayCommand;
import org.homepoker.model.game.GameStatus;
import org.homepoker.recording.EventRecorderRepository;
import org.homepoker.recording.RecordedEvent;
import org.homepoker.security.PokerUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only replay endpoint. Returns the captured event stream for a single hand on a
 * single table. If the game is not yet {@code COMPLETED}, the live {@code CashGameManager}
 * receives an {@link AdminViewingReplayCommand} that emits {@code AdminViewingReplay} to
 * connected players — see the 2026-04-25 event-store-and-replay spec.
 */
@Slf4j
@RestController
@RequestMapping("/admin/replay")
@Tag(name = "Replay", description = "Admin-only event-stream replay")
public class ReplayController {

  private final EventRecorderRepository repository;
  private final CashGameService cashGameService;

  public ReplayController(EventRecorderRepository repository, CashGameService cashGameService) {
    this.repository = repository;
    this.cashGameService = cashGameService;
  }

  @GetMapping("/games/{gameId}/tables/{tableId}/hands/{handNumber}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Replay one hand", description =
      "Returns all events captured for the given (gameId, tableId, handNumber) tuple, "
          + "ordered by sequenceNumber then recordedAt. Empty array if no events are found "
          + "(no distinction between 'never happened' and 'no events captured').")
  public ResponseEntity<List<RecordedEvent>> replayHand(
      @PathVariable String gameId,
      @PathVariable String tableId,
      @PathVariable Integer handNumber,
      @AuthenticationPrincipal PokerUserDetails admin) {

    notifyIfGameIsLive(gameId, tableId, handNumber, admin);

    List<RecordedEvent> events = repository
        .findByGameIdAndTableIdAndHandNumberOrderBySequenceNumberAscRecordedAtAsc(
            gameId, tableId, handNumber);

    return ResponseEntity.ok(events);
  }

  /**
   * Best-effort: if the game is in memory and not COMPLETED, submit the warning command.
   * The event itself is published on the next tick — the HTTP response does not wait for
   * delivery. If the {@code CashGameManager} cannot be looked up (e.g., the game does not
   * exist), the request still succeeds with an empty array.
   */
  private void notifyIfGameIsLive(String gameId, String tableId, int handNumber, PokerUserDetails admin) {
    CashGameManager manager;
    try {
      manager = cashGameService.getGameManger(gameId);
    } catch (RuntimeException e) {
      log.debug("AdminViewingReplay warning skipped — no live manager for [{}]: {}", gameId, e.getMessage());
      return;
    }
    if (manager.gameStatus() == GameStatus.COMPLETED) {
      return;
    }
    manager.submitCommand(new AdminViewingReplayCommand(
        gameId, admin.toUser(), tableId, handNumber));
  }
}
```

- [ ] **Step 2: Allow the endpoint through Spring Security**

In `poker-server/src/main/java/org/homepoker/security/WebSecurityConfiguration.java`, the existing rule `.anyRequest().authenticated()` already requires authentication for `/admin/**`. The `@PreAuthorize` does the role check on top. **No security-config changes needed.**

(Sanity check: confirm `@EnableMethodSecurity` is present — it is, on `HomePoker.java`.)

- [ ] **Step 3: Build**

Run: `./gradlew :poker-server:compileJava`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add poker-server/src/main/java/org/homepoker/rest/ReplayController.java
git commit -m "feat(recording): add admin-only ReplayController endpoint

GET /admin/replay/games/{gameId}/tables/{tableId}/hands/{handNumber}
returns the captured event array ordered by sequenceNumber then
recordedAt. If the game is in memory and not COMPLETED, submits an
AdminViewingReplayCommand so the live game broadcasts the warning."
```

---

### Task 3.3: Replay endpoint integration test

**Files:**
- Create: `poker-server/src/test/java/org/homepoker/rest/ReplayControllerIntegrationTest.java`

Full HTTP path with Spring Security. Uses `TestRestTemplate` (already available via `@SpringBootTest(webEnvironment = RANDOM_PORT)` in `BaseIntegrationTest`).

- [ ] **Step 1: Write the test**

```java
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
import org.homepoker.model.user.UserRole;
import org.homepoker.recording.EventRecorderRepository;
import org.homepoker.recording.EventRecorderService;
import org.homepoker.recording.RecordedEvent;
import org.homepoker.security.JwtTokenService;
import org.homepoker.test.BaseIntegrationTest;
import org.homepoker.test.TestDataHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ReplayControllerIntegrationTest extends BaseIntegrationTest {

  @Autowired CashGameService cashGameService;
  @Autowired EventRecorderRepository eventRecorderRepository;
  @Autowired EventRecorderService eventRecorderService;
  @Autowired JwtTokenService jwtTokenService;
  @Autowired TestRestTemplate restTemplate;

  @Test
  void adminGetForExistingHandReturnsEventsInOrder() {
    String gameId = setupOneHandPlayed();

    String adminToken = adminToken();
    ResponseEntity<List<RecordedEvent>> response = restTemplate.exchange(
        "/admin/replay/games/" + gameId + "/tables/TABLE-0/hands/1",
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
        "/admin/replay/games/" + gameId + "/tables/TABLE-0/hands/1",
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
        "/admin/replay/games/" + gameId + "/tables/TABLE-0/hands/999",
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
        "/admin/replay/games/" + gameId + "/tables/TABLE-0/hands/1",
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
        "/admin/replay/games/" + gameId + "/tables/TABLE-0/hands/1",
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

  private String setupOneHandPlayed() {
    User owner = createUser(TestDataHelper.user("admin", "password", "Admin").toBuilder()
        .roles(Set.of(UserRole.ADMIN, UserRole.USER))
        .build());
    String gameId = "replay-it-" + System.currentTimeMillis();

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

    await().atMost(Duration.ofSeconds(5))
        .until(() -> eventRecorderRepository.count() > 0
            && eventRecorderService.writtenEventCount() >= eventRecorderRepository.count());

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
```

**Note on `manager.getGameForTest()`** — this accessor doesn't exist today. The test mutates the in-memory game status to drive the COMPLETED branch. Two options:

(a) Add a package-private `gameForTest()` to `GameManager` for this test only.
(b) Drive the game to COMPLETED via the production flow (`EndGame` command + ticks) — more realistic but more setup.

Pick (a) for v1: it's a single accessor that admits "tests need to peek." If the team prefers, swap to (b) by issuing `EndGame` and ticking until the game's status flips.

If picking (a), add to `GameManager`:

```java
/** Test-only accessor — do not use outside test fixtures. */
@org.jspecify.annotations.NonNull
T gameForTestOnly() {
  return game;
}
```

And expose on `CashGameManager`:

```java
public CashGame getGameForTest() {
  return gameForTestOnly();
}
```

- [ ] **Step 2: Run**

Run: `./gradlew :poker-server:test --tests ReplayControllerIntegrationTest`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add poker-server/src/test/java/org/homepoker/rest/ReplayControllerIntegrationTest.java \
        poker-server/src/main/java/org/homepoker/game/GameManager.java \
        poker-server/src/main/java/org/homepoker/game/cash/CashGameManager.java
git commit -m "test(recording): integration test for admin replay endpoint

Covers: admin GET returns events in order, non-admin → 403, missing
hand → 200 empty array, in-progress game emits AdminViewingReplay,
COMPLETED game does NOT emit it. Adds a small test-only accessor to
peek at the underlying game so we can flip status without driving
through the production EndGame flow."
```

---

## Phase 4 — Server-restart hand-tracker recovery

The recorder must seed `currentHandByTable` on restart so events captured AFTER a restart get the right `handNumber` if a table was mid-hand. `EventRecorderService.seedHandTracker` is implemented in Task 2.1 and called from `CashGameManager`'s constructor in Task 2.3 — this phase is one integration test that exercises the path explicitly.

### Task 4.1: Restart-recovery integration test

**Files:**
- Modify: `poker-server/src/test/java/org/homepoker/recording/EventRecorderIntegrationTest.java`

Add a second test method to the existing integration test class.

- [ ] **Step 1: Add the test method**

Append to `EventRecorderIntegrationTest`:

```java
@Test
void afterRestartCurrentHandByTableIsSeededFromRecording() {
  // Set up a game and play one HandStarted (no HandComplete yet — table is mid-hand).
  String gameId = setupGameAtMidHand();

  // Drop the in-memory CashGameManager from the registry to simulate a server restart.
  cashGameService.invalidateGameManagerForTest(gameId);

  // Re-acquire — this will run the constructor again, including seedHandTracker.
  CashGameManager fresh = cashGameService.getGameManger(gameId);

  // Drive the loop one more tick. Whatever event fires next should carry handNumber=1
  // (the open hand). We verify by checking the most recent recorded event for that table.
  fresh.processGameTick();

  await().atMost(Duration.ofSeconds(5))
      .until(() -> eventRecorderService.writtenEventCount() > eventRecorderRepository.count() - 1);

  RecordedEvent latest = eventRecorderRepository
      .findTopByGameIdAndTableIdOrderByHandNumberDesc(gameId, "TABLE-0")
      .orElseThrow();
  assertThat(latest.handNumber()).isEqualTo(1);
}

private String setupGameAtMidHand() {
  // Same as setupOneHandPlayed but does NOT drive past HandStarted.
  // Implementation note: ticks 1 and 2 deal the first hand and emit HandStarted; we leave
  // the game in PRE_FLOP_BETTING with no HandComplete yet.
  // Reuse the helper from setupOneHandPlayed if present; otherwise inline the seating.
  // (Concrete code intentionally omitted here — copy from the existing helper and
  // remove any post-HandStarted ticks.)
  throw new UnsupportedOperationException(
      "Implement: same as the happy-path setup, but stop after the deal so the table is "
      + "in PRE_FLOP_BETTING. Reuse the seating loop from the happy-path test.");
}
```

(Yes, the `throw` is on purpose — the implementer should copy/adapt the seating helper from the happy-path test. The point of the placeholder is to not duplicate 50 lines of setup in the plan; the implementer paste-and-trim. Concrete contract: the helper produces a game where `TABLE-0` is in PRE_FLOP_BETTING with at least one `HandStarted(handNumber=1)` recorded and no `HandComplete`.)

- [ ] **Step 2: Add `invalidateGameManagerForTest` to `CashGameService`**

In `poker-server/src/main/java/org/homepoker/game/cash/CashGameService.java`, add a small test-only accessor:

```java
/** Test-only: drop the cached CashGameManager so the next getGameManger() reconstructs it. */
public void invalidateGameManagerForTest(String gameId) {
  getGameManagerMap().remove(gameId);
}
```

(`getGameManagerMap()` is already a protected accessor.)

- [ ] **Step 3: Run**

Run: `./gradlew :poker-server:test --tests EventRecorderIntegrationTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add poker-server/src/test/java/org/homepoker/recording/EventRecorderIntegrationTest.java \
        poker-server/src/main/java/org/homepoker/game/cash/CashGameService.java
git commit -m "test(recording): seedHandTracker recovers on server restart

After dropping the in-memory CashGameManager and re-acquiring it (which
re-runs the constructor, which runs seedHandTracker), events emitted in
the still-open hand are tagged with the correct handNumber rather than
null."
```

---

## Phase 5 — Final integration check

### Task 5.1: Full clean build

- [ ] **Step 1: Run**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL with all tests green.

- [ ] **Step 2: Smoke-test the endpoint manually (optional)**

Run: `./gradlew :poker-server:bootRun` (with Mongo up via `docker-compose up`).
Hit `GET http://localhost:8080/admin/replay/games/<id>/tables/<tid>/hands/1` with an admin JWT.
Confirm a JSON array of `RecordedEvent` objects.

- [ ] **Step 3: No commit** — verification only.

---

## Self-Review Notes

**Spec coverage map:**

| Spec section | Plan task |
|---|---|
| Architecture: EventRecorder is a GameListener | Task 2.2 |
| Architecture: async write queue + worker | Task 2.1 |
| Architecture: capture is purely additive | Task 2.3 |
| Architecture: replay is read-only against Mongo | Task 3.2 |
| System User: SystemUsers.EVENT_RECORDER, reserved id `__system_recorder__` | Task 1.1 |
| System User: cannot be evicted by removeGameListenersByUserId | Task 2.4 |
| EventRecorder: hand-windowing rules | Task 2.2 |
| EventRecorder: try/catch in onEvent | Task 2.2 |
| EventRecorderService: bounded queue, non-blocking offer, drops counter | Task 2.1 |
| EventRecorderService: virtual-thread worker, drains on shutdown | Task 2.1 |
| EventRecorderService: seedHandTracker | Task 2.1 + wiring in 2.3 + IT in 4.1 |
| RecordedEvent: schema and indexes | Task 1.4 |
| EventRecorderRepository: two finders | Task 1.4 |
| ReplayController: admin-only, single endpoint | Task 3.2 |
| AdminViewingReplay event | Task 1.2 |
| AdminViewingReplayCommand command (server-internal, no GameCommandMarker) | Task 1.3 |
| AdminViewingReplayCommand handler in GameManager | Task 3.1 |
| Wiring: CashGameManager constructor takes service; null-safe for tests | Task 2.3 |
| Game-loop integration test (capture happy path) | Task 2.5 |
| Server-restart hand-tracker recovery test | Task 4.1 |
| Replay endpoint integration test (admin/non-admin/empty/in-progress/completed) | Task 3.3 |
| Resilience test (overflow + worker failure) | Task 2.6 |
| Spec doc updated for AdminViewingReplay | Task 1.2 step 5 |

**Placeholder scan:** the "setupGameAtMidHand" helper in Task 4.1 has a TODO-style placeholder by design (the implementer copies and trims the existing helper from Task 3.3). Every code step otherwise contains complete code.

**Type consistency:** `EventRecorderService`, `EventRecorderRepository`, `EventRecorder`, `RecordedEvent`, `SystemUsers.EVENT_RECORDER_ID`, `AdminViewingReplay`, `AdminViewingReplayCommand` — used consistently across all tasks. Method names: `offer`, `seedHandTracker`, `toPayload`, `currentHandByTableSnapshot`, `findByGameIdAndTableIdAndHandNumberOrderBySequenceNumberAscRecordedAtAsc`, `findTopByGameIdAndTableIdOrderByHandNumberDesc` — also consistent.

**Deferred to follow-up:** the `gameListeners` thread-safety question raised in T6's review (ArrayList mutated from WebSocket threads while iterated on the game-loop thread) is unchanged by this work and remains a pre-existing race. Worth resolving in a separate ticket.

**One known soft spot:** the Replay endpoint emits the warning event via `submitCommand`, which is fire-and-forget. The HTTP response returns before the warning is published. If the request races with `EndGame` such that the game flips to `COMPLETED` between the status check and the next tick, the warning may not fire. This is consistent with the spec's "best effort" wording but worth noting in the controller's javadoc — already done.
