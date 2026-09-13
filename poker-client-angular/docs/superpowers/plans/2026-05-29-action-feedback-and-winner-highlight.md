# Action Feedback & Winner Highlighting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Phaser poker table communicate clearly what just happened (each player's action, who's up next, who folded) and who won at showdown (with hand description and per-pot attribution).

**Architecture:** State layer gains a small `actionSeq` counter and an optional `kind` discriminator on synthetic client-side messages. A new `ActionBadge` Phaser game object renders per-seat action/winner pills driven by a pure `computeBadgeState` function. `SeatDisplay` learns folded-seat dimming. The wire protocol is unchanged.

**Tech Stack:** Angular 19 standalone components, RxJS, Phaser 3, Jest, Tailwind/DaisyUI.

**Spec:** `docs/superpowers/specs/2026-05-29-action-feedback-and-winner-highlight-design.md`

---

## File Structure

**New files:**
- `src/app/game-lobby/phaser-table/utils/action-badge-state.ts` — pure render-state mapping (Phaser-free)
- `src/app/game-lobby/phaser-table/utils/action-badge-state.spec.ts` — unit tests for the pure function
- `src/app/game-lobby/phaser-table/game-objects/action-badge.ts` — Phaser container that renders a BadgeState

**Modified files:**
- `src/app/game/game-events.ts` — add `ClientGameMessage` extension type with optional `kind`
- `src/app/game/game-state.service.ts` — add `actionSeq` to `TableState`; tag synthetic messages with `kind`; add action-history log lines
- `src/app/game/game-state.service.spec.ts` — cover the new behaviors
- `src/app/game-lobby/phaser-table/game-objects/seat-display.ts` — folded-seat dimming; expose `getBadgeAnchor()`
- `src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts` — instantiate and drive `ActionBadge` per seat
- `src/app/game-lobby/messages-panel/messages-panel.component.ts` — read `kind` field
- `src/app/game-lobby/messages-panel/messages-panel.component.html` — distinct row styling for `kind === 'showdown'` and `kind === 'action'`

---

## Background Reading

Before starting, an engineer new to this codebase should know:

- **Standalone components only.** No NgModules. See `CLAUDE.md`.
- **RxJS BehaviorSubject** is the only state primitive. No NgRx.
- **Jest** is the test runner. `npm test` runs the full suite; `npm test -- --testPathPattern=<file>` runs a single file.
- **Phaser is the active table renderer.** The legacy `css-poker-table/` directory is parked — do not modify it.
- **Seat positions are 1-indexed** on the wire and throughout the state layer. The Phaser scene's `seats[]` array is 0-indexed (seat at array position `i` corresponds to seat position `i + 1`).
- **`SeatCard.showCard`** controls post-hand reveal; the local user always sees their own hole cards regardless of this flag.
- **Chip amounts are integer cents** everywhere. `LangUtils.formatCurrency(cents)` formats them as `$X.XX`.

---

## Task 1: Add `actionSeq` to `TableState`

**Files:**
- Modify: `src/app/game/game-state.service.ts` (interface `TableState`, function `createInitialTableState`)
- Modify: `src/app/game/game-state.service.spec.ts`

- [ ] **Step 1: Add the failing test**

Open `src/app/game/game-state.service.spec.ts`. After the existing `describe('player-acted', ...)` block (around line 185), add a new `describe` block:

```ts
describe('actionSeq', () => {
  function getActionSeq(): number | undefined {
    let seq: number | undefined;
    service.getTableState(TABLE_ID).subscribe((t) => {
      seq = t?.actionSeq;
    });
    return seq;
  }

  it('initializes to 0 when a hand starts', () => {
    ws.events$.next(handStarted([seat(2, 'user-2', 100, 0), seat(3, 'user-3', 100, 0)]));
    expect(getActionSeq()).toBe(0);
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- --testPathPattern=game-state.service.spec`

Expected: FAIL — `Property 'actionSeq' does not exist on type 'TableState'.` or `expect(received).toBe(expected) … Received: undefined`.

- [ ] **Step 3: Add the field**

In `src/app/game/game-state.service.ts`, add `actionSeq: number;` to the `TableState` interface (after `currentBet`, `minimumRaise`):

```ts
export interface TableState {
  // ...existing fields...
  callAmount: number;
  currentBet: number;
  minimumRaise: number;
  actionSeq: number;
}
```

In `createInitialTableState`, add `actionSeq: 0,` (just before the closing brace).

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- --testPathPattern=game-state.service.spec`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/app/game/game-state.service.ts src/app/game/game-state.service.spec.ts
git commit -m "feat(state): add actionSeq to TableState"
```

---

## Task 2: Increment `actionSeq` on `player-acted` and reset on `hand-started`

**Files:**
- Modify: `src/app/game/game-state.service.ts` (case `'player-acted'`, case `'hand-started'`)
- Modify: `src/app/game/game-state.service.spec.ts`

- [ ] **Step 1: Add failing tests**

In the existing `describe('actionSeq', ...)` block in `game-state.service.spec.ts`, add:

```ts
it('increments on each player-acted event', () => {
  ws.events$.next(handStarted([seat(2, 'user-2', 95, 5), seat(3, 'user-3', 90, 10)]));

  ws.events$.next({
    eventType: 'player-acted',
    timestamp: '2026-05-09T00:00:01Z',
    gameId: GAME_ID,
    tableId: TABLE_ID,
    seatPosition: 3,
    userId: 'user-3',
    action: { type: 'raise', amount: 30 },
    chipCount: 70,
    resultingStatus: 'ACTIVE',
    currentBet: 30,
    minimumRaise: 20,
    potTotal: 35,
  } satisfies PlayerActedEvent);
  expect(getActionSeq()).toBe(1);

  ws.events$.next({
    eventType: 'player-acted',
    timestamp: '2026-05-09T00:00:02Z',
    gameId: GAME_ID,
    tableId: TABLE_ID,
    seatPosition: 2,
    userId: 'user-2',
    action: { type: 'call', amount: 25 },
    chipCount: 70,
    resultingStatus: 'ACTIVE',
    currentBet: 30,
    minimumRaise: 20,
    potTotal: 60,
  } satisfies PlayerActedEvent);
  expect(getActionSeq()).toBe(2);
});

it('resets to 0 when a new hand starts', () => {
  ws.events$.next(handStarted([seat(2, 'user-2', 95, 5), seat(3, 'user-3', 90, 10)]));

  ws.events$.next({
    eventType: 'player-acted',
    timestamp: '2026-05-09T00:00:01Z',
    gameId: GAME_ID,
    tableId: TABLE_ID,
    seatPosition: 3,
    userId: 'user-3',
    action: { type: 'fold' },
    chipCount: 90,
    resultingStatus: 'FOLDED',
    currentBet: 10,
    minimumRaise: 10,
    potTotal: 15,
  } satisfies PlayerActedEvent);
  expect(getActionSeq()).toBe(1);

  ws.events$.next(handStarted([seat(2, 'user-2', 95, 5), seat(3, 'user-3', 90, 10)]));
  expect(getActionSeq()).toBe(0);
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `npm test -- --testPathPattern=game-state.service.spec`

Expected: the two new tests FAIL with `Received: 0` (or `undefined`).

- [ ] **Step 3: Implement the increment**

In `src/app/game/game-state.service.ts`, locate the `case 'player-acted':` block. Inside the `updateTable(...)` callback, add `actionSeq: t.actionSeq + 1` to the returned object:

```ts
case 'player-acted': {
  this.updateTable(state, event.tableId, (t) => {
    const seatSummaries = new Map(t.seatSummaries);
    const prior = seatSummaries.get(event.seatPosition);
    if (prior) {
      const chipsSpent = prior.chipCount - event.chipCount;
      seatSummaries.set(event.seatPosition, {
        ...prior,
        status: event.resultingStatus,
        chipCount: event.chipCount,
        currentBetAmount: prior.currentBetAmount + chipsSpent,
      });
    }
    return {
      ...t,
      lastAction: { seatPosition: event.seatPosition, action: event.action.type },
      currentBet: event.currentBet,
      minimumRaise: event.minimumRaise,
      potTotal: event.potTotal,
      seatSummaries,
      actionSeq: t.actionSeq + 1,
    };
  });
  // ...rest unchanged
}
```

The `hand-started` case already constructs a fresh table state. Verify it still sets `actionSeq: 0` (it will, because `createInitialTableState()` is not called — `hand-started` does an explicit object literal. Add `actionSeq: 0,` to that literal):

In the `case 'hand-started':` block, add `actionSeq: 0,` to the returned object (next to `callAmount: 0,`).

- [ ] **Step 4: Run the tests**

Run: `npm test -- --testPathPattern=game-state.service.spec`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/app/game/game-state.service.ts src/app/game/game-state.service.spec.ts
git commit -m "feat(state): bump actionSeq on player-acted, reset on hand-started"
```

---

## Task 3: Increment `actionSeq` on `player-timed-out` and set `lastAction`

The current implementation of `player-timed-out` sets `lastAction` but does not bump `actionSeq`. Also verify the existing behavior records the default action.

**Files:**
- Modify: `src/app/game/game-state.service.ts` (case `'player-timed-out'`)
- Modify: `src/app/game/game-state.service.spec.ts`

- [ ] **Step 1: Add failing test**

Add to the `describe('actionSeq', ...)` block:

```ts
it('bumps on player-timed-out and records the default action', () => {
  ws.events$.next(handStarted([seat(2, 'user-2', 95, 5), seat(3, 'user-3', 90, 10)]));

  ws.events$.next({
    eventType: 'player-timed-out',
    timestamp: '2026-05-09T00:00:03Z',
    gameId: GAME_ID,
    tableId: TABLE_ID,
    seatPosition: 3,
    userId: 'user-3',
    defaultAction: { type: 'fold' },
  });

  expect(getActionSeq()).toBe(1);

  let lastAction: { seatPosition: number; action: string } | null = null;
  service.getTableState(TABLE_ID).subscribe((t) => {
    lastAction = t?.lastAction ?? null;
  });
  expect(lastAction).toEqual({ seatPosition: 3, action: 'fold' });
});
```

- [ ] **Step 2: Run the test, expect failure**

Run: `npm test -- --testPathPattern=game-state.service.spec`

Expected: FAIL — `actionSeq` stays at 0.

- [ ] **Step 3: Add the increment**

In `src/app/game/game-state.service.ts`, modify the `'player-timed-out'` case:

```ts
case 'player-timed-out': {
  this.updateTable(state, event.tableId, (t) => ({
    ...t,
    lastAction: {
      seatPosition: event.seatPosition,
      action: event.defaultAction.type,
    },
    actionSeq: t.actionSeq + 1,
  }));
  break;
}
```

- [ ] **Step 4: Run the test, expect pass**

Run: `npm test -- --testPathPattern=game-state.service.spec`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/app/game/game-state.service.ts src/app/game/game-state.service.spec.ts
git commit -m "feat(state): bump actionSeq on player-timed-out"
```

---

## Task 4: Introduce `ClientGameMessage` type with optional `kind` discriminator

The wire-level `GameMessageEvent` stays unchanged. We need a client-only extension to tag synthetic messages.

**Files:**
- Modify: `src/app/game/game-events.ts` (add `ClientGameMessage` type)
- Modify: `src/app/game/game-state.service.ts` (replace `GameMessageEvent` with `ClientGameMessage` in the messages array union)
- Modify: `src/app/game-lobby/messages-panel/messages-panel.component.ts` (input type)

- [ ] **Step 1: Add the type**

In `src/app/game/game-events.ts`, at the bottom of the `// --- Game-Level Events ---` section (just after the `GameMessageEvent` interface definition around line 47), add:

```ts
/**
 * Client-side synthetic variant of GameMessageEvent. Used for messages
 * generated by the client (action history, winner announcements) to carry
 * an optional `kind` discriminator for distinct rendering. Real server
 * `game-message` events have `kind === undefined`.
 */
export interface ClientGameMessage extends GameMessageEvent {
  kind?: 'action' | 'showdown';
}
```

- [ ] **Step 2: Update consumers' types**

In `src/app/game/game-state.service.ts`:

- Add `ClientGameMessage` to the imports:
  ```ts
  import {
    GameEvent,
    PotResult,
    SeatCard,
    HandPhase,
    GameMessageEvent,
    ClientGameMessage,
    UserMessageEvent,
    SeatSummary,
  } from './game-events';
  ```
- Change the `GameState.messages` field type:
  ```ts
  export interface GameState {
    gameId: string | null;
    status: GameStatus | null;
    players: Map<string, PlayerState>;
    tables: Map<string, TableState>;
    messages: Array<ClientGameMessage | UserMessageEvent>;
  }
  ```
- Change the `getMessages()` return type:
  ```ts
  getMessages(): Observable<Array<ClientGameMessage | UserMessageEvent>> {
    return this.state$.pipe(map((s) => s.messages));
  }
  ```
- Change the `createInfoMessage` return type to `ClientGameMessage`:
  ```ts
  private createInfoMessage(gameId: string, message: string): ClientGameMessage {
    return {
      eventType: 'game-message',
      timestamp: new Date().toISOString(),
      gameId,
      message,
    };
  }
  ```

In `src/app/game-lobby/messages-panel/messages-panel.component.ts`:

- Add `ClientGameMessage` to the imports and change the `Input`:
  ```ts
  import { ClientGameMessage, UserMessageEvent } from '../../game/game-events';
  // ...
  @Input() messages: (ClientGameMessage | UserMessageEvent)[] = [];

  getMessageClass(msg: ClientGameMessage | UserMessageEvent): string {
    if (msg.eventType === 'user-message') {
      switch (msg.severity) {
        case 'ERROR': return 'text-error';
        case 'WARNING': return 'text-warning';
      }
    }
    return '';
  }
  ```

- [ ] **Step 3: Run the test suite to confirm no regression**

Run: `npm test`

Expected: all existing tests still pass — this change is type-only.

- [ ] **Step 4: Commit**

```bash
git add src/app/game/game-events.ts src/app/game/game-state.service.ts src/app/game-lobby/messages-panel/messages-panel.component.ts
git commit -m "refactor: add ClientGameMessage type with optional kind discriminator"
```

---

## Task 5: Tag showdown winner messages with `kind: 'showdown'`

**Files:**
- Modify: `src/app/game/game-state.service.ts` (case `'showdown-result'`)
- Modify: `src/app/game/game-state.service.spec.ts`

- [ ] **Step 1: Add a failing test**

In `game-state.service.spec.ts`, add an import for `ShowdownResultEvent` and `ClientGameMessage` at the top, then add a new describe block:

```ts
describe('showdown-result messages', () => {
  it('tags emitted winner messages with kind="showdown"', () => {
    ws.events$.next(handStarted([seat(2, 'user-2', 95, 5), seat(3, 'user-3', 90, 10)]));

    ws.events$.next({
      eventType: 'showdown-result',
      timestamp: '2026-05-09T00:01:00Z',
      gameId: GAME_ID,
      tableId: TABLE_ID,
      potResults: [
        {
          potIndex: 0,
          potAmount: 50,
          winners: [
            { seatPosition: 3, userId: 'user-3', amount: 50, handDescription: 'Pair of Aces' },
          ],
        },
      ],
    } satisfies ShowdownResultEvent);

    let messages: Array<ClientGameMessage | UserMessageEvent> = [];
    service.getMessages().subscribe((m) => { messages = m; });
    const winnerMsg = messages.find(
      (m) => m.eventType === 'game-message' && (m as ClientGameMessage).kind === 'showdown'
    );
    expect(winnerMsg).toBeDefined();
    expect(winnerMsg!.message).toContain('Pair of Aces');
  });
});
```

Add the missing imports near the top of the file:

```ts
import {
  AdminViewingReplayEvent,
  BlindPostedEvent,
  ClientGameMessage,
  GameEvent,
  HandStartedEvent,
  PlayerActedEvent,
  PlayerDisconnectedEvent,
  PlayerReconnectedEvent,
  SeatSummary,
  ShowdownResultEvent,
  UserMessageEvent,
} from './game-events';
```

- [ ] **Step 2: Run the test, expect failure**

Run: `npm test -- --testPathPattern=game-state.service.spec`

Expected: FAIL — no message with `kind === 'showdown'`.

- [ ] **Step 3: Tag the message**

In `src/app/game/game-state.service.ts`, locate the `case 'showdown-result':` block. Replace the inner `createInfoMessage(...)` call with an inline literal that includes `kind`:

```ts
case 'showdown-result': {
  this.updateTable(state, event.tableId, (t) => ({
    ...t,
    potResults: event.potResults,
  }));
  for (const pot of event.potResults) {
    for (const winner of pot.winners) {
      const name = this.getDisplayName(winner.userId);
      const desc = winner.handDescription
        ? ` (${winner.handDescription})`
        : '';
      const msg: ClientGameMessage = {
        eventType: 'game-message',
        timestamp: new Date().toISOString(),
        gameId: event.gameId,
        message: `${name} won ${LangUtils.formatCurrency(winner.amount)}${desc}`,
        kind: 'showdown',
      };
      state.messages = [...state.messages, msg];
    }
  }
  break;
}
```

- [ ] **Step 4: Run the test, expect pass**

Run: `npm test -- --testPathPattern=game-state.service.spec`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/app/game/game-state.service.ts src/app/game/game-state.service.spec.ts
git commit -m "feat(state): tag showdown winner messages with kind=showdown"
```

---

## Task 6: Emit action-history log lines on `player-acted`

Currently, `player-acted` doesn't produce any log entry. Add a tagged action message so players have a scroll-back history.

**Files:**
- Modify: `src/app/game/game-state.service.ts` (case `'player-acted'`)
- Modify: `src/app/game/game-state.service.spec.ts`

- [ ] **Step 1: Add failing tests**

Add to `game-state.service.spec.ts`:

```ts
describe('player-acted log messages', () => {
  function getMessages(): Array<ClientGameMessage | UserMessageEvent> {
    let result: Array<ClientGameMessage | UserMessageEvent> = [];
    service.getMessages().subscribe((m) => { result = m; });
    return result;
  }

  beforeEach(() => {
    ws.events$.next(handStarted([seat(2, 'user-2', 95, 5), seat(3, 'user-3', 90, 10)]));
  });

  it('logs "folds" with kind=action', () => {
    ws.events$.next({
      eventType: 'player-acted',
      timestamp: '2026-05-09T00:00:01Z',
      gameId: GAME_ID,
      tableId: TABLE_ID,
      seatPosition: 2,
      userId: 'user-2',
      action: { type: 'fold' },
      chipCount: 95,
      resultingStatus: 'FOLDED',
      currentBet: 10,
      minimumRaise: 10,
      potTotal: 15,
    } satisfies PlayerActedEvent);

    const last = getMessages().at(-1)!;
    expect(last.eventType).toBe('game-message');
    expect((last as ClientGameMessage).kind).toBe('action');
    expect(last.message).toMatch(/folds$/);
  });

  it('logs "calls $X.XX"', () => {
    ws.events$.next({
      eventType: 'player-acted',
      timestamp: '2026-05-09T00:00:01Z',
      gameId: GAME_ID,
      tableId: TABLE_ID,
      seatPosition: 2,
      userId: 'user-2',
      action: { type: 'call', amount: 5 },
      chipCount: 90,
      resultingStatus: 'ACTIVE',
      currentBet: 10,
      minimumRaise: 10,
      potTotal: 20,
    } satisfies PlayerActedEvent);

    expect(getMessages().at(-1)!.message).toMatch(/calls \$0\.05$/);
  });

  it('logs "raises to $X.XX" using the wire-level total bet amount', () => {
    ws.events$.next({
      eventType: 'player-acted',
      timestamp: '2026-05-09T00:00:01Z',
      gameId: GAME_ID,
      tableId: TABLE_ID,
      seatPosition: 3,
      userId: 'user-3',
      action: { type: 'raise', amount: 30 },
      chipCount: 70,
      resultingStatus: 'ACTIVE',
      currentBet: 30,
      minimumRaise: 20,
      potTotal: 35,
    } satisfies PlayerActedEvent);

    expect(getMessages().at(-1)!.message).toMatch(/raises to \$0\.30$/);
  });

  it('appends "(all in)" when resultingStatus is ALL_IN', () => {
    ws.events$.next({
      eventType: 'player-acted',
      timestamp: '2026-05-09T00:00:01Z',
      gameId: GAME_ID,
      tableId: TABLE_ID,
      seatPosition: 3,
      userId: 'user-3',
      action: { type: 'raise', amount: 90 },
      chipCount: 0,
      resultingStatus: 'ALL_IN',
      currentBet: 90,
      minimumRaise: 80,
      potTotal: 95,
    } satisfies PlayerActedEvent);

    expect(getMessages().at(-1)!.message).toMatch(/raises to \$0\.90 \(all in\)$/);
  });
});
```

- [ ] **Step 2: Run the tests, expect failure**

Run: `npm test -- --testPathPattern=game-state.service.spec`

Expected: all four new tests FAIL — no action message is emitted today.

- [ ] **Step 3: Emit the messages**

In `src/app/game/game-state.service.ts`, locate the `case 'player-acted':` block. After the existing `updateTable(...)` call but before the player-chip-count update, add a helper to construct the message:

First, define a private helper method on the class (place it near `createInfoMessage`):

```ts
private formatActionVerb(action: PlayerAction): string {
  switch (action.type) {
    case 'fold': return 'folds';
    case 'check': return 'checks';
    case 'call': return `calls ${LangUtils.formatCurrency(action.amount)}`;
    case 'bet': return `bets ${LangUtils.formatCurrency(action.amount)}`;
    case 'raise': return `raises to ${LangUtils.formatCurrency(action.amount)}`;
  }
}
```

Add `PlayerAction` to the imports at the top of the file:

```ts
import { PlayerAction } from './game-commands';
```

Then modify the `case 'player-acted':` block to append the action message. Inside the case, after the `updateTable(...)` call:

```ts
// Append action history message
const actorName = this.getDisplayName(event.userId);
const verb = this.formatActionVerb(event.action);
const allInSuffix = event.resultingStatus === 'ALL_IN' ? ' (all in)' : '';
const actionMsg: ClientGameMessage = {
  eventType: 'game-message',
  timestamp: new Date().toISOString(),
  gameId: event.gameId,
  message: `${actorName} ${verb}${allInSuffix}`,
  kind: 'action',
};
state.messages = [...state.messages, actionMsg];
```

- [ ] **Step 4: Run the tests, expect pass**

Run: `npm test -- --testPathPattern=game-state.service.spec`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/app/game/game-state.service.ts src/app/game/game-state.service.spec.ts
git commit -m "feat(state): emit action-history log lines tagged kind=action"
```

---

## Task 7: Update messages panel template for `kind` styling

**Files:**
- Modify: `src/app/game-lobby/messages-panel/messages-panel.component.ts`
- Modify: `src/app/game-lobby/messages-panel/messages-panel.component.html`
- Create: `src/app/game-lobby/messages-panel/messages-panel.component.spec.ts`

- [ ] **Step 1: Create the failing test**

Create `src/app/game-lobby/messages-panel/messages-panel.component.spec.ts`:

```ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessagesPanelComponent } from './messages-panel.component';
import { ClientGameMessage, UserMessageEvent } from '../../game/game-events';

function showdownMsg(message: string): ClientGameMessage {
  return {
    eventType: 'game-message',
    timestamp: '2026-05-09T00:00:01Z',
    gameId: 'g1',
    message,
    kind: 'showdown',
  };
}

function actionMsg(message: string): ClientGameMessage {
  return {
    eventType: 'game-message',
    timestamp: '2026-05-09T00:00:01Z',
    gameId: 'g1',
    message,
    kind: 'action',
  };
}

function plainInfoMsg(message: string): ClientGameMessage {
  return {
    eventType: 'game-message',
    timestamp: '2026-05-09T00:00:01Z',
    gameId: 'g1',
    message,
  };
}

describe('MessagesPanelComponent', () => {
  let fixture: ComponentFixture<MessagesPanelComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [MessagesPanelComponent] });
    fixture = TestBed.createComponent(MessagesPanelComponent);
  });

  function render(messages: (ClientGameMessage | UserMessageEvent)[]): HTMLElement {
    fixture.componentInstance.messages = messages;
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('renders showdown messages with the showdown-row class and a WINNER prefix', () => {
    const el = render([showdownMsg('Alice won $0.50 (Pair of Aces)')]);
    const row = el.querySelector('.showdown-row');
    expect(row).not.toBeNull();
    expect(row!.textContent).toContain('WINNER');
    expect(row!.textContent).toContain('Alice won $0.50 (Pair of Aces)');
  });

  it('renders action messages with the action-row class and no special prefix', () => {
    const el = render([actionMsg('Bob folds')]);
    const row = el.querySelector('.action-row');
    expect(row).not.toBeNull();
    expect(row!.textContent).not.toContain('WINNER');
  });

  it('renders plain info messages without showdown or action classes', () => {
    const el = render([plainInfoMsg('Hand #1 started')]);
    expect(el.querySelector('.showdown-row')).toBeNull();
    expect(el.querySelector('.action-row')).toBeNull();
  });
});
```

- [ ] **Step 2: Run the test, expect failure**

Run: `npm test -- --testPathPattern=messages-panel`

Expected: FAIL — no `.showdown-row` or `.action-row` elements exist.

- [ ] **Step 3: Add helpers to the component**

Replace `src/app/game-lobby/messages-panel/messages-panel.component.ts`:

```ts
import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ClientGameMessage, UserMessageEvent } from '../../game/game-events';

type DisplayMessage = ClientGameMessage | UserMessageEvent;

@Component({
  selector: 'app-messages-panel',
  imports: [CommonModule],
  templateUrl: './messages-panel.component.html',
})
export class MessagesPanelComponent {
  @Input() messages: DisplayMessage[] = [];

  isShowdown(msg: DisplayMessage): boolean {
    return msg.eventType === 'game-message' && (msg as ClientGameMessage).kind === 'showdown';
  }

  isAction(msg: DisplayMessage): boolean {
    return msg.eventType === 'game-message' && (msg as ClientGameMessage).kind === 'action';
  }

  getMessageClass(msg: DisplayMessage): string {
    if (msg.eventType === 'user-message') {
      switch (msg.severity) {
        case 'ERROR': return 'text-error';
        case 'WARNING': return 'text-warning';
      }
    }
    return '';
  }
}
```

- [ ] **Step 4: Update the template**

Replace `src/app/game-lobby/messages-panel/messages-panel.component.html`:

```html
<div class="flex flex-col h-full">
  <h2 class="text-lg font-semibold mb-3 text-base-content/70 uppercase tracking-wider">Messages</h2>
  <div class="overflow-y-auto flex-1 flex flex-col">
    @for (msg of messages; track $index) {
      @if (isShowdown(msg)) {
        <div class="showdown-row py-1 px-2 text-sm border-b border-base-200 border-l-4 border-l-accent bg-accent/10">
          <span class="text-base-content/50 text-xs">{{ msg.timestamp | date:'h:mm:ss a' }}</span>
          <span class="ml-2 font-bold text-accent">WINNER:</span>
          <span class="ml-1">{{ msg.message }}</span>
        </div>
      } @else if (isAction(msg)) {
        <div class="action-row py-1 px-2 text-sm border-b border-base-200 text-base-content/70">
          <span class="text-base-content/50 text-xs">{{ msg.timestamp | date:'h:mm:ss a' }}</span>
          <span class="ml-2">{{ msg.message }}</span>
        </div>
      } @else {
        <div class="py-1 px-2 text-sm border-b border-base-200" [ngClass]="getMessageClass(msg)">
          <span class="text-base-content/50 text-xs">{{ msg.timestamp | date:'h:mm:ss a' }}</span>
          <span class="ml-2">{{ msg.message }}</span>
        </div>
      }
    } @empty {
      <div class="text-center text-base-content/50 py-8">No messages yet</div>
    }
  </div>
</div>
```

- [ ] **Step 5: Run the tests, expect pass**

Run: `npm test -- --testPathPattern=messages-panel`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/app/game-lobby/messages-panel/
git commit -m "feat(ui): distinct styling for showdown and action messages"
```

---

## Task 8: Pure `computeBadgeState` function

A pure mapping from `(seatPosition, TableState)` to a `BadgeState`. This is the bulk of the logic and is Phaser-free, so it can be tested in isolation.

**Files:**
- Create: `src/app/game-lobby/phaser-table/utils/action-badge-state.ts`
- Create: `src/app/game-lobby/phaser-table/utils/action-badge-state.spec.ts`

- [ ] **Step 1: Define the API**

Create `src/app/game-lobby/phaser-table/utils/action-badge-state.ts` with type definitions only (implementation will be `null` stubs):

```ts
import { TableState } from '../../../game/game-state.service';

export type BadgeKind =
  | 'none'
  | 'to-act'
  | 'fold'
  | 'check'
  | 'call'
  | 'bet'
  | 'raise'
  | 'all-in'
  | 'winner';

export interface BadgeState {
  kind: BadgeKind;
  /** Line 1 text. Empty when kind === 'none'. */
  line1: string;
  /** Line 2 text. Empty when not used (winner with hand description). */
  line2: string;
  /**
   * Monotonic per-seat freshness counter, used by the renderer to retrigger
   * fade animations on repeated actions. Equals tableState.actionSeq at the
   * moment the seat became the actor, or 0 otherwise.
   */
  actionSeq: number;
}

const NONE: BadgeState = { kind: 'none', line1: '', line2: '', actionSeq: 0 };

function formatCents(cents: number): string {
  return '$' + (cents / 100).toFixed(2);
}

export function computeBadgeState(
  seatPosition: number,
  tableState: TableState | null,
): BadgeState {
  // TODO: implement
  return NONE;
}
```

- [ ] **Step 2: Write the failing tests**

Create `src/app/game-lobby/phaser-table/utils/action-badge-state.spec.ts`:

```ts
import { computeBadgeState, BadgeState } from './action-badge-state';
import { TableState } from '../../../game/game-state.service';
import { SeatSummary } from '../../../game/game-events';

function makeTableState(overrides: Partial<TableState> = {}): TableState {
  return {
    tableId: 't1',
    tableStatus: 'ACTIVE',
    handNumber: 1,
    dealerPosition: 1,
    smallBlindPosition: 2,
    bigBlindPosition: 3,
    smallBlindAmount: 5,
    bigBlindAmount: 10,
    communityCards: [],
    pots: [],
    potTotal: 0,
    phase: 'PREFLOP',
    potResults: null,
    seatCards: new Map(),
    seatSummaries: new Map<number, SeatSummary>(),
    lastAction: null,
    actionPosition: null,
    actionDeadline: null,
    callAmount: 0,
    currentBet: 0,
    minimumRaise: 0,
    actionSeq: 0,
    ...overrides,
  };
}

function summary(
  seatPosition: number,
  status: SeatSummary['status'],
  chipCount: number,
  currentBetAmount: number,
): SeatSummary {
  return { seatPosition, userId: `u${seatPosition}`, status, chipCount, currentBetAmount };
}

describe('computeBadgeState', () => {
  it('returns none for a null table state', () => {
    expect(computeBadgeState(1, null).kind).toBe('none');
  });

  it('returns none for a seat that is neither to-act nor the most recent actor', () => {
    const ts = makeTableState({
      actionPosition: 2,
      lastAction: { seatPosition: 3, action: 'call' },
      actionSeq: 1,
    });
    expect(computeBadgeState(5, ts).kind).toBe('none');
  });

  it('returns to-act for the current action position with no lastAction', () => {
    const ts = makeTableState({ actionPosition: 2, lastAction: null });
    expect(computeBadgeState(2, ts)).toEqual<BadgeState>({
      kind: 'to-act', line1: 'TO ACT', line2: '', actionSeq: 0,
    });
  });

  it('returns to-act for the action position even when another seat acted last', () => {
    const ts = makeTableState({
      actionPosition: 2,
      lastAction: { seatPosition: 5, action: 'call' },
      actionSeq: 3,
    });
    expect(computeBadgeState(2, ts).kind).toBe('to-act');
  });

  it('shows the action (not to-act) when a seat is both actionPosition and the most recent actor', () => {
    // This brief window exists between `player-acted` and the next `action-on-player`:
    // actionPosition still points at the seat that just acted.
    const ts = makeTableState({
      actionPosition: 5,
      lastAction: { seatPosition: 5, action: 'fold' },
      actionSeq: 2,
    });
    expect(computeBadgeState(5, ts).kind).toBe('fold');
  });

  it('returns fold for the seat that just folded', () => {
    const ts = makeTableState({
      actionPosition: 3,
      lastAction: { seatPosition: 5, action: 'fold' },
      actionSeq: 4,
    });
    expect(computeBadgeState(5, ts)).toEqual<BadgeState>({
      kind: 'fold', line1: 'FOLD', line2: '', actionSeq: 4,
    });
  });

  it('returns check for the seat that just checked', () => {
    const ts = makeTableState({
      lastAction: { seatPosition: 5, action: 'check' },
      actionSeq: 7,
    });
    expect(computeBadgeState(5, ts).kind).toBe('check');
    expect(computeBadgeState(5, ts).line1).toBe('CHECK');
  });

  it('returns call with the chips spent for the seat that just called', () => {
    // Seat 5 had 100 chips, currentBetAmount 0. Now has 95 chips, currentBetAmount 5.
    const ts = makeTableState({
      lastAction: { seatPosition: 5, action: 'call' },
      actionSeq: 8,
      seatSummaries: new Map([[5, summary(5, 'ACTIVE', 95, 5)]]),
    });
    expect(computeBadgeState(5, ts)).toMatchObject({
      kind: 'call', line1: 'CALL $0.05',
    });
  });

  it('returns bet with the total bet amount', () => {
    const ts = makeTableState({
      lastAction: { seatPosition: 5, action: 'bet' },
      currentBet: 20,
      actionSeq: 9,
      seatSummaries: new Map([[5, summary(5, 'ACTIVE', 80, 20)]]),
    });
    expect(computeBadgeState(5, ts)).toMatchObject({
      kind: 'bet', line1: 'BET $0.20',
    });
  });

  it('returns raise with the new total bet amount', () => {
    const ts = makeTableState({
      lastAction: { seatPosition: 5, action: 'raise' },
      currentBet: 50,
      actionSeq: 10,
      seatSummaries: new Map([[5, summary(5, 'ACTIVE', 50, 50)]]),
    });
    expect(computeBadgeState(5, ts)).toMatchObject({
      kind: 'raise', line1: 'RAISE → $0.50',
    });
  });

  it('upgrades to all-in when the resulting status is ALL_IN', () => {
    const ts = makeTableState({
      lastAction: { seatPosition: 5, action: 'raise' },
      currentBet: 80,
      actionSeq: 11,
      seatSummaries: new Map([[5, summary(5, 'ALL_IN', 0, 80)]]),
    });
    expect(computeBadgeState(5, ts).kind).toBe('all-in');
    expect(computeBadgeState(5, ts).line1).toBe('ALL IN');
  });

  it('returns winner with WON $X.XX and hand description for a single-pot winner', () => {
    const ts = makeTableState({
      potResults: [
        { potIndex: 0, potAmount: 50, winners: [
          { seatPosition: 5, userId: 'u5', amount: 50, handDescription: 'Pair of Aces' },
        ]},
      ],
    });
    expect(computeBadgeState(5, ts)).toMatchObject({
      kind: 'winner', line1: 'WON $0.50', line2: 'Pair of Aces',
    });
  });

  it('labels side-pot wins with POT N when there are multiple pots', () => {
    const ts = makeTableState({
      potResults: [
        { potIndex: 0, potAmount: 30, winners: [
          { seatPosition: 3, userId: 'u3', amount: 30, handDescription: 'Two Pair' },
        ]},
        { potIndex: 1, potAmount: 20, winners: [
          { seatPosition: 5, userId: 'u5', amount: 20, handDescription: 'Pair of Aces' },
        ]},
      ],
    });
    expect(computeBadgeState(5, ts)).toMatchObject({
      kind: 'winner', line1: 'WON POT 2 — $0.20', line2: 'Pair of Aces',
    });
  });

  it('aggregates multi-pot wins for a single seat', () => {
    const ts = makeTableState({
      potResults: [
        { potIndex: 0, potAmount: 30, winners: [
          { seatPosition: 5, userId: 'u5', amount: 30, handDescription: 'Two Pair' },
        ]},
        { potIndex: 1, potAmount: 20, winners: [
          { seatPosition: 5, userId: 'u5', amount: 20, handDescription: 'Two Pair' },
        ]},
      ],
    });
    expect(computeBadgeState(5, ts)).toMatchObject({
      kind: 'winner', line1: 'WON $0.50 (2 pots)', line2: 'Two Pair',
    });
  });

  it('omits line 2 when the winner has no hand description (no-showdown win)', () => {
    const ts = makeTableState({
      potResults: [
        { potIndex: 0, potAmount: 50, winners: [
          { seatPosition: 5, userId: 'u5', amount: 50, handDescription: '' },
        ]},
      ],
    });
    expect(computeBadgeState(5, ts)).toMatchObject({
      kind: 'winner', line1: 'WON $0.50', line2: '',
    });
  });

  it('truncates line 2 over 28 chars with ellipsis', () => {
    const long = 'Full House, Aces full of Kings and More';
    const ts = makeTableState({
      potResults: [
        { potIndex: 0, potAmount: 50, winners: [
          { seatPosition: 5, userId: 'u5', amount: 50, handDescription: long },
        ]},
      ],
    });
    const result = computeBadgeState(5, ts);
    expect(result.line2.length).toBeLessThanOrEqual(28);
    expect(result.line2.endsWith('…')).toBe(true);
  });

  it('prefers winner over any in-progress action state', () => {
    const ts = makeTableState({
      actionPosition: 5,
      lastAction: { seatPosition: 5, action: 'call' },
      actionSeq: 9,
      potResults: [
        { potIndex: 0, potAmount: 50, winners: [
          { seatPosition: 5, userId: 'u5', amount: 50, handDescription: 'Pair of Aces' },
        ]},
      ],
    });
    expect(computeBadgeState(5, ts).kind).toBe('winner');
  });
});
```

- [ ] **Step 3: Run the tests, expect failure**

Run: `npm test -- --testPathPattern=action-badge-state`

Expected: most tests FAIL (current stub returns `none`).

- [ ] **Step 4: Implement the function**

Replace `src/app/game-lobby/phaser-table/utils/action-badge-state.ts`:

```ts
import { TableState } from '../../../game/game-state.service';
import { PotResult, Winner } from '../../../game/game-events';

export type BadgeKind =
  | 'none'
  | 'to-act'
  | 'fold'
  | 'check'
  | 'call'
  | 'bet'
  | 'raise'
  | 'all-in'
  | 'winner';

export interface BadgeState {
  kind: BadgeKind;
  line1: string;
  line2: string;
  actionSeq: number;
}

const NONE: BadgeState = { kind: 'none', line1: '', line2: '', actionSeq: 0 };
const MAX_DESC_CHARS = 28;

function formatCents(cents: number): string {
  return '$' + (cents / 100).toFixed(2);
}

function truncateDesc(s: string): string {
  if (s.length <= MAX_DESC_CHARS) return s;
  return s.substring(0, MAX_DESC_CHARS - 1) + '…';
}

function computeWinnerBadge(seatPos: number, potResults: PotResult[]): BadgeState | null {
  const wins: Array<{ pot: PotResult; winner: Winner }> = [];
  for (const pot of potResults) {
    for (const w of pot.winners) {
      if (w.seatPosition === seatPos) wins.push({ pot, winner: w });
    }
  }
  if (wins.length === 0) return null;

  const totalAmount = wins.reduce((sum, w) => sum + w.winner.amount, 0);
  const description = wins.reduce(
    (longest, w) => (w.pot.potAmount > (longest.pot.potAmount ?? 0) ? w : longest),
    wins[0],
  ).winner.handDescription;

  let line1: string;
  if (wins.length > 1) {
    line1 = `WON ${formatCents(totalAmount)} (${wins.length} pots)`;
  } else if (potResults.length > 1) {
    const potNumber = potResults.indexOf(wins[0].pot) + 1;
    line1 = `WON POT ${potNumber} — ${formatCents(wins[0].winner.amount)}`;
  } else {
    line1 = `WON ${formatCents(totalAmount)}`;
  }

  const line2 = description ? truncateDesc(description) : '';
  return { kind: 'winner', line1, line2, actionSeq: 0 };
}

export function computeBadgeState(
  seatPosition: number,
  tableState: TableState | null,
): BadgeState {
  if (!tableState) return NONE;

  // 1. Winner takes precedence over any in-progress action state.
  if (tableState.potResults && tableState.potResults.length > 0) {
    const winnerBadge = computeWinnerBadge(seatPosition, tableState.potResults);
    if (winnerBadge) return winnerBadge;
  }

  // 2. If this seat is the most recent actor, show their action. This must
  //    win over to-act because between `player-acted` and the next
  //    `action-on-player`, both flags are briefly true for this seat.
  const la = tableState.lastAction;
  if (la && la.seatPosition === seatPosition) {
    const seq = tableState.actionSeq;
    const seat = tableState.seatSummaries.get(seatPosition);
    const isAllIn = seat?.status === 'ALL_IN';
    const totalBet = seat?.currentBetAmount ?? 0;

    switch (la.action) {
    case 'fold':
      return { kind: 'fold', line1: 'FOLD', line2: '', actionSeq: seq };
    case 'check':
      return { kind: 'check', line1: 'CHECK', line2: '', actionSeq: seq };
    case 'call':
      // Display the total amount matched (the seat's currentBetAmount after
      // acting). This always equals the table's currentBet for a call and is
      // stable across subsequent action-on-player events.
      return isAllIn
        ? { kind: 'all-in', line1: 'ALL IN', line2: '', actionSeq: seq }
        : { kind: 'call', line1: `CALL ${formatCents(totalBet)}`, line2: '', actionSeq: seq };
    case 'bet':
      return isAllIn
        ? { kind: 'all-in', line1: 'ALL IN', line2: '', actionSeq: seq }
        : { kind: 'bet', line1: `BET ${formatCents(totalBet)}`, line2: '', actionSeq: seq };
    case 'raise':
      return isAllIn
        ? { kind: 'all-in', line1: 'ALL IN', line2: '', actionSeq: seq }
        : { kind: 'raise', line1: `RAISE → ${formatCents(totalBet)}`, line2: '', actionSeq: seq };
    }
    return NONE;
  }

  // 3. If this seat is the action position (and didn't just act), show TO ACT.
  if (tableState.actionPosition === seatPosition) {
    return { kind: 'to-act', line1: 'TO ACT', line2: '', actionSeq: 0 };
  }

  return NONE;
}
```

**Note on `call` display amount:** we show the seat's total `currentBetAmount` after the call (which equals the table's `currentBet`), not the chip delta of this action. Rationale: this value is stable across the next `action-on-player` event (chip delta would require keeping prior state we don't track), and "CALL $0.30" reading as "matched the $0.30 bet" is a natural poker UI convention.

- [ ] **Step 5: Run the tests, expect pass**

Run: `npm test -- --testPathPattern=action-badge-state`

Expected: PASS. If the call-amount test fails, adjust by reading the expected test value and updating the test fixture to set `callAmount: 5` in the table state.

If any tests fail, fix the implementation, not the tests (except where noted above for `callAmount`).

- [ ] **Step 6: Commit**

```bash
git add src/app/game-lobby/phaser-table/utils/action-badge-state.ts src/app/game-lobby/phaser-table/utils/action-badge-state.spec.ts
git commit -m "feat(phaser): pure computeBadgeState mapping from TableState"
```

---

## Task 9: Create `ActionBadge` Phaser game object

A two-line pill with state-driven fill colors, fade in/out, and a pulse mode.

**Files:**
- Create: `src/app/game-lobby/phaser-table/game-objects/action-badge.ts`

- [ ] **Step 1: Create the file**

Create `src/app/game-lobby/phaser-table/game-objects/action-badge.ts`:

```ts
import Phaser from 'phaser';
import { BadgeKind, BadgeState } from '../utils/action-badge-state';

interface PaletteEntry {
  bg: number;
  bgAlpha: number;
  border: number;
  textColor: string;
  pulse: boolean;
}

const PALETTE: Record<Exclude<BadgeKind, 'none'>, PaletteEntry> = {
  'to-act':  { bg: 0x000000, bgAlpha: 0.6, border: 0xf5d678, textColor: '#f5d678', pulse: false },
  'fold':    { bg: 0x4a1414, bgAlpha: 0.85, border: 0xb84141, textColor: '#ff8888', pulse: false },
  'check':   { bg: 0x000000, bgAlpha: 0.7, border: 0xcccccc, textColor: '#ffffff', pulse: false },
  'call':    { bg: 0x14304a, bgAlpha: 0.85, border: 0x4488cc, textColor: '#88bbff', pulse: false },
  'bet':     { bg: 0x3a2a0a, bgAlpha: 0.85, border: 0xf5d678, textColor: '#f5d678', pulse: false },
  'raise':   { bg: 0x3a2a0a, bgAlpha: 0.85, border: 0xf5d678, textColor: '#f5d678', pulse: false },
  'all-in':  { bg: 0x3a2a0a, bgAlpha: 0.9, border: 0xf5d678, textColor: '#ffe89a', pulse: true },
  'winner':  { bg: 0x3a2a0a, bgAlpha: 0.9, border: 0xf5d678, textColor: '#ffe89a', pulse: true },
};

const FADE_IN_MS = 150;
const FADE_OUT_MS = 200;
const ACTION_HOLD_MS = 2500;

const TEXT_LINE_GAP = 2;

/** Kinds that auto-fade after ACTION_HOLD_MS. Others are sticky. */
const STICKY_KINDS: BadgeKind[] = ['to-act', 'winner', 'none'];

export class ActionBadge extends Phaser.GameObjects.Container {
  private bg: Phaser.GameObjects.Graphics;
  private line1Text: Phaser.GameObjects.Text;
  private line2Text: Phaser.GameObjects.Text;
  private pulseTween: Phaser.Tweens.Tween | null = null;
  private fadeOutTimer: Phaser.Time.TimerEvent | null = null;
  private currentKind: BadgeKind = 'none';
  private currentSeq = -1;
  private fontSize = 11;

  constructor(scene: Phaser.Scene, x: number, y: number) {
    super(scene, x, y);
    scene.add.existing(this);

    this.bg = new Phaser.GameObjects.Graphics(scene);
    this.add(this.bg);

    this.line1Text = new Phaser.GameObjects.Text(scene, 0, 0, '', {
      fontFamily: 'system-ui, sans-serif',
      fontSize: `${this.fontSize}px`,
      fontStyle: 'bold',
      color: '#ffffff',
    }).setOrigin(0.5);
    this.add(this.line1Text);

    this.line2Text = new Phaser.GameObjects.Text(scene, 0, 0, '', {
      fontFamily: 'system-ui, sans-serif',
      fontSize: `${Math.max(8, this.fontSize - 2)}px`,
      color: '#ffffff',
    }).setOrigin(0.5);
    this.add(this.line2Text);

    this.setVisible(false);
    this.setAlpha(0);
  }

  setFontSize(px: number): void {
    this.fontSize = px;
    this.line1Text.setFontSize(px);
    this.line2Text.setFontSize(Math.max(8, px - 2));
  }

  applyState(state: BadgeState): void {
    if (state.kind === 'none') {
      this.hide();
      return;
    }

    // For action kinds, the actionSeq must change for us to retrigger the
    // fade animation; otherwise an idle re-render keeps the badge in its
    // current fade phase.
    const isAction = !STICKY_KINDS.includes(state.kind);
    if (
      this.currentKind === state.kind &&
      this.currentSeq === state.actionSeq &&
      !isAction // sticky kinds: skip re-show; action kinds: always re-show on same seq is fine (no-op)
    ) {
      return;
    }

    this.currentKind = state.kind;
    this.currentSeq = state.actionSeq;
    this.render(state);
  }

  hide(): void {
    if (this.currentKind === 'none' && !this.visible) return;
    this.currentKind = 'none';
    this.currentSeq = -1;
    this.cancelTimers();
    this.scene.tweens.killTweensOf(this);
    this.setAlpha(0);
    this.setVisible(false);
  }

  private cancelTimers(): void {
    if (this.fadeOutTimer) {
      this.fadeOutTimer.remove(false);
      this.fadeOutTimer = null;
    }
    if (this.pulseTween) {
      this.pulseTween.stop();
      this.pulseTween = null;
    }
  }

  private render(state: BadgeState): void {
    this.cancelTimers();
    this.scene.tweens.killTweensOf(this);

    const palette = PALETTE[state.kind as Exclude<BadgeKind, 'none'>];
    this.line1Text.setColor(palette.textColor);
    this.line2Text.setColor(palette.textColor);
    this.line1Text.setText(state.line1);
    this.line2Text.setText(state.line2);

    const hasLine2 = state.line2.length > 0;
    this.line1Text.setVisible(true);
    this.line2Text.setVisible(hasLine2);

    const padX = 8;
    const padY = 4;
    const lineGap = hasLine2 ? TEXT_LINE_GAP : 0;
    const totalTextH = this.line1Text.height + (hasLine2 ? this.line2Text.height + lineGap : 0);
    const totalTextW = Math.max(this.line1Text.width, hasLine2 ? this.line2Text.width : 0);
    const w = totalTextW + padX * 2;
    const h = totalTextH + padY * 2;

    this.line1Text.setPosition(0, -h / 2 + padY + this.line1Text.height / 2);
    if (hasLine2) {
      this.line2Text.setPosition(0, h / 2 - padY - this.line2Text.height / 2);
    }

    this.bg.clear();
    this.bg.fillStyle(palette.bg, palette.bgAlpha);
    this.bg.fillRoundedRect(-w / 2, -h / 2, w, h, Math.min(h / 2, 8));
    this.bg.lineStyle(1, palette.border, 1);
    this.bg.strokeRoundedRect(-w / 2, -h / 2, w, h, Math.min(h / 2, 8));

    this.setVisible(true);
    this.scene.tweens.add({
      targets: this,
      alpha: 1,
      duration: FADE_IN_MS,
      ease: 'Quad.easeOut',
    });

    if (palette.pulse) {
      this.pulseTween = this.scene.tweens.add({
        targets: this,
        alpha: { from: 1, to: 0.85 },
        duration: 1200,
        yoyo: true,
        repeat: -1,
        ease: 'Sine.easeInOut',
      });
    }

    if (!STICKY_KINDS.includes(state.kind)) {
      this.fadeOutTimer = this.scene.time.delayedCall(ACTION_HOLD_MS, () => {
        this.scene.tweens.add({
          targets: this,
          alpha: 0,
          duration: FADE_OUT_MS,
          ease: 'Quad.easeIn',
          onComplete: () => {
            this.setVisible(false);
            this.currentKind = 'none';
          },
        });
      });
    }
  }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `npm test -- --testPathPattern=action-badge-state`

Expected: still PASS — this task adds an unreferenced file, so it should not break any existing tests. If TypeScript reports an error on the unused import or palette index, fix it.

- [ ] **Step 3: Commit**

```bash
git add src/app/game-lobby/phaser-table/game-objects/action-badge.ts
git commit -m "feat(phaser): ActionBadge game object with fade/pulse states"
```

---

## Task 10: Folded-seat dimming + badge anchor on `SeatDisplay`

**Files:**
- Modify: `src/app/game-lobby/phaser-table/game-objects/seat-display.ts`

- [ ] **Step 1: Extend the `updateSeat` signature**

Currently `SeatDisplay.updateSeat` takes `(name, chipCount, cards, isActive)`. Add an `isFolded` flag and an output for badge anchor coordinates.

In `src/app/game-lobby/phaser-table/game-objects/seat-display.ts`:

Add a new constant near the top:
```ts
const POD_BG_FOLDED_ALPHA = 0.4;
const FOLDED_TEXT_COLOR = 'rgba(255,255,255,0.35)';
const FOLDED_TAG_GAP = 2;
```

Add a new property:
```ts
private foldedTag: Phaser.GameObjects.Text;
private lastPodH = 0;
```

In the constructor, after creating `stackText`:
```ts
this.foldedTag = new Phaser.GameObjects.Text(scene, 0, 0, 'FOLDED', {
  fontFamily: 'system-ui, sans-serif',
  fontSize: '9px',
  fontStyle: 'bold',
  color: FOLDED_TEXT_COLOR,
}).setOrigin(0, 0.5);
this.add(this.foldedTag);
this.foldedTag.setVisible(false);
```

Change the signature of `updateSeat`:
```ts
updateSeat(
  playerName: string | null,
  chipCount: number | null,
  cards: SeatCard[] | null,
  isActive: boolean,
  isFolded: boolean,
): void {
  if (!playerName) {
    this.renderEmpty();
    return;
  }
  this.renderOccupied(playerName, chipCount, cards, isActive, isFolded);
}
```

Change `renderOccupied`'s signature and body to accept `isFolded`:

Inside `renderOccupied`, after `this.bg.lineStyle(...)` lines:

Replace:
```ts
this.bg.fillStyle(POD_BG, POD_BG_ALPHA);
```
With:
```ts
const podAlpha = isFolded ? POD_BG_FOLDED_ALPHA : POD_BG_ALPHA;
this.bg.fillStyle(POD_BG, podAlpha);
```

After the existing `this.card2.setCardSize(...)` block, add the folded-tag positioning and card tinting:

```ts
// Folded tag below the stack text
if (isFolded) {
  this.foldedTag.setFontSize(Math.max(8, Math.round(stackFontSize * 0.85)));
  this.foldedTag.setPosition(textX, nameFontSize / 2 + stackFontSize + FOLDED_TAG_GAP);
  this.foldedTag.setVisible(true);
} else {
  this.foldedTag.setVisible(false);
}

// Greyscale tint on hole cards when folded
const cardTint = isFolded ? 0x808080 : 0xffffff;
this.card1.setTint(cardTint);
this.card2.setTint(cardTint);

this.lastPodH = podH;
```

`CardSprite` already extends `Phaser.GameObjects.Container` — confirm it supports `setTint` by inspecting `card-sprite.ts`. If it does not, add a public `setTint(color: number)` method that calls `setTint` on each internal sprite. (See Task 10a below.)

In `renderEmpty`, hide `foldedTag`:
```ts
this.foldedTag.setVisible(false);
```

Add a public method exposing the badge anchor (positioned just above the pod, in seat-local coordinates):

```ts
/**
 * Returns the local-space coordinates where an external badge should be
 * anchored (above the pod, centered horizontally). Caller adds seat.x/y for
 * world space.
 */
getBadgeAnchor(): { x: number; y: number } {
  return { x: 0, y: -this.lastPodH / 2 - 14 };
}
```

- [ ] **Step 2: Verify CardSprite supports tinting**

Open `src/app/game-lobby/phaser-table/game-objects/card-sprite.ts` and look for a `setTint` method.

If it does NOT have one, add it. The most common shape:

```ts
setTint(color: number): void {
  // Apply to all child sprites that support tinting.
  this.iterate((child: Phaser.GameObjects.GameObject) => {
    const tintable = child as Phaser.GameObjects.GameObject & { setTint?: (c: number) => void };
    if (typeof tintable.setTint === 'function') {
      tintable.setTint(color);
    }
  });
}
```

If `CardSprite` is not a `Container` but renders cards as `Phaser.GameObjects.Text` (for suit glyphs), the same `setTint` approach works because `Text` supports `setTint` since Phaser 3.50.

- [ ] **Step 3: Run the full test suite**

Run: `npm test`

Expected: existing tests pass. The `SeatDisplay` change has no unit tests today (visual component) so we rely on the rest of the suite remaining green.

If there are TypeScript errors about callers passing 4 arguments to `updateSeat`, that's expected — Task 11 will update the caller. For now, find the caller in `poker-table.scene.ts` and add a temporary `false` as the 5th argument:

```ts
this.seats[idx].updateSeat(name, player.chipCount, cards, isActive, false);
// ...
this.seats[idx].updateSeat(null, null, null, false, false);
```

Re-run `npm test`. Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/app/game-lobby/phaser-table/game-objects/seat-display.ts src/app/game-lobby/phaser-table/game-objects/card-sprite.ts src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts
git commit -m "feat(phaser): folded-seat dimming + badge anchor on SeatDisplay"
```

---

## Task 11: Wire `ActionBadge` into `PokerTableScene`

**Files:**
- Modify: `src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts`

- [ ] **Step 1: Add the import and array**

In `poker-table.scene.ts`, add:

```ts
import { ActionBadge } from '../game-objects/action-badge';
import { computeBadgeState } from '../utils/action-badge-state';
```

Add a new field:
```ts
private actionBadges: ActionBadge[] = [];
```

In `create()`, alongside the seat-creation loop:

```ts
for (let i = 0; i < MAX_SEATS; i++) {
  const seat = new SeatDisplay(this, 0, 0);
  seat.setDepth(5);
  this.seats.push(seat);
  const chip = new BetChip(this, 0, 0);
  chip.setDepth(4);
  this.betChips.push(chip);
  const badge = new ActionBadge(this, 0, 0);
  badge.setDepth(6);
  this.actionBadges.push(badge);
}
```

- [ ] **Step 2: Lay the badges out**

In `layoutAll()`, after the seat-positioning loop, add:

```ts
const badgeFontSize = Math.max(10, Math.round(width * 0.008));
for (let i = 0; i < MAX_SEATS; i++) {
  this.actionBadges[i].setFontSize(badgeFontSize);
  const anchor = this.seats[i].getBadgeAnchor();
  this.actionBadges[i].setPosition(
    this.seatPositions[i].x + anchor.x,
    this.seatPositions[i].y + anchor.y,
  );
}
```

- [ ] **Step 3: Pass `isFolded` and drive badges from `renderState`**

Inside `renderState()`, in the seat loop:

```ts
for (let pos = 1; pos <= MAX_SEATS; pos++) {
  const idx = pos - 1;
  const player = seatPlayerMap.get(pos);
  const rawCards = ts.seatCards.get(pos) ?? null;
  const isActive = ts.actionPosition === pos;
  const summary = ts.seatSummaries.get(pos);
  const isFolded = summary?.status === 'FOLDED';

  if (player) {
    const isLocalUser = player.userId === this.currentUserId;
    const name = isLocalUser ? 'You' : player.displayName;
    const cards = rawCards && isLocalUser
      ? rawCards.map((c) => ({ ...c, showCard: true }))
      : rawCards;
    this.seats[idx].updateSeat(name, player.chipCount, cards, isActive, isFolded);
  } else {
    this.seats[idx].updateSeat(null, null, null, false, false);
  }

  this.betChips[idx].setAmount(summary?.currentBetAmount ?? 0);

  // Drive the action badge from the pure mapping.
  const badgeState = computeBadgeState(pos, ts);
  if (player) {
    this.actionBadges[idx].applyState(badgeState);
  } else {
    this.actionBadges[idx].hide();
  }
}
```

In the `if (!ts) { ... }` early-return at the top of `renderState`, hide all badges:

```ts
if (!ts) {
  for (const seat of this.seats) seat.updateSeat(null, null, null, false, false);
  for (const chip of this.betChips) chip.setAmount(0);
  for (const badge of this.actionBadges) badge.hide();
  this.communityCards.updateCards([]);
  this.potDisplay.updatePots([]);
  this.dealerButton.hide();
  return;
}
```

Update the call site after the seat update earlier (you added a temporary `false` argument in Task 10 — verify all call sites use the correct `isFolded` value now).

- [ ] **Step 4: Run the build and the full test suite**

Run: `npm test`

Expected: PASS.

Run: `npm run build`

Expected: successful build with no TypeScript errors.

- [ ] **Step 5: Commit**

```bash
git add src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts
git commit -m "feat(phaser): wire ActionBadge into PokerTableScene"
```

---

## Task 12: Manual browser verification

This feature is visual; type-checking and the test suite verify code correctness but not feature correctness. Walk through the scenarios below in a real browser before considering the work complete.

**Prereq:** The backend at `http://localhost:8080` must be running. Start the dev server in another terminal:

```bash
npm start
```

Open `http://localhost:4200`, log in, and join an active game.

- [ ] **Step 1: Action feedback golden path**

With at least two seated players, play through one betting round and observe:
- [ ] Each action displays a per-seat badge (FOLD / CHECK / CALL $X / BET $X / RAISE → $X / ALL IN)
- [ ] Badges fade in ~150ms and auto-fade after ~2.5s
- [ ] When a new player acts, the previous badge disappears (only one action badge visible at a time)
- [ ] The seat on the clock shows "TO ACT" until they act

- [ ] **Step 2: Folded seat treatment**

- [ ] After a player folds, their pod becomes visibly dimmer (~half opacity)
- [ ] Their hole cards (back-of-card view) are greyscale and slightly smaller
- [ ] A small "FOLDED" tag appears under their stack
- [ ] On the next hand-started, the pod returns to full brightness

- [ ] **Step 3: Winner highlight, simple case**

- [ ] At showdown with a single pot, the winning seat gets a pulsing gold glow
- [ ] A two-line badge above their pod shows `WON $X.XX` / hand description
- [ ] Highlight persists from `showdown-result` until the next `hand-started`

- [ ] **Step 4: Winner highlight, multiple pots**

This is harder to test locally. If the backend supports forcing a side-pot scenario (e.g. one player shoves all-in for less and is called by two larger stacks), trigger it. Otherwise, note any side-pot test instructions for the backend team.

Expected: the side-pot winner's badge reads `WON POT 2 — $X.XX` / hand description.

- [ ] **Step 5: Message log**

- [ ] Each action produces a log line (e.g., "Alice folds", "Bob raises to $0.20")
- [ ] Winner lines stand out: gold left border, bold `WINNER:` prefix, light gold background
- [ ] Plain info lines (e.g. "Hand #2 started") render in the default style

- [ ] **Step 6: Edge case — no-showdown win**

When everyone folds except one player:
- [ ] Winner badge shows `WON $X.XX` with no second line
- [ ] Log line reads `Alice won $X.XX` with no `(hand)` suffix

- [ ] **Step 7: Regressions check**

Verify nothing else broke:
- [ ] Existing "active player" golden glow still works
- [ ] Timer bar under the active seat still ticks down
- [ ] Bet chips on the felt still update correctly
- [ ] Dealer button still positions correctly

- [ ] **Step 8: Document any UI rough edges**

If badge positioning collides for top-of-screen seats, or hand descriptions truncate awkwardly, note them in a follow-up file `docs/superpowers/notes/action-feedback-polish.md` and commit. Do not block the merge on these unless they are visually broken.

---

## Definition of Done

- [ ] All 11 implementation tasks complete and committed
- [ ] `npm test` passes (all suites green)
- [ ] `npm run build` succeeds with no TypeScript errors
- [ ] Manual verification (Task 12) checklist complete
- [ ] No new lint warnings on touched files
