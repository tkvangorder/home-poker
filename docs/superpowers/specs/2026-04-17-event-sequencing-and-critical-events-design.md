# Event Sequencing and Critical Events — Design

**Date:** 2026-04-17
**Status:** Design, pending implementation plan

**Related:** [2026-04-25-event-store-and-replay-design.md](2026-04-25-event-store-and-replay-design.md) introduces an admin-facing forensic event store that consumes the sequence numbers defined here. It is forward-compatible — until this spec is implemented, the recorder writes `sequenceNumber = 0` for every event and falls back to `recordedAt` for ordering.

## Problem

An audit of the current event system found that a poker client cannot reliably reconstruct game and table state from the emitted event stream. Two classes of gap exist:

1. **Reliability gaps** — there is no way for a client to detect a dropped event. Events carry a timestamp but no sequence number, so a client that misses an event continues processing with corrupt state until the next snapshot request.
2. **Event coverage gaps** — several state transitions happen silently, leaving a client unable to observe them:
   - Small blind and big blind are posted without events; chip counts mutate and the client sees only the post-blind result in `HandStarted.seats[]`.
   - A player's socket can disconnect and reconnect with no event on the stream; other clients cannot distinguish "connecting…" from "left the game."

This spec addresses both classes of gap with the minimum additions needed to make client state reconstruction reliable.

## Goals

- Allow a client to detect and recover from dropped events.
- Emit blind-posting as a first-class event so clients can render chip movement and distinguish all-in-on-blind.
- Emit connection transitions so clients can surface presence state for other players.

## Non-Goals

- Event replay by range from a server-side ring buffer. Snapshot-based recovery is sufficient for this iteration.
- Ante, straddle, and dead-blind events. None of those rules are currently implemented; the event shape must be additively extensible but should not pre-model them.
- Grace periods or timers for disconnect debouncing. Clients can delay surfacing the event to the UI if desired.
- Auto-fold / auto-leave on disconnect. The existing action-timeout path (`PlayerTimedOut`) continues to be the only thing that acts on an absent player's turn.

## Design

### Event Sequence Numbers

**Where the number lives.** Add `long sequenceNumber()` to the `GameEvent` interface in `poker-common`. Since `TableEvent extends GameEvent`, all table events inherit it.

**Two counters per game:**

- **Game-stream counter** — one `AtomicLong` on `GameManager`, incremented for each non-`TableEvent` `GameEvent` published.
- **Per-table counter** — one `AtomicLong` on each `TexasHoldemTableManager`, incremented for each `TableEvent` published on that table.

Both counters start at `1` and are in-memory only. A server restart resets the streams; clients recover by requesting a fresh snapshot (`GetGameState` / `GetTableState`), which exists today.

**Where numbers are assigned.** Inside the existing fan-out loop in `GameManager.tick()` (around line 185). Before publishing each event from `gameContext.events()`:

1. If the event implements `UserEvent`, skip stamping (see "User-targeted events" below).
2. Else if the event implements `TableEvent`, stamp from that table's counter.
3. Else stamp from the game counter.

Stamping happens at fan-out rather than at event construction, so every listener sees the same sequence number for the same event and ordering within a stream is deterministic even when multiple commands produce events in the same tick.

**Implementation note.** Events are Java records; `sequenceNumber` is a final field. At construction time the emitter passes `0`, and the fan-out stamps by building a copy via the record's canonical constructor (a `withSequenceNumber` helper or equivalent per-event wither).

### User-Targeted Events and Gap Detection

A naive scheme assigns sequence numbers to every event on a stream, including user-targeted events like `HoleCardsDealt`. This is broken: when nine players each receive a private `HoleCardsDealt`, eight clients observe a gap in the stream.

**Rule:** Events implementing `UserEvent` are not part of the gap-detection contract. They carry `sequenceNumber = 0` and clients do not use them to advance stream expectations.

This matches the filtering precedence already in `WebSocketGameListener`: events that are both `TableEvent` and `UserEvent` (like `HoleCardsDealt`) are filtered as `UserEvent`.

**Worked example, one hand:**

```
table-stream seq 5:  HandStarted            (broadcast)
                     HoleCardsDealt × N     (user-targeted, no seq)
table-stream seq 6:  BlindPosted(SMALL)     (broadcast)
table-stream seq 7:  BlindPosted(BIG)       (broadcast)
table-stream seq 8:  ActionOnPlayer         (broadcast)
```

Every client sees a clean 5, 6, 7, 8 on the table stream.

**Recovery for user-targeted events.** A client that suspects it missed its own `HoleCardsDealt` requests `TableSnapshot`, which includes that user's hole cards.

### Client Semantics

A client tracks one expected counter per subscribed stream (one for the game stream plus one per table it is watching).

On each received broadcast event:

- `event.seq == expected` → accept, increment expected.
- `event.seq > expected` → gap detected; discard local state for this stream and issue `GetGameState` or `GetTableState`.
- `event.seq < expected` → duplicate or out-of-order; ignore.

### Snapshot Resume Points

Snapshots must carry the current sequence number so a client knows where to resume:

- `GameSnapshot` gains `long gameStreamSeq` and `Map<String, Long> tableStreamSeqs` (tableId → current seq).
- `TableSnapshot` gains `long streamSeq`.

Snapshot events are themselves `UserEvent`-filtered (sent only to the requestor), so they are not sequence-numbered. Their payload carries the resume points for all streams the client cares about.

`SystemError` is unchanged — filtered per-user, no sequence number.

### `BlindPosted` Event

```java
public record BlindPosted(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,
    int seatPosition,   // 1-based per project convention
    String userId,
    BlindType blindType,
    long amountPosted
) implements TableEvent {}
```

**`BlindType` enum** (new, in `poker-common` under `model/game/`):

```java
public enum BlindType { SMALL, BIG }
```

Positioned for additive extension (ante, straddle, dead blind) without breaking wire format.

**Emission site.** Inside `postBlind(...)` in `TexasHoldemTableManager` (around line 1184), immediately after the chip deduction and seat state update. The existing caller invokes `postBlind` twice (SB, then BB); that order is preserved.

**All-in-on-blind.** `postBlind` already uses `Math.min(stackSize, blindAmount)`. The `amountPosted` field carries the actual chips deducted. A client observing `amountPosted < expected blind` knows the player went all-in on the blind; no separate event is required.

**Consistency with `HandStarted`.** `HandStarted.seats[]` already shows post-blind chip counts. `BlindPosted` is the authoritative transaction record; `HandStarted` is the resulting snapshot. Clients that process events in order see chip counts move through the blinds, then confirm against the `HandStarted` seats.

### `PlayerDisconnected` and `PlayerReconnected` Events

```java
public record PlayerDisconnected(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String userId
) implements GameEvent {}

public record PlayerReconnected(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String userId
) implements GameEvent {}
```

Both are broadcast `GameEvent`s (not `TableEvent`s) — connection state is a game-level concern.

**Connection tracking.** `GameManager` owns a ref-counted `Map<String, Integer>` from `userId` to active-listener count. Ref counting is necessary because the codebase does not prevent multiple sockets per user.

**Trigger logic.**

- `addGameListener(listener)` — increment the ref count for `listener.userId()`.
  - If the count transitions `0 → 1` and a `Player` record already exists for that user in this game, emit `PlayerReconnected`.
  - If no prior `Player` record exists, the existing `PlayerJoined` path handles the event.
- `removeGameListener(listener)` / `removeGameListenersByUserId(...)` — decrement the ref count.
  - If the count transitions `1 → 0`, emit `PlayerDisconnected`.

**Emission through the command queue.** The events flow through the same `gameContext.events()` fan-out so they receive sequence numbers. Connection changes submit internal commands (`PlayerConnectedCommand`, `PlayerDisconnectedCommand`) to the MPSC queue; the game loop processes them, updates the ref count, and emits the event. This keeps connection-state mutations serialized with player-action mutations — a disconnect during an active hand cannot interleave oddly with `PlayerActed`.

## Testing

### Unit (poker-common)

- `BlindTypeTest` — enum values deserialize via Jackson.
- `PokerEventSerializationTest` — each new event round-trips JSON with `sequenceNumber` preserved.

### Game-loop integration (poker-server, `application-test.yml` deterministic mode)

- **`SequenceNumbersTest`** — drive a game with two tables. Assert each table's stream is monotonic and independent; assert the game stream is monotonic and independent of table streams; assert `HoleCardsDealt` events have `sequenceNumber == 0`.
- **`BlindPostedTest`** — deal a hand and assert the ordering `HandStarted → BlindPosted(SMALL) → BlindPosted(BIG) → ActionOnPlayer`. Assert `amountPosted` equals the blind under normal conditions. Additionally: seat a player with a stack less than the blind; assert `amountPosted` equals their stack and the seat is marked all-in.
- **`PlayerConnectionEventsTest`** — first listener registration for a new user does not emit `PlayerReconnected` (the existing `PlayerJoined` path covers it). Remove-then-re-register emits `PlayerDisconnected` then `PlayerReconnected`. Two listeners for the same user increment the ref count without emitting a second `PlayerReconnected`; removing one of two listeners does not emit `PlayerDisconnected`; removing the second does.
- **`SnapshotResumePointTest`** — drive events to advance the table stream to seq N. Issue `GetTableState` and assert the returned `TableSnapshot` carries stream seq N. Drive one more event and assert it has seq N+1.

### Existing tests to update

Event-matching assertions in `GameManagerTest`, `TexasHoldemTableManagerTest`, `CashManagerAssert`, and `WebSocketGameListenerTest` that compare full event records will need to accommodate the new `sequenceNumber` field. Prefer field-level assertions over full-record equality.

## Out of Scope (noted for later)

- Event replay by range with a server-side ring buffer.
- `isBigBlind` flag on `ActionOnPlayer`.
- Explicit seat-reassignment events during BALANCING.
- `actionDeadline` on `PlayerTimedOut`.
- Timing fields on `HandComplete`.
- The existing `LeaveGame` spec at `command-event-spec.md:174` claims disconnect triggers a leave; current code does not. This is a separate discrepancy and is not resolved here — this spec only adds informational connection events.
