# Event Store and Replay — Design

**Date:** 2026-04-25
**Status:** Design, pending implementation plan

## Problem

When a bug surfaces in game or table state, today's only forensic tools are server logs and the persisted `Game` document — neither of which captures the ordered sequence of events that produced the state. An admin cannot retroactively ask "what events fired during hand 3 on table 1?" without reproducing the bug live.

A persistent record of the event stream — written as events fan out, queryable after the fact — would let an admin scrub through any past hand to diagnose state issues.

## Goals

- Capture every event emitted by `GameManager` to a persistent store, indexed enough to answer hand-scoped queries.
- Expose a single admin-only REST endpoint for v1: "give me the events for hand N on table T in game G" as a JSON array.
- Add no synchronization or latency to the game loop. Capture must not block, and recorder failures must not break game logic.
- Preserve the existing security invariant: hole cards are visible only to their owner, and now also to admins viewing recordings — with a runtime warning broadcast to active players when an admin views a replay of a still-in-progress game.

## Non-Goals

- **Command replay** — re-executing captured commands against a fresh `GameManager` to recreate state or fork the timeline. Requires a seedable deck and is a much larger project. v1 is observation replay only.
- **State reconstruction** — rebuilding `Game`/`Table`/`Seat` objects from the event stream as of an arbitrary point in time. Adds complexity without addressing the immediate debugging pain.
- **Per-player perspective replay** — viewing a hand "as Alice saw it" with `UserEvent`s filtered to her. v1 is god-view only; the recording captures everything, but the v1 endpoint does not project per-viewer.
- **WebSocket-driven live replay** — re-pushing captured events through a WebSocket so the existing client UI renders them. The captured data supports this, but v1 ships a JSON-dump REST endpoint only. WebSocket replay is a v2 follow-up.
- **Time-range or cross-hand query endpoints** — the storage schema supports them (rich metadata on each row), but v1 exposes only the hand-scoped endpoint.
- **Retention policy / TTL** — recordings are kept indefinitely. A TTL index can be added later if storage growth ever becomes a concern.
- **Admin scrubbing UI** — no client work beyond what's needed to honor the broadcast warning event.

## Design

### Architecture

A new `EventRecorder` is a `GameListener` implementation that accepts every event passing through `GameManager.tick()`'s fan-out loop (`GameManager.java:184–194`) and writes each one to a MongoDB collection. The recorder is attached at `GameManager` construction time alongside the existing listeners, and runs synchronously inside the fan-out — but its actual Mongo writes are pushed to a bounded async queue drained by a worker virtual thread.

Replay is a single admin REST endpoint that queries the collection by `(gameId, tableId, handNumber)` and returns the captured events as a JSON array, ordered by sequence number.

**Game logic does not change.** The capture path is purely additive — one new listener, one new write path. The replay path is read-only against the recording.

The existing event-sequencing spec (2026-04-17), once implemented, gives stable per-stream ordinals on broadcast events that the recorder stores alongside each event. Until that spec lands, the recorder writes `sequenceNumber = 0` and replay falls back to ordering by `recordedAt` then `_id`. The schema is forward-compatible.

### System User for the Recorder

The existing `GameListener` contract requires a non-null `userId()`. Rather than relax the contract, define a synthetic `User` constant (`SystemUsers.EVENT_RECORDER`) with a stable, reserved id (`__system_recorder__`) that cannot collide with any real user id. The constant is **not persisted** to the users collection and is never returned by `UserManager`.

`EventRecorder extends UserGameListener` and is constructed with that `User`. Overrides `acceptsEvent → true` (it captures everything, including `UserEvent`s addressed to other users).

The reserved id ensures `addGameListener`'s `removeGameListenersByUserId(listener.userId())` call (`GameManager.java:96`) can never accidentally evict the recorder when a real user connects.

### Components

All new server-side code lives in a new package `org.homepoker.recording` under `poker-server`, except `AdminViewingReplay` which lives in `poker-common` so clients can deserialize it.

**`SystemUsers`** — holds the `EVENT_RECORDER` constant `User`. Tiny class, lives next to the recorder.

**`EventRecorder extends UserGameListener`** — the capture listener.

- Constructor takes the `EventRecorderService` (defined below) it submits to.
- `acceptsEvent → true`.
- `onEvent(event)`:
  - Determine the `handNumber` to tag this event with:
    - If `event instanceof HandStarted`: use `event.handNumber()` directly.
    - Else if `event instanceof HandComplete`: use `event.handNumber()` directly.
    - Else if event has a `tableId`: look up `currentHandByTable[tableId]` (may be null).
    - Else: `null` (game-level events never carry a hand number).
  - Build a `RecordedEvent` and call `service.offer(recordedEvent)`.
  - **After** building/offering, update the tracker:
    - If `HandStarted`: `currentHandByTable[tableId] = event.handNumber()`.
    - If `HandComplete`: `currentHandByTable.remove(tableId)`.
  - The whole body is wrapped in try/catch — exceptions are logged, never propagated to the fan-out.
- In-memory `Map<String, Integer> currentHandByTable`, seeded on startup from Mongo (see "Server restart with active hands" below).

**`EventRecorderService`** — Spring-managed singleton that owns the async write queue and the worker virtual thread.

- Constructor takes `EventRecorderRepository` and bounded queue capacity (configurable, default 10_000).
- `offer(RecordedEvent) → boolean` — non-blocking. Returns false on overflow; caller increments the dropped-event metric.
- On startup (`@PostConstruct`): start a single virtual thread (via `VirtualThreadManager`) that drains the queue and calls `repository.save(...)` for each event. Worker logs and continues on per-event failures.
- On shutdown (`@PreDestroy`): stop accepting new offers, drain the queue, then return.
- Provides the seeding query for `EventRecorder` startup recovery: `seedHandTracker(gameRepository) → Map<tableId, handNumber>`. Implementation: enumerate non-`COMPLETED` games, and for each table whose status is `PLAYING` (table is mid-hand), query `repository.findTopByGameIdAndTableIdOrderByHandNumberDesc(...)`. Tables not currently in a hand are deliberately omitted from the seed map so their next event gets `handNumber = null` until the next `HandStarted`.

**`RecordedEvent`** — MongoDB document. Fields:

- `id` (ObjectId)
- `gameId` (String, always present)
- `tableId` (String, nullable — null for game-level events with no table context)
- `handNumber` (Integer, nullable — null for events outside any hand window)
- `userId` (String, nullable — set only for `UserEvent`s)
- `eventType` (String, denormalized from `event.eventType()`)
- `sequenceNumber` (long; 0 for `UserEvent`s and for any event today before the sequence-number spec lands)
- `eventTimestamp` (Instant — the event's own `timestamp()`)
- `recordedAt` (Instant — set at write time)
- `payload` (the full event serialized to JSON via the existing `PokerEvent.pokerEventModule()`)

Compound indexes:

- `(gameId, tableId, handNumber, sequenceNumber)` — primary index for hand-scoped replay.
- `(gameId, recordedAt)` — for whole-game queries (no v1 endpoint, but trivial to add).

**`EventRecorderRepository extends MongoRepository<RecordedEvent, String>`** — Spring Data. Methods:

- `findByGameIdAndTableIdAndHandNumberOrderBySequenceNumberAscRecordedAtAsc(...)` — primary replay query (secondary sort by `recordedAt` keeps ordering stable while `sequenceNumber` is still `0` everywhere).
- `findTopByGameIdAndTableIdOrderByHandNumberDesc(...)` — startup recovery for `currentHandByTable`.

**`ReplayController`** (under existing `rest/` package) — admin-only REST controller.

- `GET /admin/replay/games/{gameId}/tables/{tableId}/hands/{handNumber}` → `ResponseEntity<List<RecordedEvent>>`.
- `@PreAuthorize` admin role check.
- Before returning, looks up the game's status. If status is anything other than `COMPLETED`, submits an `AdminViewingReplayCommand` to the live `GameManager` so the warning event flows through the standard fan-out.

**`AdminViewingReplay`** (new `GameEvent` in `poker-common/model/event/game/`) — broadcast event. Carries `gameId`, `adminUserId`, `adminAlias`, `tableId`, `handNumber`. Routed through the standard fan-out so all currently connected clients of the game receive it. Use the existing `add-event-type` skill to scaffold.

**`AdminViewingReplayCommand`** (new `GameCommand` in `poker-common/model/command/`) — submitted by `ReplayController` when the replayed game is not `COMPLETED`. Handler in `GameManager` validates that `command.user()` is admin (defense in depth — controller already enforces this, but commands do not trust callers) and emits `AdminViewingReplay` via `gameContext.queueEvent(...)`. Using a command keeps the existing single-writer invariant (only the game loop emits events) intact.

### Data Flow

**Capture (per game tick):**

```
GameManager.tick()
  applyCommand(...) → gameContext.queueEvent(...)
  fan-out loop (lines 184-194):
    for each event:
      for each listener (recorder is one of them):
        if listener.acceptsEvent(event): listener.onEvent(event)
        EventRecorder.onEvent(event):
          - track hand boundaries (HandStarted / HandComplete)
          - build RecordedEvent
          - offer() to async write queue
              ↓ (separate worker thread, not the game loop)
              drain queue → repository.save(...)
```

The game loop never touches Mongo. The worker is a single virtual thread that owns the queue. On overflow (queue full), `offer` returns false — increment a `recorder.dropped.count` metric and log once per N drops to avoid log spam. A backed-up Mongo cannot stall the game.

**Replay:**

```
GET /admin/replay/games/{gameId}/tables/{tableId}/hands/{handNumber}
  ReplayController:
    Spring Security: admin role check
    look up game.status() (via GameManager registry or game repository)
    if status != COMPLETED:
      gameManager.submitCommand(new AdminViewingReplayCommand(adminUser, gameId, tableId, handNumber))
    return repository.findByGameIdAndTableIdAndHandNumberOrderBy...(gameId, tableId, handNumber)
```

The warning event is best-effort: it's submitted to the queue and processed on the next tick, but the HTTP response does not wait for the warning to be published. The admin's data is returned regardless.

### Wiring

The recorder is attached when a `GameManager` is constructed. Concretely: `CashGameManager`'s constructor (and any future `GameManager` subclass's constructor) takes the `EventRecorderService` as a constructor argument and calls `addGameListener(new EventRecorder(service))` after `super(...)`.

`EventRecorderService` is a Spring `@Service` singleton, lifecycle-managed by Spring (`@PostConstruct` starts the worker, `@PreDestroy` drains and stops it).

For unit tests that don't bring up Mongo, provide a no-op `EventRecorderService` via test configuration (or set the queue to drop on offer when Mongo is unavailable). Integration tests extending `BaseIntegrationTest` use the real service against the TestContainers Mongo instance.

### Error Handling and Edge Cases

- **Mongo unavailable / slow.** Bounded queue absorbs the burst; on overflow, drop with metric. Game tick continues.
- **Recorder throws.** `EventRecorder.onEvent` body is wrapped in try/catch. Exceptions are logged and swallowed. A broken recorder must not break the game.
- **Sequence numbers absent (today).** Until spec 2026-04-17 lands, broadcast events do not carry a `sequenceNumber`. The recorder writes `0` for every event. Replay queries fall back to `recordedAt` then `_id` for ordering. When the upstream spec ships, the recorder picks up the field automatically and stored ordering becomes deterministic.
- **Server restart with active hands.** On `EventRecorder` startup, call `EventRecorderService.seedHandTracker(...)`, which enumerates non-`COMPLETED` games via the game repository and, for each table whose status is `PLAYING` (mid-hand), looks up the latest `handNumber` from the recordings collection. Tables not currently in a hand are deliberately not seeded — their next event correctly gets `handNumber = null` until the next `HandStarted`.
- **Game deleted / table deleted.** Recorded events are not deleted with them. The recording outlives the game. Cleanup is out of scope for v1.
- **Replay endpoint hit on a hand that doesn't exist.** Return `200 OK` with an empty array. "No events captured" and "this hand never happened" are indistinguishable at the storage layer; an empty array is a stable, no-retry-required response.
- **`AdminViewingReplay` warning when `GameManager` is gone.** If status is `COMPLETED`, skip the warning (per design). For `PAUSED`/`ACTIVE`/`SEATING`/`SCHEDULED`/`BALANCING`, the `GameManager` is in memory; if for some reason it isn't (process state inconsistency), log a warning server-side and proceed with the read — better to serve the data than silently fail the request.
- **Recorder evicted by `removeGameListenersByUserId`.** Cannot happen by design: the reserved id `__system_recorder__` is rejected as a real user id at registration time. (Defense in depth: add an explicit guard in `removeGameListenersByUserId` that refuses to remove listeners owned by `SystemUsers.EVENT_RECORDER`.)

### Security

- **Admin-only endpoint.** `ReplayController` is gated by `@PreAuthorize` requiring admin role.
- **Recordings contain hole cards.** The recorder captures `UserEvent`s including `HoleCardsDealt`. This is not a new exposure surface relative to live game state, which already persists hole cards in MongoDB while a hand is in progress — but the recording retains them indefinitely. No additional encryption is applied; the existing Mongo access controls protect the collection.
- **Broadcast warning rule extension.** When an admin opens a replay endpoint for a game whose status is not `COMPLETED`, the server emits an `AdminViewingReplay` event broadcast to all connected clients of that game. This extends the spirit of the existing CLAUDE.md admin debug-view rule: when an admin gains visibility into a game that is *currently being played*, the players in that game must be informed. Replays of `COMPLETED` games are silent (no audience to warn).
- **System user is not a real user.** `SystemUsers.EVENT_RECORDER` is a code constant, not persisted, never returned by user APIs. It cannot log in, cannot hold chips, cannot join games.

## Testing

### Game-loop integration (poker-server, Mongo via TestContainers)

Extends `BaseIntegrationTest`.

**`EventRecorderIntegrationTest`** — drive a deterministic game-loop scenario with two tables and multiple hands. Assert:

- Every emitted event is captured as a `RecordedEvent` with correct `gameId`, `tableId`, `handNumber`, `userId`.
- Events emitted before the first `HandStarted` on a table get `handNumber = null`.
- Events emitted between `HandComplete` and the next `HandStarted` get `handNumber = null`.
- All `UserEvent`s (e.g., `HoleCardsDealt` for each player) are captured — not filtered out by the listener despite being addressed to specific users.
- Game-level events (e.g., `GameStatusChanged`, `PlayerBuyIn`) get `tableId = null` and `handNumber = null`.
- After a server "restart" (reconstruct the recorder against the same Mongo), `currentHandByTable` is correctly seeded so the next event in an active hand gets the right `handNumber`.

**`ReplayControllerIntegrationTest`** — full HTTP path with Spring Security.

- Admin GET for an existing hand returns events in the expected order (by `sequenceNumber`, then `recordedAt`).
- Non-admin GET → 403.
- GET for a non-existent `(gameId, tableId, handNumber)` → 200 with empty array.
- GET for a hand on an in-progress game emits `AdminViewingReplay` to a test listener attached to that game.
- GET for a hand on a `COMPLETED` game does NOT emit `AdminViewingReplay`.

### Unit (poker-server)

**`EventRecorderResilienceTest`** — fill the bounded queue, assert subsequent `onEvent` calls return without blocking and the dropped-event counter increments. Inject a repository that throws from the worker thread; assert the worker logs and continues processing subsequent events.

### Existing tests

The recorder is attached by default in any path that constructs a `GameManager`. Existing event-assertion tests assert against events emitted by the game manager (via `gameContext.events()`), not against listeners, so they are unaffected. Tests that don't have Mongo wired up rely on the no-op `EventRecorderService` from test configuration.

## Out of Scope (noted for later)

- WebSocket-based live replay (the v2 follow-up).
- Per-player perspective filtering on replay (always god-view in v1).
- Time-range / cross-hand / cross-game query endpoints (schema supports them; no v1 endpoint).
- Retention policy / TTL index.
- Admin scrubbing UI in the client.
- Replay of *commands* (would let you fork the timeline; bigger project; needs a seedable deck).
- Encryption-at-rest for the recordings collection beyond what Mongo natively provides.
