# Event Sequence Numbers — Client Design

**Date:** 2026-04-26
**Status:** Approved (brainstorm)
**Spec source:** `http://localhost:8080/command-event-spec.md` (server-authoritative)

## Background

The server now stamps every broadcast `GameEvent` with a `sequenceNumber` and exposes per-stream resume points on its snapshots. The client must (1) detect gaps, (2) discard stale state on a gap, and (3) recover from snapshots. Three new game-stream events (`PlayerDisconnected`, `PlayerReconnected`, `AdminViewingReplay`) and one new table-stream event (`BlindPosted`) ship in the same release and are in scope.

## Server contract (verbatim from spec)

Two independent monotonic streams per game:
- **Game stream** — single counter on broadcast `GameEvent`s that are not `TableEvent`s.
- **Table stream(s)** — one counter per table, on broadcast `TableEvent`s.

Both start at `1` and advance by `1` per stamped event. They are in-memory; a server restart resets them.

| Event category | Stamped? | Notes |
|---|---|---|
| Broadcast `GameEvent` (not `TableEvent`) | Yes | Game stream. |
| Broadcast `TableEvent` | Yes | Per-table stream. |
| `UserEvent` (incl. `HoleCardsDealt`, snapshots, `UserMessage`) | No | `sequenceNumber = 0`. Excluded from gap detection. |
| `SystemError` | No | No `sequenceNumber` field. |

Recovery rule per stream:

| `seq` vs `expected` | Action |
|---|---|
| `seq == expected` | Accept; increment expected. |
| `seq > expected` | Gap — discard local state for the stream and request a snapshot. |
| `seq < expected` | Duplicate or out-of-order — ignore. |

`HoleCardsDealt` implements both `TableEvent` and `UserEvent`; the `UserEvent` filter wins, so it carries `sequenceNumber = 0` and **must not** advance the table-stream expected.

`GameSnapshot` carries `gameStreamSeq: long` and `tableStreamSeqs: Map<String, long>`. `TableSnapshot` carries `streamSeq: long`. Resume = next event will be `seq + 1`.

## Goal

Bring the Angular client to compliance with the new contract:
- Track sequence numbers per stream.
- Detect gaps and recover via snapshot.
- Survive server restart and WebSocket reconnect transparently.
- Handle the four new event types end-to-end (state + UI).

## Non-goals

- No `BlindPosted` chip-flying animation (existing chip rendering will reflect the bet update naturally).
- No debounce of `PlayerDisconnected` flicker (acknowledged as a possible follow-up; YAGNI for now).
- No persistent "admin watching" badge (one-shot toast + message-list entry only).

## Architecture

A new `EventStreamService` sits between `GameWebSocketService` (transport) and `GameStateService` (state).

```
GameWebSocketService ─events──> EventStreamService ──delivered──> GameStateService
                                       │
                                       ├──gapDetected──────────────> GameStateService
                                       │                              (wipe slice + refetch)
                                       └──sendCommand callback ─────> GameWebSocketService
                                          (request snapshot on gap)
```

`EventStreamService` is the single source of truth for sequence tracking. `GameWebSocketService` stays a transport (no domain knowledge). `GameStateService`'s reducers see only events that have already passed gap detection.

## `EventStreamService`

### State

```ts
private gameBaselineSet = false;
private expectedGameSeq = 0;

private tableBaselines = new Map<string, {
  baselineSet: boolean;
  expectedSeq: number;
}>();

private gapDetected$ = new Subject<{ kind: 'game' } | { kind: 'table'; tableId: string }>();
private deliveredEvents$ = new Subject<GameEvent>();
```

### Public API

```ts
process(event: GameEvent): void;
seedGameBaseline(gameStreamSeq: number, tableStreamSeqs: Record<string, number>): void;
seedTableBaseline(tableId: string, streamSeq: number): void;
reset(): void;
getDeliveredEvents(): Observable<GameEvent>;
getGapDetected(): Observable<{ kind: 'game' } | { kind: 'table'; tableId: string }>;
```

### `process(event)` flow

```
classify(event):
  exempt   → emit on deliveredEvents$  (snapshots, HoleCardsDealt, UserMessage)
  game     → checkAndAdvance(stream='game')
  table:T  → checkAndAdvance(stream={tableId: T})

checkAndAdvance(stream):
  if !baselineSet(stream): drop silently
  else:
    seq = event.sequenceNumber
    expected = currentExpected(stream)
    if seq == expected: advance(stream); emit on deliveredEvents$
    if seq < expected:  drop (debug-log)
    if seq > expected:  invalidate(stream); emit on gapDetected$
```

`invalidate(stream)` clears `baselineSet` and zeroes the expected counter for that stream. Subsequent events on the stream are dropped until a fresh baseline is seeded.

### Why drop while no baseline (instead of buffering)

A snapshot is the source of truth at its resume point. Anything that arrived before the snapshot's resume seq is stale by the time the snapshot lands; anything after must be re-evaluated against the seeded baseline anyway. Buffering adds complexity for no correctness gain.

## Stream classification

`src/app/game/event-streams.ts`:

```ts
export type EventStream =
  | { kind: 'game' }
  | { kind: 'table'; tableId: string }
  | { kind: 'exempt' };

export function classifyEvent(event: GameEvent): EventStream;
```

Internally, two `Set<eventType>` constants. Adding a future event type is a one-line edit.

**Game-stream event types (9):**
`game-status-changed`, `game-message`, `player-buy-in`, `player-joined`, `player-seated`, `player-moved-tables`, `player-disconnected`, `player-reconnected`, `admin-viewing-replay`.

**Table-stream event types (12):**
`table-status-changed`, `hand-phase-changed`, `waiting-for-players`, `hand-started`, `blind-posted`, `community-cards-dealt`, `player-acted`, `player-timed-out`, `action-on-player`, `betting-round-complete`, `showdown-result`, `hand-complete`.

**Exempt:** `hole-cards-dealt`, `user-message`, `game-snapshot`, `table-snapshot`. (Snapshots have no `sequenceNumber` field at all; the rest carry `sequenceNumber = 0`.)

## Connection lifecycle

| Trigger | Action |
|---|---|
| `GameStateService.connectToGame(gameId)` | `eventStream.reset()`; subscribe to delivered events; on WS `connected`, send `join-game` then `get-game-state`. (Existing flow; `reset()` is the only addition.) |
| WS retry begins (`connecting` status from retry block) | `eventStream.reset()`. |
| WS retry succeeds (`connected` after a prior retry) | Send `get-game-state` again to re-seed baselines. |
| `GameSnapshot` reducer | Call `eventStream.seedGameBaseline(gameStreamSeq, tableStreamSeqs)`. |
| `TableSnapshot` reducer | Call `eventStream.seedTableBaseline(table.id, streamSeq)`. |

This handles three failure modes identically: connection blip, server restart (counters reset), and missed events during disconnect.

## Gap recovery in `GameStateService`

Subscribed to `eventStream.getGapDetected()`:

```
on { kind: 'game' }:
  - clear state.status, state.players, state.messages, state.tables
  - keep state.gameId
  - sendCommand('get-game-state', { gameId })

on { kind: 'table', tableId }:
  - replace state.tables.get(tableId) with createInitialTableState(tableId)
  - sendCommand('get-table-state', { gameId, tableId })
  - leave players/messages/status alone
```

Player chips and seat positions sourced from table-stream events (`BettingRoundComplete`, `BlindPosted`, etc.) get re-derived from the incoming `TableSnapshot.table.seats[]`. Player chips from `PlayerBuyIn` (game-stream) survive a table-only discard.

## Data-model changes

### Existing event interfaces

Add `sequenceNumber: number` to:
- Game-stream: `GameStatusChangedEvent`, `GameMessageEvent`, `PlayerBuyInEvent`, `PlayerJoinedEvent`, `PlayerSeatedEvent`, `PlayerMovedTablesEvent`.
- Table-stream: `TableStatusChangedEvent`, `HandPhaseChangedEvent`, `WaitingForPlayersEvent`, `HandStartedEvent`, `CommunityCardsDealtEvent`, `PlayerActedEvent`, `PlayerTimedOutEvent`, `ActionOnPlayerEvent`, `BettingRoundCompleteEvent`, `ShowdownResultEvent`, `HandCompleteEvent`.
- Exempt (carries 0): `HoleCardsDealtEvent`, `UserMessageEvent`.

### Snapshot resume-point fields

```ts
interface GameSnapshotEvent {
  // ...existing fields...
  gameStreamSeq: number;
  tableStreamSeqs: Record<string, number>;
}

interface TableSnapshotEvent {
  // ...existing fields...
  streamSeq: number;
}
```

### New event interfaces

```ts
export interface PlayerDisconnectedEvent {
  eventType: 'player-disconnected';
  timestamp: string;
  sequenceNumber: number;
  gameId: string;
  userId: string;
}

export interface PlayerReconnectedEvent {
  eventType: 'player-reconnected';
  timestamp: string;
  sequenceNumber: number;
  gameId: string;
  userId: string;
}

export interface AdminViewingReplayEvent {
  eventType: 'admin-viewing-replay';
  timestamp: string;
  sequenceNumber: number;
  gameId: string;
  adminUserId: string;
  adminAlias: string;
  tableId: string;
  handNumber: number;
}

export type BlindType = 'SMALL' | 'BIG';

export interface BlindPostedEvent {
  eventType: 'blind-posted';
  timestamp: string;
  sequenceNumber: number;
  gameId: string;
  tableId: string;
  seatPosition: number;
  userId: string;
  blindType: BlindType;
  amountPosted: number;
}
```

All four are added to the `GameEvent` discriminated union.

### `PlayerState` shape

```ts
interface PlayerState {
  userId: string;
  displayName: string;
  chipCount: number;
  tableId: string | null;
  seatPosition: number | null;
  isConnected: boolean | null;  // NEW; null = unknown
}
```

`null` initial state matters: snapshots don't carry presence, so users seen only via snapshots have unknown presence and must render as connected.

## New event reducers

### `BlindPosted` (table-stream)

- Decrement posting seat's `chipCount` by `amountPosted`; bump `currentBetAmount` by the same.
- Update `state.players.<userId>.chipCount`.
- Append a `game-message`: `"<name> posts <small|big> blind <amount>"`.
- No change to `lastAction`.

### `PlayerDisconnected` / `PlayerReconnected` (game-stream)

- Set `state.players.<userId>.isConnected = false` / `true`.
- Append `game-message`: `"<name> disconnected"` / `"<name> reconnected"`.

### `AdminViewingReplay` (game-stream)

- Append a `game-message`: `"<adminAlias> is reviewing hand <handNumber>"`.
- Call `toasterService.displayToast({ message, type: 'warning' })`.

## UI changes

- **Phaser seat pod** (`src/app/game-lobby/phaser-table/`): when the player at a seat has `isConnected === false`, dim the avatar. `null` and `true` render normally.
- No new components.

## File breakdown

**New:**
- `src/app/game/event-streams.ts` — classifier (~40 lines).
- `src/app/game/event-streams.spec.ts` — table-driven classification tests.
- `src/app/game/event-stream.service.ts` — `EventStreamService`.
- `src/app/game/event-stream.service.spec.ts` — unit tests.

**Modified:**
- `src/app/game/game-events.ts` — field additions, four new interfaces, union extension.
- `src/app/game/game-state.service.ts` — wire `EventStreamService` in; subscribe to delivered events and gap signal; call seed methods from snapshot reducers; reset on retry/disconnect; new event reducers; `isConnected` handling.
- `src/app/game/game-state.service.spec.ts` — gap-recovery and new-event-reducer tests.
- `src/app/game-lobby/phaser-table/...` — dim-on-disconnect.

**Unchanged:**
- `src/app/game/game-websocket.service.ts` — stays a pure transport.
- `src/app/game/game-commands.ts` — `get-game-state` and `get-table-state` already exist.

## Testing

**Unit — `event-streams.spec.ts`**
For every `eventType` literal, assert classification matches the spec table. Catches drift when the union grows.

**Unit — `event-stream.service.spec.ts`**
- Drop events when baseline not set; no emission, no gap.
- After `seedGameBaseline(5, {t1: 10})`: accept `seq=6` (game), `seq=11` (t1); reject `seq=8` (game-gap), `seq=13` (t1-gap); drop `seq=4` (duplicate).
- Exempt events pass through regardless of baseline state and never advance any counter.
- Per-table gap doesn't disturb game-stream tracking, and vice versa.
- `reset()` clears all baselines.

**Integration — `game-state.service.spec.ts`**
- Connect → `GameSnapshot` arrives with seq fields → subsequent events with correct seqs are accepted.
- Inject a sequenced table event with a future seq → table state cleared, `get-table-state` sent, follow-up `TableSnapshot` re-seats baseline.
- Game-stream gap → all tables cleared, `get-game-state` sent.
- `BlindPosted` reducer: chip count and `currentBetAmount` updated.
- `PlayerDisconnected` → `isConnected: false`; `PlayerReconnected` → `isConnected: true`.
- `AdminViewingReplay` → toast + message-list entry.

**Manual smoke (per CLAUDE.md):**
- `npm start` against the local server; play a hand end-to-end.
- Restart the server while connected; verify resync and UI restoration.
- Open two clients, close one's socket; verify dimmed avatar appears on the other.

## Implementation order

1. Data model (`game-events.ts` field additions) + classifier (`event-streams.ts`).
2. `EventStreamService` standalone, fully tested.
3. Wire it into `GameStateService`; snapshot reducers seed baselines; gap signal triggers discard + refetch. Existing tests should still pass.
4. New event reducers (`BlindPosted`, presence pair, `AdminViewingReplay`).
5. Phaser seat-pod dim-on-disconnect.
6. Manual smoke test.
