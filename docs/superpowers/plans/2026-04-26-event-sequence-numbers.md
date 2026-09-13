# Event Sequence Numbers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the Angular client to compliance with the server's per-stream monotonic `sequenceNumber` contract: detect gaps, discard stale state on a gap, recover from snapshots, and survive WebSocket reconnect / server restart. Also fully handle the four new event types shipping with the same release.

**Architecture:** A new `EventStreamService` sits between `GameWebSocketService` (transport) and `GameStateService` (state). It classifies each incoming event into game-stream / table-stream / exempt, applies the spec's three-way `seq` vs `expected` rule, drops events when no baseline is set, and emits two streams: delivered-events (passes gap detection) and gap-detected (signals state discard + snapshot refetch). Snapshot reducers seed baselines via two service methods. Connection lifecycle (initial connect, retry) calls `reset()` and re-fetches snapshots so server-restart and missed-events recover identically.

**Tech Stack:** Angular 19 standalone components, RxJS, TypeScript strict, Jest.

**Spec:** `docs/superpowers/specs/2026-04-26-event-sequence-numbers-design.md`.

---

## File Structure

**New:**
- `src/app/game/event-streams.ts` — `classifyEvent()` and the static stream-type sets.
- `src/app/game/event-streams.spec.ts`
- `src/app/game/event-stream.service.ts` — `EventStreamService` (state machine, baseline seeding, gap signal).
- `src/app/game/event-stream.service.spec.ts`
- `src/app/game/game-state.service.spec.ts` — new (no existing tests for this service); covers gap recovery + new reducers.

**Modified:**
- `src/app/game/game-events.ts` — add `sequenceNumber` to existing event interfaces; add `BlindType`, `BlindPostedEvent`, `PlayerDisconnectedEvent`, `PlayerReconnectedEvent`, `AdminViewingReplayEvent`; extend `GameSnapshotEvent` and `TableSnapshotEvent` with resume-point fields; extend `GameEvent` union.
- `src/app/game/game-state.service.ts` — add `EventStreamService` dependency; route raw events through it; subscribe to delivered + gap streams; call seed methods from snapshot reducers; add `isConnected` to `PlayerState`; add reducers for the four new events; reset on retry; toast on `AdminViewingReplay`.
- `src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts` — pass `isConnected` to `seat.updateSeat(...)`.
- `src/app/game-lobby/phaser-table/game-objects/seat-display.ts` — accept `isConnected: boolean | null` and dim avatar when `false`.

**Unchanged:** `src/app/game/game-websocket.service.ts` (stays a transport), `src/app/game/game-commands.ts` (`get-game-state` and `get-table-state` already exist).

---

## Task 1: Add sequenceNumber to existing event interfaces

**Files:**
- Modify: `src/app/game/game-events.ts`

- [ ] **Step 1: Add `sequenceNumber: number` to existing interfaces**

In `src/app/game/game-events.ts`, add the field immediately after `timestamp` on these interfaces:

Game-stream:
- `GameStatusChangedEvent`
- `GameMessageEvent`
- `PlayerBuyInEvent`
- `PlayerJoinedEvent`
- `PlayerSeatedEvent`
- `PlayerMovedTablesEvent`

Table-stream:
- `TableStatusChangedEvent`
- `HandPhaseChangedEvent`
- `WaitingForPlayersEvent`
- `HandStartedEvent`
- `CommunityCardsDealtEvent`
- `PlayerActedEvent`
- `PlayerTimedOutEvent`
- `ActionOnPlayerEvent`
- `BettingRoundCompleteEvent`
- `ShowdownResultEvent`
- `HandCompleteEvent`

Exempt (always 0 per spec):
- `HoleCardsDealtEvent`
- `UserMessageEvent`

Example for `GameStatusChangedEvent`:
```ts
export interface GameStatusChangedEvent {
  eventType: 'game-status-changed';
  timestamp: string;
  sequenceNumber: number;
  gameId: string;
  oldStatus: GameStatus;
  newStatus: GameStatus;
}
```

Apply the same pattern to all 18 interfaces above. Do **not** add the field to `GameSnapshotEvent` or `TableSnapshotEvent` — those carry resume-point fields instead (Task 2).

- [ ] **Step 2: Run TypeScript compiler to confirm shape additions don't break anything**

Run: `npx tsc --noEmit`

Expected: PASS. Tests/state code does not currently destructure `sequenceNumber`, so adding it is purely additive.

- [ ] **Step 3: Commit**

```bash
git add src/app/game/game-events.ts
git commit -m "feat(events): add sequenceNumber field to GameEvent interfaces"
```

---

## Task 2: Add snapshot resume-point fields and BlindType enum

**Files:**
- Modify: `src/app/game/game-events.ts`

- [ ] **Step 1: Extend `GameSnapshotEvent`**

In `src/app/game/game-events.ts`, change `GameSnapshotEvent` to:

```ts
export interface GameSnapshotEvent {
  eventType: 'game-snapshot';
  timestamp: string;
  userId: string;
  gameId: string;
  gameName: string;
  status: GameStatus;
  startTime: string;
  smallBlind: number;
  bigBlind: number;
  players: Player[];
  tableIds: string[];
  gameStreamSeq: number;
  tableStreamSeqs: Record<string, number>;
}
```

- [ ] **Step 2: Extend `TableSnapshotEvent`**

In the same file, change `TableSnapshotEvent` to:

```ts
export interface TableSnapshotEvent {
  eventType: 'table-snapshot';
  timestamp: string;
  userId: string;
  gameId: string;
  table: Table;
  streamSeq: number;
}
```

- [ ] **Step 3: Add `BlindType`**

Add near the top of the file (e.g. just after the `MessageSeverity` type):

```ts
export type BlindType = 'SMALL' | 'BIG';
```

- [ ] **Step 4: Run TypeScript compiler**

Run: `npx tsc --noEmit`

Expected: PASS. (Reducer code in `game-state.service.ts` does not currently read these new fields, so additions are non-breaking.)

- [ ] **Step 5: Commit**

```bash
git add src/app/game/game-events.ts
git commit -m "feat(events): add snapshot resume-point fields and BlindType"
```

---

## Task 3: Add four new event interfaces and extend GameEvent union

**Files:**
- Modify: `src/app/game/game-events.ts`

- [ ] **Step 1: Add the four new interfaces**

Insert these in `src/app/game/game-events.ts`. Place game-stream events grouped near the existing game-level events; place `BlindPostedEvent` grouped with the other table-level events.

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

- [ ] **Step 2: Extend `GameEvent` discriminated union**

Change the existing `GameEvent` union to:

```ts
export type GameEvent =
  | GameStatusChangedEvent
  | GameMessageEvent
  | PlayerJoinedEvent
  | PlayerSeatedEvent
  | PlayerBuyInEvent
  | PlayerMovedTablesEvent
  | PlayerDisconnectedEvent
  | PlayerReconnectedEvent
  | AdminViewingReplayEvent
  | TableStatusChangedEvent
  | HandPhaseChangedEvent
  | WaitingForPlayersEvent
  | HandStartedEvent
  | BlindPostedEvent
  | HoleCardsDealtEvent
  | CommunityCardsDealtEvent
  | PlayerActedEvent
  | PlayerTimedOutEvent
  | ActionOnPlayerEvent
  | BettingRoundCompleteEvent
  | ShowdownResultEvent
  | HandCompleteEvent
  | UserMessageEvent
  | GameSnapshotEvent
  | TableSnapshotEvent;
```

- [ ] **Step 3: Run TypeScript compiler**

Run: `npx tsc --noEmit`

Expected: FAIL with errors in `game-state.service.ts` `handleEvent` switch — the four new event types are not yet handled. **This is expected and gets fixed in Tasks 9 + 11.** To unblock the rest of the plan, add a temporary catch-all clause now.

- [ ] **Step 4: Add a temporary catch-all to the `handleEvent` switch**

In `src/app/game/game-state.service.ts`, find the switch in `handleEvent`. Immediately before the closing brace of the switch (after the `'table-snapshot'` case), add:

```ts
      default:
        // Temporary; new event types are wired up in later tasks.
        break;
```

- [ ] **Step 5: Run TypeScript compiler again**

Run: `npx tsc --noEmit`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/app/game/game-events.ts src/app/game/game-state.service.ts
git commit -m "feat(events): add new event interfaces (disconnect/reconnect/replay/blind-posted)"
```

---

## Task 4: Stream classifier — failing test

**Files:**
- Create: `src/app/game/event-streams.spec.ts`

- [ ] **Step 1: Write the test file**

Create `src/app/game/event-streams.spec.ts`:

```ts
import { classifyEvent, EventStream } from './event-streams';
import { GameEvent } from './game-events';

function makeEvent(eventType: string, extras: Record<string, unknown> = {}): GameEvent {
  return {
    eventType,
    timestamp: '2026-04-26T00:00:00Z',
    sequenceNumber: 1,
    gameId: 'g1',
    ...extras,
  } as unknown as GameEvent;
}

describe('classifyEvent', () => {
  const gameStreamTypes = [
    'game-status-changed',
    'game-message',
    'player-buy-in',
    'player-joined',
    'player-seated',
    'player-moved-tables',
    'player-disconnected',
    'player-reconnected',
    'admin-viewing-replay',
  ];

  const tableStreamTypes = [
    'table-status-changed',
    'hand-phase-changed',
    'waiting-for-players',
    'hand-started',
    'blind-posted',
    'community-cards-dealt',
    'player-acted',
    'player-timed-out',
    'action-on-player',
    'betting-round-complete',
    'showdown-result',
    'hand-complete',
  ];

  const exemptTypes = [
    'hole-cards-dealt',
    'user-message',
    'game-snapshot',
    'table-snapshot',
  ];

  it.each(gameStreamTypes)('classifies %s as game stream', (eventType) => {
    const result = classifyEvent(makeEvent(eventType));
    expect(result).toEqual<EventStream>({ kind: 'game' });
  });

  it.each(tableStreamTypes)('classifies %s as table stream with tableId', (eventType) => {
    const result = classifyEvent(makeEvent(eventType, { tableId: 't1' }));
    expect(result).toEqual<EventStream>({ kind: 'table', tableId: 't1' });
  });

  it.each(exemptTypes)('classifies %s as exempt', (eventType) => {
    const result = classifyEvent(makeEvent(eventType));
    expect(result).toEqual<EventStream>({ kind: 'exempt' });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx jest src/app/game/event-streams.spec.ts`

Expected: FAIL with module-not-found error for `./event-streams`.

---

## Task 5: Stream classifier — implementation

**Files:**
- Create: `src/app/game/event-streams.ts`

- [ ] **Step 1: Write the classifier**

Create `src/app/game/event-streams.ts`:

```ts
import { GameEvent } from './game-events';

export type EventStream =
  | { kind: 'game' }
  | { kind: 'table'; tableId: string }
  | { kind: 'exempt' };

const GAME_STREAM_TYPES = new Set<GameEvent['eventType']>([
  'game-status-changed',
  'game-message',
  'player-buy-in',
  'player-joined',
  'player-seated',
  'player-moved-tables',
  'player-disconnected',
  'player-reconnected',
  'admin-viewing-replay',
]);

const TABLE_STREAM_TYPES = new Set<GameEvent['eventType']>([
  'table-status-changed',
  'hand-phase-changed',
  'waiting-for-players',
  'hand-started',
  'blind-posted',
  'community-cards-dealt',
  'player-acted',
  'player-timed-out',
  'action-on-player',
  'betting-round-complete',
  'showdown-result',
  'hand-complete',
]);

export function classifyEvent(event: GameEvent): EventStream {
  if (GAME_STREAM_TYPES.has(event.eventType)) {
    return { kind: 'game' };
  }
  if (TABLE_STREAM_TYPES.has(event.eventType)) {
    return { kind: 'table', tableId: (event as { tableId: string }).tableId };
  }
  return { kind: 'exempt' };
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `npx jest src/app/game/event-streams.spec.ts`

Expected: PASS, 25 tests.

- [ ] **Step 3: Commit**

```bash
git add src/app/game/event-streams.ts src/app/game/event-streams.spec.ts
git commit -m "feat(events): add event stream classifier"
```

---

## Task 6: EventStreamService — failing tests

**Files:**
- Create: `src/app/game/event-stream.service.spec.ts`

- [ ] **Step 1: Write the test file**

Create `src/app/game/event-stream.service.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { EventStreamService } from './event-stream.service';
import { GameEvent } from './game-events';

function gameStreamEvent(seq: number): GameEvent {
  return {
    eventType: 'game-status-changed',
    timestamp: '2026-04-26T00:00:00Z',
    sequenceNumber: seq,
    gameId: 'g1',
    oldStatus: 'SCHEDULED',
    newStatus: 'ACTIVE',
  } as GameEvent;
}

function tableStreamEvent(tableId: string, seq: number): GameEvent {
  return {
    eventType: 'hand-phase-changed',
    timestamp: '2026-04-26T00:00:00Z',
    sequenceNumber: seq,
    gameId: 'g1',
    tableId,
    oldPhase: 'PREDEAL',
    newPhase: 'DEAL',
  } as GameEvent;
}

function snapshotEvent(): GameEvent {
  return {
    eventType: 'game-snapshot',
    timestamp: '2026-04-26T00:00:00Z',
    userId: 'u1',
    gameId: 'g1',
    gameName: 'Game',
    status: 'ACTIVE',
    startTime: '2026-04-26T00:00:00Z',
    smallBlind: 1,
    bigBlind: 2,
    players: [],
    tableIds: ['t1'],
    gameStreamSeq: 0,
    tableStreamSeqs: { t1: 0 },
  } as GameEvent;
}

describe('EventStreamService', () => {
  let service: EventStreamService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(EventStreamService);
  });

  describe('without baselines', () => {
    it('drops game-stream events when no baseline is set', () => {
      const delivered: GameEvent[] = [];
      service.getDeliveredEvents().subscribe((e) => delivered.push(e));
      service.process(gameStreamEvent(1));
      expect(delivered).toEqual([]);
    });

    it('drops table-stream events when no baseline is set', () => {
      const delivered: GameEvent[] = [];
      service.getDeliveredEvents().subscribe((e) => delivered.push(e));
      service.process(tableStreamEvent('t1', 1));
      expect(delivered).toEqual([]);
    });

    it('does not emit gapDetected for events with no baseline', () => {
      const gaps: unknown[] = [];
      service.getGapDetected().subscribe((g) => gaps.push(g));
      service.process(gameStreamEvent(5));
      service.process(tableStreamEvent('t1', 5));
      expect(gaps).toEqual([]);
    });

    it('passes exempt events through (snapshot)', () => {
      const delivered: GameEvent[] = [];
      service.getDeliveredEvents().subscribe((e) => delivered.push(e));
      service.process(snapshotEvent());
      expect(delivered.length).toBe(1);
      expect(delivered[0].eventType).toBe('game-snapshot');
    });
  });

  describe('after seedGameBaseline', () => {
    beforeEach(() => {
      service.seedGameBaseline(5, { t1: 10 });
    });

    it('accepts game-stream seq == expected', () => {
      const delivered: GameEvent[] = [];
      service.getDeliveredEvents().subscribe((e) => delivered.push(e));
      service.process(gameStreamEvent(6));
      expect(delivered.length).toBe(1);
    });

    it('accepts table-stream seq == expected', () => {
      const delivered: GameEvent[] = [];
      service.getDeliveredEvents().subscribe((e) => delivered.push(e));
      service.process(tableStreamEvent('t1', 11));
      expect(delivered.length).toBe(1);
    });

    it('emits gap and drops event when game-stream seq > expected', () => {
      const delivered: GameEvent[] = [];
      const gaps: unknown[] = [];
      service.getDeliveredEvents().subscribe((e) => delivered.push(e));
      service.getGapDetected().subscribe((g) => gaps.push(g));

      service.process(gameStreamEvent(8));

      expect(delivered).toEqual([]);
      expect(gaps).toEqual([{ kind: 'game' }]);
    });

    it('emits gap and drops event when table-stream seq > expected', () => {
      const delivered: GameEvent[] = [];
      const gaps: unknown[] = [];
      service.getDeliveredEvents().subscribe((e) => delivered.push(e));
      service.getGapDetected().subscribe((g) => gaps.push(g));

      service.process(tableStreamEvent('t1', 13));

      expect(delivered).toEqual([]);
      expect(gaps).toEqual([{ kind: 'table', tableId: 't1' }]);
    });

    it('drops game-stream seq < expected (duplicate/out-of-order)', () => {
      const delivered: GameEvent[] = [];
      const gaps: unknown[] = [];
      service.getDeliveredEvents().subscribe((e) => delivered.push(e));
      service.getGapDetected().subscribe((g) => gaps.push(g));

      service.process(gameStreamEvent(4));

      expect(delivered).toEqual([]);
      expect(gaps).toEqual([]);
    });

    it('a per-table gap does not disturb game-stream tracking', () => {
      const delivered: GameEvent[] = [];
      service.getDeliveredEvents().subscribe((e) => delivered.push(e));

      service.process(tableStreamEvent('t1', 13)); // gap on t1
      service.process(gameStreamEvent(6));         // game still expects 6

      expect(delivered.map((e) => e.eventType)).toEqual(['game-status-changed']);
    });

    it('after a gap, subsequent stream events are dropped until reseeded', () => {
      const delivered: GameEvent[] = [];
      service.getDeliveredEvents().subscribe((e) => delivered.push(e));

      service.process(gameStreamEvent(8));   // gap; baseline cleared
      service.process(gameStreamEvent(9));   // dropped — no baseline
      service.process(gameStreamEvent(10));  // dropped

      expect(delivered).toEqual([]);
    });

    it('reseeding after a gap re-arms acceptance', () => {
      const delivered: GameEvent[] = [];
      service.getDeliveredEvents().subscribe((e) => delivered.push(e));

      service.process(gameStreamEvent(8));   // gap
      service.seedGameBaseline(20, { t1: 10 });
      service.process(gameStreamEvent(21));

      expect(delivered.length).toBe(1);
    });

    it('exempt events pass through and never advance counters', () => {
      const delivered: GameEvent[] = [];
      const gaps: unknown[] = [];
      service.getDeliveredEvents().subscribe((e) => delivered.push(e));
      service.getGapDetected().subscribe((g) => gaps.push(g));

      service.process(snapshotEvent());
      service.process(gameStreamEvent(6)); // still expects 6 even though snapshot just passed

      expect(delivered.length).toBe(2);
      expect(gaps).toEqual([]);
    });
  });

  describe('seedTableBaseline', () => {
    it('arms a previously-unseen table for acceptance', () => {
      const delivered: GameEvent[] = [];
      service.getDeliveredEvents().subscribe((e) => delivered.push(e));

      service.seedTableBaseline('t2', 4);
      service.process(tableStreamEvent('t2', 5));

      expect(delivered.length).toBe(1);
    });
  });

  describe('reset', () => {
    it('clears baselines so subsequent events drop', () => {
      const delivered: GameEvent[] = [];
      service.getDeliveredEvents().subscribe((e) => delivered.push(e));

      service.seedGameBaseline(0, { t1: 0 });
      service.reset();
      service.process(gameStreamEvent(1));

      expect(delivered).toEqual([]);
    });
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npx jest src/app/game/event-stream.service.spec.ts`

Expected: FAIL with module-not-found error for `./event-stream.service`.

---

## Task 7: EventStreamService — implementation

**Files:**
- Create: `src/app/game/event-stream.service.ts`

- [ ] **Step 1: Write the service**

Create `src/app/game/event-stream.service.ts`:

```ts
import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { GameEvent } from './game-events';
import { classifyEvent } from './event-streams';

export type GapStream = { kind: 'game' } | { kind: 'table'; tableId: string };

interface TableBaseline {
  baselineSet: boolean;
  expectedSeq: number;
}

@Injectable({
  providedIn: 'root',
})
export class EventStreamService {
  private gameBaselineSet = false;
  private expectedGameSeq = 0;
  private tableBaselines = new Map<string, TableBaseline>();

  private deliveredEvents$ = new Subject<GameEvent>();
  private gapDetected$ = new Subject<GapStream>();

  process(event: GameEvent): void {
    const stream = classifyEvent(event);

    if (stream.kind === 'exempt') {
      this.deliveredEvents$.next(event);
      return;
    }

    if (stream.kind === 'game') {
      if (!this.gameBaselineSet) return;
      this.checkAndAdvanceGame(event);
      return;
    }

    // stream.kind === 'table'
    const baseline = this.tableBaselines.get(stream.tableId);
    if (!baseline || !baseline.baselineSet) return;
    this.checkAndAdvanceTable(stream.tableId, baseline, event);
  }

  seedGameBaseline(gameStreamSeq: number, tableStreamSeqs: Record<string, number>): void {
    this.gameBaselineSet = true;
    this.expectedGameSeq = gameStreamSeq + 1;
    for (const [tableId, seq] of Object.entries(tableStreamSeqs)) {
      this.tableBaselines.set(tableId, {
        baselineSet: true,
        expectedSeq: seq + 1,
      });
    }
  }

  seedTableBaseline(tableId: string, streamSeq: number): void {
    this.tableBaselines.set(tableId, {
      baselineSet: true,
      expectedSeq: streamSeq + 1,
    });
  }

  reset(): void {
    this.gameBaselineSet = false;
    this.expectedGameSeq = 0;
    this.tableBaselines.clear();
  }

  getDeliveredEvents(): Observable<GameEvent> {
    return this.deliveredEvents$.asObservable();
  }

  getGapDetected(): Observable<GapStream> {
    return this.gapDetected$.asObservable();
  }

  private checkAndAdvanceGame(event: GameEvent): void {
    const seq = event.sequenceNumber;
    if (seq === this.expectedGameSeq) {
      this.expectedGameSeq += 1;
      this.deliveredEvents$.next(event);
    } else if (seq > this.expectedGameSeq) {
      this.gameBaselineSet = false;
      this.expectedGameSeq = 0;
      this.gapDetected$.next({ kind: 'game' });
    }
    // seq < expected: duplicate/out-of-order; drop silently.
  }

  private checkAndAdvanceTable(tableId: string, baseline: TableBaseline, event: GameEvent): void {
    const seq = event.sequenceNumber;
    if (seq === baseline.expectedSeq) {
      baseline.expectedSeq += 1;
      this.deliveredEvents$.next(event);
    } else if (seq > baseline.expectedSeq) {
      baseline.baselineSet = false;
      baseline.expectedSeq = 0;
      this.gapDetected$.next({ kind: 'table', tableId });
    }
    // seq < expected: drop.
  }
}
```

A note on the access pattern: `event.sequenceNumber` is unsafe across the union (snapshot variants don't have it, but those go down the `exempt` branch and never reach the seq check). The narrowing is expressed structurally — by the time we read `sequenceNumber`, the classifier has guaranteed the event is a stamped variant. TypeScript can't prove this from the classifier signature, so we read the field directly via the union; the field exists on every game-stream and table-stream event interface.

- [ ] **Step 2: Run tests to verify they pass**

Run: `npx jest src/app/game/event-stream.service.spec.ts`

Expected: PASS.

- [ ] **Step 3: Run TypeScript compiler**

Run: `npx tsc --noEmit`

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/app/game/event-stream.service.ts src/app/game/event-stream.service.spec.ts
git commit -m "feat(game): add EventStreamService for sequence tracking and gap detection"
```

---

## Task 8: Wire EventStreamService into GameStateService

**Files:**
- Modify: `src/app/game/game-state.service.ts`

This task replaces the direct `webSocketService.connect(...).subscribe(handleEvent)` wiring with a routed flow through `EventStreamService`. Snapshot reducers seed baselines; gap signals trigger discard + refetch. The new event reducers are added in Task 9 (so this task ends with a clean commit covering only the wiring).

- [ ] **Step 1: Inject `EventStreamService` into `GameStateService`**

In `src/app/game/game-state.service.ts`, change the imports and the constructor / inject calls:

```ts
import { EventStreamService } from './event-stream.service';
```

Add the inject below the existing `toasterService`:

```ts
private eventStream = inject(EventStreamService);
```

- [ ] **Step 2: Replace the WS subscription with routed delivery**

Find the existing `connectToGame` method. Locate this block:

```ts
    this.subscription = this.webSocketService
      .connect(gameId)
      .subscribe((event) => this.handleEvent(event));
```

Replace it with:

```ts
    this.eventStream.reset();

    this.subscription = this.webSocketService
      .connect(gameId)
      .subscribe((event) => this.eventStream.process(event));

    this.deliveredSubscription = this.eventStream
      .getDeliveredEvents()
      .subscribe((event) => this.handleEvent(event));

    this.gapSubscription = this.eventStream
      .getGapDetected()
      .subscribe((stream) => this.handleGap(stream));
```

Add the matching field declarations near the existing `private subscription: Subscription | null = null;`:

```ts
  private deliveredSubscription: Subscription | null = null;
  private gapSubscription: Subscription | null = null;
```

- [ ] **Step 3: Update `disconnect()` to tear down the new subscriptions and reset the tracker**

Replace the existing body of `disconnect()` with:

```ts
  disconnect(): void {
    this.subscription?.unsubscribe();
    this.subscription = null;
    this.deliveredSubscription?.unsubscribe();
    this.deliveredSubscription = null;
    this.gapSubscription?.unsubscribe();
    this.gapSubscription = null;
    this.statusSubscription?.unsubscribe();
    this.statusSubscription = null;
    this.webSocketService.disconnect();
    this.eventStream.reset();
    this.displayNames.clear();
    this.state$.next(createInitialState());
  }
```

- [ ] **Step 4: Add `handleGap` method**

Add this private method to `GameStateService`, near `handleEvent`:

```ts
  private handleGap(stream: GapStream): void {
    const gameId = this.state$.value.gameId;
    if (!gameId) return;

    if (stream.kind === 'game') {
      const cleared = createInitialState();
      cleared.gameId = gameId;
      this.state$.next(cleared);
      this.webSocketService.sendCommand({
        commandId: 'get-game-state',
        gameId,
      });
      return;
    }

    // stream.kind === 'table'
    const state = { ...this.state$.value };
    const tables = new Map(state.tables);
    tables.set(stream.tableId, createInitialTableState(stream.tableId));
    state.tables = tables;
    this.state$.next(state);

    this.webSocketService.sendCommand({
      commandId: 'get-table-state',
      gameId,
      tableId: stream.tableId,
    });
  }
```

Add the `GapStream` import at the top of the file:

```ts
import { EventStreamService, GapStream } from './event-stream.service';
```

- [ ] **Step 5: Seed baselines from snapshot reducers**

In `handleEvent`, find the `'game-snapshot'` case. After the existing body (just before the `break;`), add:

```ts
        this.eventStream.seedGameBaseline(event.gameStreamSeq, event.tableStreamSeqs);
```

In the `'table-snapshot'` case, after the existing body (just before the `break;`), add:

```ts
        this.eventStream.seedTableBaseline(event.table.id, event.streamSeq);
```

- [ ] **Step 6: Reset + refetch on WS reconnect**

Find the existing `statusSubscription` setup in `connectToGame`. Currently it filters for `'connected'` and runs once via `take(1)`. Replace it with a subscription that reacts on every status change so we re-seed on reconnect:

```ts
    this.statusSubscription = this.webSocketService
      .getConnectionStatus()
      .subscribe((status) => {
        if (status === 'connecting') {
          this.eventStream.reset();
          return;
        }
        if (status === 'connected') {
          this.webSocketService.sendCommand({
            commandId: 'join-game',
            gameId,
          });
          this.webSocketService.sendCommand({
            commandId: 'get-game-state',
            gameId,
          });
        }
      });
```

Note: this fires `join-game` again on reconnect. The existing server-side `join-game` is idempotent for an already-joined player (re-issuing it on reconnect is the safe path). If your server does not tolerate this, narrow the second branch to only send `get-game-state` on a *re*-connect; for the initial connect the spec doesn't define behavior either way. We send both since it matches today's flow.

The unused `filter`, `take` imports may now be removable from the RxJS import line; remove them only if your linter complains.

- [ ] **Step 7: Run TypeScript compiler**

Run: `npx tsc --noEmit`

Expected: PASS.

- [ ] **Step 8: Run existing tests to make sure nothing regressed**

Run: `npm test`

Expected: PASS for all existing suites. (No new test added in this task; testing comes in Task 10.)

- [ ] **Step 9: Commit**

```bash
git add src/app/game/game-state.service.ts
git commit -m "feat(game): route events through EventStreamService for gap detection"
```

---

## Task 9: New event reducers

**Files:**
- Modify: `src/app/game/game-state.service.ts`

- [ ] **Step 1: Add `isConnected` to `PlayerState`**

In `src/app/game/game-state.service.ts`, change the `PlayerState` interface:

```ts
export interface PlayerState {
  userId: string;
  displayName: string;
  chipCount: number;
  tableId: string | null;
  /** 1-indexed seat position (1..numberOfSeats), or null if not seated. */
  seatPosition: number | null;
  /** Connection presence; null until first presence event for this user. */
  isConnected: boolean | null;
}
```

- [ ] **Step 2: Update player creation sites to default `isConnected: null`**

Grep for places that construct a `PlayerState`. Add `isConnected: null` to each. The current sites are:

In the `'player-joined'` case:
```ts
players.set(event.userId, {
  userId: event.userId,
  displayName: this.getDisplayName(event.userId),
  chipCount: 0,
  tableId: null,
  seatPosition: null,
  isConnected: null,
});
```

In the `'player-buy-in'` case:
```ts
players.set(event.userId, {
  userId: event.userId,
  displayName: existing?.displayName ?? this.getDisplayName(event.userId),
  chipCount: event.newChipCount,
  tableId: existing?.tableId ?? null,
  seatPosition: existing?.seatPosition ?? null,
  isConnected: existing?.isConnected ?? null,
});
```

In the `'player-moved-tables'` case:
```ts
players.set(event.userId, {
  userId: event.userId,
  displayName: existing?.displayName ?? this.getDisplayName(event.userId),
  chipCount: existing?.chipCount ?? 0,
  tableId: event.toTableId,
  seatPosition: existing?.seatPosition ?? null,
  isConnected: existing?.isConnected ?? null,
});
```

In the `'game-snapshot'` case (inside the player-build loop):
```ts
players.set(player.user.id, {
  userId: player.user.id,
  displayName,
  chipCount: player.chipCount,
  tableId: player.tableId,
  seatPosition: null,
  isConnected: null,
});
```

In `syncPlayersFromSummaries` for the new-player branch:
```ts
players.set(s.userId, {
  userId: s.userId,
  displayName: this.getDisplayName(s.userId),
  chipCount: s.chipCount,
  tableId,
  seatPosition: s.seatPosition,
  isConnected: null,
});
```

- [ ] **Step 3: Add reducer for `'player-disconnected'` and `'player-reconnected'`**

In `handleEvent`, replace the temporary `default:` clause from Task 3 by adding these case blocks before it:

```ts
      case 'player-disconnected': {
        const players = new Map(state.players);
        const existing = players.get(event.userId);
        if (existing) {
          players.set(event.userId, { ...existing, isConnected: false });
          state.players = players;
        }
        const name = this.getDisplayName(event.userId);
        state.messages = [
          ...state.messages,
          this.createInfoMessage(event.gameId, `${name} disconnected`),
        ];
        break;
      }

      case 'player-reconnected': {
        const players = new Map(state.players);
        const existing = players.get(event.userId);
        if (existing) {
          players.set(event.userId, { ...existing, isConnected: true });
          state.players = players;
        }
        const name = this.getDisplayName(event.userId);
        state.messages = [
          ...state.messages,
          this.createInfoMessage(event.gameId, `${name} reconnected`),
        ];
        break;
      }
```

- [ ] **Step 4: Add reducer for `'admin-viewing-replay'`**

Add this case in the same switch:

```ts
      case 'admin-viewing-replay': {
        const message = `${event.adminAlias} is reviewing hand ${event.handNumber}`;
        state.messages = [
          ...state.messages,
          this.createInfoMessage(event.gameId, message),
        ];
        this.toasterService.displayToast({ message, type: 'warning' });
        break;
      }
```

- [ ] **Step 5: Add reducer for `'blind-posted'`**

Add this case:

```ts
      case 'blind-posted': {
        this.updateTable(state, event.tableId, (t) => {
          const seatSummaries = new Map(t.seatSummaries);
          const prior = seatSummaries.get(event.seatPosition);
          if (prior) {
            seatSummaries.set(event.seatPosition, {
              ...prior,
              chipCount: prior.chipCount - event.amountPosted,
              currentBetAmount: prior.currentBetAmount + event.amountPosted,
            });
          }
          return { ...t, seatSummaries };
        });

        const players = new Map(state.players);
        const existing = players.get(event.userId);
        if (existing) {
          players.set(event.userId, {
            ...existing,
            chipCount: existing.chipCount - event.amountPosted,
          });
          state.players = players;
        }

        const name = this.getDisplayName(event.userId);
        const blindLabel = event.blindType === 'SMALL' ? 'small' : 'big';
        state.messages = [
          ...state.messages,
          this.createInfoMessage(
            event.gameId,
            `${name} posts ${blindLabel} blind ${LangUtils.formatCurrency(event.amountPosted)}`,
          ),
        ];
        break;
      }
```

- [ ] **Step 6: Remove the temporary `default:` clause**

The four new cases now cover the union; delete the temporary clause from Task 3.

- [ ] **Step 7: Run TypeScript compiler**

Run: `npx tsc --noEmit`

Expected: PASS. The switch is exhaustive over `GameEvent['eventType']`.

- [ ] **Step 8: Run existing tests**

Run: `npm test`

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/app/game/game-state.service.ts
git commit -m "feat(game): handle PlayerDisconnected/Reconnected, AdminViewingReplay, BlindPosted"
```

---

## Task 10: GameStateService integration tests

**Files:**
- Create: `src/app/game/game-state.service.spec.ts`

This task adds focused integration tests exercising the wiring. We don't try to re-test every reducer — we cover the four new reducers and the gap-recovery flow.

- [ ] **Step 1: Write the test file**

Create `src/app/game/game-state.service.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { GameStateService } from './game-state.service';
import { GameWebSocketService, ConnectionStatus } from './game-websocket.service';
import { EventStreamService } from './event-stream.service';
import { ToasterService } from '../toaster/toaster.service';
import { GameEvent } from './game-events';
import { GameCommand } from './game-commands';

class FakeWebSocket {
  events$ = new Subject<GameEvent>();
  status$ = new Subject<ConnectionStatus>();
  sent: GameCommand[] = [];

  connect(_gameId: string) {
    return this.events$.asObservable();
  }
  getConnectionStatus() {
    return this.status$.asObservable();
  }
  sendCommand(command: GameCommand) {
    this.sent.push(command);
  }
  disconnect() {}
}

class FakeToaster {
  toasts: { message: string; type: string }[] = [];
  displayToast(t: { message: string; type: string }) {
    this.toasts.push(t);
  }
}

function snapshot(extras: { gameStreamSeq: number; tableStreamSeqs: Record<string, number> }): GameEvent {
  return {
    eventType: 'game-snapshot',
    timestamp: '2026-04-26T00:00:00Z',
    userId: 'u1',
    gameId: 'g1',
    gameName: 'Test',
    status: 'ACTIVE',
    startTime: '2026-04-26T00:00:00Z',
    smallBlind: 1,
    bigBlind: 2,
    players: [],
    tableIds: Object.keys(extras.tableStreamSeqs),
    gameStreamSeq: extras.gameStreamSeq,
    tableStreamSeqs: extras.tableStreamSeqs,
  } as GameEvent;
}

function tableSnapshot(tableId: string, streamSeq: number): GameEvent {
  return {
    eventType: 'table-snapshot',
    timestamp: '2026-04-26T00:00:00Z',
    userId: 'u1',
    gameId: 'g1',
    table: {
      id: tableId,
      seats: [],
      status: 'PLAYING',
      handPhase: 'PREDEAL',
      dealerPosition: null,
      actionPosition: null,
      smallBlindPosition: null,
      bigBlindPosition: null,
      lastRaiserPosition: null,
      currentBet: 0,
      minimumRaise: 0,
      handNumber: 0,
      phaseStartedAt: null,
      actionDeadline: null,
      communityCards: [],
      pots: [],
    },
    streamSeq,
  } as GameEvent;
}

describe('GameStateService', () => {
  let service: GameStateService;
  let ws: FakeWebSocket;
  let toaster: FakeToaster;

  beforeEach(() => {
    ws = new FakeWebSocket();
    toaster = new FakeToaster();
    TestBed.configureTestingModule({
      providers: [
        GameStateService,
        EventStreamService,
        { provide: GameWebSocketService, useValue: ws },
        { provide: ToasterService, useValue: toaster },
      ],
    });
    service = TestBed.inject(GameStateService);
    service.connectToGame('g1');
    ws.status$.next('connected');
  });

  describe('gap recovery', () => {
    it('clears table state and sends get-table-state on a per-table gap', () => {
      // Seed baselines via snapshot.
      ws.events$.next(snapshot({ gameStreamSeq: 0, tableStreamSeqs: { t1: 0 } }));

      // Inject a future-seq table event.
      ws.events$.next({
        eventType: 'hand-phase-changed',
        timestamp: '2026-04-26T00:00:00Z',
        sequenceNumber: 5,
        gameId: 'g1',
        tableId: 't1',
        oldPhase: 'PREDEAL',
        newPhase: 'DEAL',
      } as GameEvent);

      const sent = ws.sent.find((c) => c.commandId === 'get-table-state');
      expect(sent).toEqual({ commandId: 'get-table-state', gameId: 'g1', tableId: 't1' });
    });

    it('clears all tables and sends get-game-state on a game-stream gap', () => {
      ws.events$.next(snapshot({ gameStreamSeq: 0, tableStreamSeqs: { t1: 0 } }));

      ws.events$.next({
        eventType: 'game-status-changed',
        timestamp: '2026-04-26T00:00:00Z',
        sequenceNumber: 5,
        gameId: 'g1',
        oldStatus: 'ACTIVE',
        newStatus: 'PAUSED',
      } as GameEvent);

      const lastGetGameState = ws.sent
        .filter((c) => c.commandId === 'get-game-state')
        .pop();
      expect(lastGetGameState).toBeDefined();
    });

    it('table-snapshot reseats the table baseline so subsequent events are accepted', () => {
      ws.events$.next(snapshot({ gameStreamSeq: 0, tableStreamSeqs: { t1: 0 } }));

      // Cause a gap.
      ws.events$.next({
        eventType: 'hand-phase-changed',
        timestamp: '2026-04-26T00:00:00Z',
        sequenceNumber: 5,
        gameId: 'g1',
        tableId: 't1',
        oldPhase: 'PREDEAL',
        newPhase: 'DEAL',
      } as GameEvent);

      // Server responds with a table snapshot at streamSeq=10.
      ws.events$.next(tableSnapshot('t1', 10));

      // Subsequent table event seq=11 should be accepted (no second gap).
      const beforeSent = ws.sent.length;
      ws.events$.next({
        eventType: 'hand-phase-changed',
        timestamp: '2026-04-26T00:00:00Z',
        sequenceNumber: 11,
        gameId: 'g1',
        tableId: 't1',
        oldPhase: 'PREDEAL',
        newPhase: 'DEAL',
      } as GameEvent);
      const afterSent = ws.sent.length;
      expect(afterSent).toBe(beforeSent); // no extra get-table-state
    });
  });

  describe('new event reducers', () => {
    beforeEach(() => {
      ws.events$.next(snapshot({ gameStreamSeq: 0, tableStreamSeqs: { t1: 0 } }));
    });

    it('PlayerDisconnected sets isConnected=false', (done) => {
      ws.events$.next({
        eventType: 'player-joined',
        timestamp: '2026-04-26T00:00:00Z',
        sequenceNumber: 1,
        gameId: 'g1',
        userId: 'u-alice',
      } as GameEvent);

      ws.events$.next({
        eventType: 'player-disconnected',
        timestamp: '2026-04-26T00:00:00Z',
        sequenceNumber: 2,
        gameId: 'g1',
        userId: 'u-alice',
      } as GameEvent);

      service.getState().subscribe((s) => {
        const player = s.players.get('u-alice');
        if (player && player.isConnected === false) {
          done();
        }
      });
    });

    it('PlayerReconnected sets isConnected=true', (done) => {
      ws.events$.next({
        eventType: 'player-joined',
        timestamp: '2026-04-26T00:00:00Z',
        sequenceNumber: 1,
        gameId: 'g1',
        userId: 'u-alice',
      } as GameEvent);
      ws.events$.next({
        eventType: 'player-disconnected',
        timestamp: '2026-04-26T00:00:00Z',
        sequenceNumber: 2,
        gameId: 'g1',
        userId: 'u-alice',
      } as GameEvent);
      ws.events$.next({
        eventType: 'player-reconnected',
        timestamp: '2026-04-26T00:00:00Z',
        sequenceNumber: 3,
        gameId: 'g1',
        userId: 'u-alice',
      } as GameEvent);

      service.getState().subscribe((s) => {
        const player = s.players.get('u-alice');
        if (player && player.isConnected === true) {
          done();
        }
      });
    });

    it('AdminViewingReplay produces a warning toast and a message', (done) => {
      ws.events$.next({
        eventType: 'admin-viewing-replay',
        timestamp: '2026-04-26T00:00:00Z',
        sequenceNumber: 1,
        gameId: 'g1',
        adminUserId: 'admin-1',
        adminAlias: 'AdminAlice',
        tableId: 't1',
        handNumber: 42,
      } as GameEvent);

      service.getState().subscribe((s) => {
        const found = s.messages.find(
          (m) => m.eventType === 'game-message' && m.message.includes('AdminAlice') && m.message.includes('42'),
        );
        if (found) {
          expect(toaster.toasts).toEqual([
            { message: 'AdminAlice is reviewing hand 42', type: 'warning' },
          ]);
          done();
        }
      });
    });

    it('BlindPosted decrements seat chips and bumps currentBetAmount', (done) => {
      // Seed table state with a hand-started so seat summaries exist.
      ws.events$.next({
        eventType: 'hand-started',
        timestamp: '2026-04-26T00:00:00Z',
        sequenceNumber: 1,
        gameId: 'g1',
        tableId: 't1',
        handNumber: 1,
        dealerPosition: 1,
        smallBlindPosition: 2,
        bigBlindPosition: 3,
        smallBlindAmount: 50,
        bigBlindAmount: 100,
        currentBet: 100,
        minimumRaise: 100,
        seats: [
          { seatPosition: 2, userId: 'u-sb', status: 'ACTIVE', chipCount: 1000, currentBetAmount: 0 },
        ],
      } as GameEvent);

      ws.events$.next({
        eventType: 'blind-posted',
        timestamp: '2026-04-26T00:00:00Z',
        sequenceNumber: 2,
        gameId: 'g1',
        tableId: 't1',
        seatPosition: 2,
        userId: 'u-sb',
        blindType: 'SMALL',
        amountPosted: 50,
      } as GameEvent);

      service.getTableState('t1').subscribe((t) => {
        const summary = t?.seatSummaries.get(2);
        if (summary && summary.chipCount === 950 && summary.currentBetAmount === 50) {
          done();
        }
      });
    });
  });
});
```

- [ ] **Step 2: Run the new tests**

Run: `npx jest src/app/game/game-state.service.spec.ts`

Expected: PASS.

- [ ] **Step 3: Run the full suite**

Run: `npm test`

Expected: PASS for everything.

- [ ] **Step 4: Commit**

```bash
git add src/app/game/game-state.service.spec.ts
git commit -m "test(game): cover gap recovery and new event reducers"
```

---

## Task 11: Phaser seat dim-on-disconnect

**Files:**
- Modify: `src/app/game-lobby/phaser-table/game-objects/seat-display.ts`
- Modify: `src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts`

- [ ] **Step 1: Extend `updateSeat` to take an `isConnected` flag**

In `src/app/game-lobby/phaser-table/game-objects/seat-display.ts`, change the `updateSeat` signature to accept a connection flag:

```ts
  updateSeat(
    playerName: string | null,
    chipCount: number | null,
    cards: SeatCard[] | null,
    isActive: boolean,
    isConnected: boolean | null,
  ): void {
    if (!playerName) {
      this.renderEmpty();
      return;
    }
    this.renderOccupied(playerName, chipCount, cards, isActive, isConnected);
  }
```

Update `renderOccupied` to accept the new param and apply alpha to the avatar group when disconnected. Change the signature and add the alpha application near the bottom of the method:

```ts
  private renderOccupied(
    playerName: string,
    chipCount: number | null,
    cards: SeatCard[] | null,
    isActive: boolean,
    isConnected: boolean | null,
  ): void {
    // ... existing body unchanged ...

    // Dim avatar + initial when player is known to be disconnected.
    const dimAlpha = isConnected === false ? 0.4 : 1;
    this.avatarBg.setAlpha(dimAlpha);
    this.avatarRing.setAlpha(dimAlpha === 1 ? AVATAR_RING_ALPHA : dimAlpha * AVATAR_RING_ALPHA);
    this.initialText.setAlpha(dimAlpha);
  }
```

(The existing `renderOccupied` body should remain — only the signature changes and the new dim block is appended at its very end, after `this.redrawTimer();`.)

- [ ] **Step 2: Update the scene to pass `isConnected`**

In `src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts`, find the two call sites for `updateSeat` (around lines 175, 196, 198 in the current file). Change them:

```ts
// Empty-state branch (no table state):
for (const seat of this.seats) seat.updateSeat(null, null, null, false, null);
```

```ts
// Occupied branch:
this.seats[idx].updateSeat(name, player.chipCount, cards, isActive, player.isConnected);
```

```ts
// Empty-seat branch:
this.seats[idx].updateSeat(null, null, null, false, null);
```

- [ ] **Step 3: Run TypeScript compiler**

Run: `npx tsc --noEmit`

Expected: PASS.

- [ ] **Step 4: Run the full test suite**

Run: `npm test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/app/game-lobby/phaser-table/game-objects/seat-display.ts src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts
git commit -m "feat(phaser): dim seat avatar when player is disconnected"
```

---

## Task 12: Manual smoke test

**Files:** none

Per CLAUDE.md UI-test guidance, exercise the running client end-to-end before declaring done.

- [ ] **Step 1: Start the dev server**

Run: `npm start`

Expected: Server boots; navigate to `http://localhost:4200`.

- [ ] **Step 2: Confirm baseline gameplay**

Log in, join a cash game, sit at a table, and play a single hand to completion. Confirm:
- Hole cards render
- Action timer ticks
- Bet chips show on bets
- Showdown messages appear

- [ ] **Step 3: Force a server-restart resync**

While connected, restart the local poker server (`Ctrl-C` and re-launch). Confirm in the client:
- Connection status briefly shows reconnect
- Game state restores after reconnect (no stuck UI, no console errors about gap-handling)

- [ ] **Step 4: Verify presence indicators with two browsers**

Open two browser tabs, log in as different users, both at the same table. Close one tab. Confirm:
- The other tab shows the closed user's avatar dimmed within a few seconds
- Re-open the tab: avatar returns to full opacity

- [ ] **Step 5: Verify blind-posted state**

Watch a hand start. Confirm chip counts on the small-blind and big-blind seats update immediately (without waiting for the next betting-round-complete) and that `posts small/big blind` messages appear in the message panel.

- [ ] **Step 6: Final commit (if any unrelated cleanup surfaced)**

If no further changes, no commit needed. Otherwise:

```bash
git add -A
git commit -m "chore: smoke-test fixups"
```

---

## Self-Review Checklist (post-plan)

- **Spec coverage:**
  - Sequence-tracking + gap detection → Tasks 6, 7, 8.
  - Snapshot resume points → Tasks 2, 8 (seed calls), 10 (test).
  - Drop-while-no-baseline rule → Task 7 implementation, Task 6 test.
  - Reconnect/server-restart recovery → Task 8 (`statusSubscription` rewrite).
  - `BlindPosted` reducer + UI → Task 9, Task 10 test.
  - `PlayerDisconnected` / `PlayerReconnected` reducers + UI → Task 9, Task 11 (avatar dim), Task 10 tests.
  - `AdminViewingReplay` reducer + toast → Task 9, Task 10 test.
  - `isConnected` field on `PlayerState` → Task 9.
- **Placeholder scan:** No "TBD"/"TODO"/"implement later" text. Each step has the actual code or command.
- **Type consistency:**
  - `EventStreamService.process / seedGameBaseline / seedTableBaseline / reset / getDeliveredEvents / getGapDetected` consistent across Tasks 6–8.
  - `GapStream` exported from `event-stream.service.ts` and imported in `game-state.service.ts` (Task 8).
  - `BlindType` defined in `game-events.ts` (Task 2) and used by `BlindPostedEvent` (Task 3) and the reducer's `event.blindType` check (Task 9).
  - `isConnected: boolean | null` consistent across `PlayerState` definition (Task 9), every player-creation site (Task 9), and the seat-display signature (Task 11).
