# Pot-Winner Highlight Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** At showdown, move the winner announcement from the player pod to a center-of-table banner, glow the winning seat, and highlight the 5 cards that made the hand — cycling one winning hand at a time across pots (smallest side pot → main pot last), ~4s per stage, aborting immediately when a new hand starts.

**Architecture:** A tested pure-logic seam (`buildShowdownStages`, `matchWinningCards`, banner-text formatters) decides *what* to show; thin Phaser game objects (`WinnerBanner`, plus highlight methods on `CardSprite`/`SeatDisplay`/`CommunityCards`) render it; a small presenter in `PokerTableScene` drives stage timers and abort. The winner is removed from the pod `ActionBadge`, which then becomes dead code and is deleted.

**Tech Stack:** Angular 21 (standalone), Phaser 3, RxJS `BehaviorSubject`, Jest. All work is in `src/app/game-lobby/phaser-table/` plus one type addition in `src/app/game/game-events.ts`.

**Spec:** `docs/superpowers/specs/2026-05-31-pot-winner-highlight-design.md`

---

## Baseline / Preconditions (read before starting)

Verified on `main` at plan-authoring time, with **no source changes applied**:

- **`npm run build` passes** (warnings only — CommonJS `phaser`/`dayjs` bailouts; harmless). This is the **reliable type-check gate** for every task.
- **`npm test` (full suite) is RED at baseline**: ~10 failures across `app.component`, `title-page`, `modal`, `navigation-bar`, and `action-panel` specs — stale `TableState` mocks (missing `actionSeq`) and unrelated DI/template errors. **None of these files are touched by this plan.** Do not try to fix them here; do not treat them as regressions.
- A **nested git worktree** exists at `.worktrees/event-sequence-numbers/` and Jest scans it, double-counting and double-failing specs. Ignore it.
- **The suite this plan extends is GREEN**: `npx jest src/app/game-lobby/phaser-table` → 19/19 passing.

**Consequence for gates:** this plan uses **scoped** Jest runs (`npm test -- <pattern>`) plus `npm run build`, never a bare full-suite `npm test` as a pass/fail gate. A scoped run executes only the relevant spec(s) and stays green-or-red on this feature's own merits.

---

## File Structure

| File | Responsibility | Change |
|------|----------------|--------|
| `src/app/game/game-events.ts` | Wire types | **Modify** — add `winningCards: Card[]` to `Winner` |
| `src/app/game-lobby/phaser-table/utils/showdown-sequence.ts` | Pure: order pots, expand split winners into stages, format banner lines | **Create** |
| `src/app/game-lobby/phaser-table/utils/showdown-sequence.spec.ts` | Tests for above | **Create** |
| `src/app/game-lobby/phaser-table/utils/card-match.ts` | Pure: resolve winning cards → board/hole sprite indices | **Create** |
| `src/app/game-lobby/phaser-table/utils/card-match.spec.ts` | Tests for above | **Create** |
| `src/app/game-lobby/phaser-table/game-objects/card-sprite.ts` | Single card render | **Modify** — add `setHighlight`/`setDimmed` |
| `src/app/game-lobby/phaser-table/game-objects/community-cards.ts` | Board cards | **Modify** — add `applyShowdownHighlight` |
| `src/app/game-lobby/phaser-table/game-objects/seat-display.ts` | Player pod | **Modify** — add `setWinnerHighlight`/`applyShowdownHighlight` |
| `src/app/game-lobby/phaser-table/game-objects/winner-banner.ts` | Center banner | **Create** |
| `src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts` | Presenter: trigger/timers/abort, banner, highlights; drop `ActionBadge` | **Modify** |
| `src/app/game-lobby/phaser-table/utils/action-badge-state.ts` | Pure action mapping | **Modify** — remove `'winner'` kind + `computeWinnerBadge` |
| `src/app/game-lobby/phaser-table/utils/action-badge-state.spec.ts` | Tests | **Modify** — remove winner cases |
| `src/app/game-lobby/phaser-table/game-objects/action-badge.ts` | Pod badge (now unused) | **Delete** |

**Why these boundaries:** the value-bearing decisions (pot ordering, split expansion, card matching, label/amount formatting) live in pure functions that are fully unit-testable without a Phaser canvas. Phaser objects only render what they're told. The scene wires them together. This keeps the untestable Phaser surface as thin as possible.

**Phaser unit-testing note:** Phaser `GameObjects` need a live scene/WebGL canvas, which the Jest setup does not provide (the only existing phaser-table spec tests a pure util). Tasks that touch `card-sprite.ts`, `community-cards.ts`, `seat-display.ts`, `winner-banner.ts`, and the scene are therefore verified by **building and running the app**, not by unit tests. The pure tasks (2, 3) and the regression task (9) carry the automated coverage.

---

### Task 1: Add `winningCards` to the `Winner` wire type

**Files:**
- Modify: `src/app/game/game-events.ts:248-253`

- [ ] **Step 1: Add the field**

In `src/app/game/game-events.ts`, the `Winner` interface currently reads:

```ts
export interface Winner {
  seatPosition: number;
  userId: string;
  amount: number;
  handDescription: string;
}
```

Change it to:

```ts
export interface Winner {
  seatPosition: number;
  userId: string;
  amount: number;
  handDescription: string;
  /**
   * The concrete cards (value + suit) forming the winner's best five-card hand,
   * so clients can highlight them. Empty when the pot was won without a showdown
   * (e.g. everyone else folded). Matches the server `showdown-result` payload.
   */
  winningCards: Card[];
}
```

`Card` is already imported at the top of the file (`import { Card } from '../poker/poker-models';`). No other change is needed here — `game-state.service.ts`'s `showdown-result` handler spreads `event.potResults` wholesale, so `winningCards` rides along automatically.

- [ ] **Step 2: Verify the project still type-checks**

Run: `npm run build`
Expected: build succeeds. (Existing code that constructs `Winner` objects lives only in `action-badge-state.spec.ts`, which we revise in Task 9; until then those literals will be missing `winningCards`. If the build fails *only* in that spec file, that is expected and resolved in Task 9. If it fails elsewhere, stop and investigate.)

> Note for the executor: if `npm run build` fails in `action-badge-state.spec.ts` at this point, that is acceptable — proceed. `npm test` is the gate that must be green at the end of each task; the winner specs there are removed in Task 9. To keep Task 1 self-contained and green, you may run only the non-spec build check, or proceed directly — the suite is made green in Task 9.

- [ ] **Step 3: Commit**

```bash
git add src/app/game/game-events.ts
git commit -m "feat(game): add winningCards to Winner showdown payload type"
```

---

### Task 2: `buildShowdownStages` + banner formatters (pure)

**Files:**
- Create: `src/app/game-lobby/phaser-table/utils/showdown-sequence.ts`
- Test: `src/app/game-lobby/phaser-table/utils/showdown-sequence.spec.ts`

- [ ] **Step 1: Write the failing test**

Create `src/app/game-lobby/phaser-table/utils/showdown-sequence.spec.ts`:

```ts
import {
  buildShowdownStages,
  formatStageLine1,
  formatStageLine2,
  ShowdownStage,
} from './showdown-sequence';
import { PotResult } from '../../../game/game-events';
import { Card, CardSuit, CardValue } from '../../../poker/poker-models';

function card(value: CardValue, suit: CardSuit): Card {
  return { value, suit };
}

describe('buildShowdownStages', () => {
  it('returns a single unlabelled stage for a single-pot single-winner hand', () => {
    const pots: PotResult[] = [
      {
        potIndex: 0,
        potAmount: 50,
        winners: [
          {
            seatPosition: 5,
            userId: 'u5',
            amount: 50,
            handDescription: 'Pair of Aces',
            winningCards: [card(CardValue.ACE, CardSuit.SPADE)],
          },
        ],
      },
    ];
    const stages = buildShowdownStages(pots);
    expect(stages).toHaveLength(1);
    expect(stages[0]).toMatchObject<Partial<ShowdownStage>>({
      potIndex: 0,
      potLabel: '',
      seatPosition: 5,
      userId: 'u5',
      amount: 50,
      handDescription: 'Pair of Aces',
      isSplit: false,
    });
    expect(stages[0].winningCards).toHaveLength(1);
  });

  it('orders side pots ascending by amount, with the main pot last', () => {
    const pots: PotResult[] = [
      { potIndex: 0, potAmount: 100, winners: [w(1)] },
      { potIndex: 2, potAmount: 80, winners: [w(2)] },
      { potIndex: 1, potAmount: 20, winners: [w(3)] },
    ];
    const stages = buildShowdownStages(pots);
    // Side pots by amount ascending (20 then 80), then main pot (100) last.
    expect(stages.map((s) => s.potIndex)).toEqual([1, 2, 0]);
  });

  it('labels stages Main Pot / Side Pot N when there is more than one pot', () => {
    const pots: PotResult[] = [
      { potIndex: 0, potAmount: 100, winners: [w(1)] },
      { potIndex: 1, potAmount: 20, winners: [w(3)] },
    ];
    const stages = buildShowdownStages(pots);
    const byIndex = new Map(stages.map((s) => [s.potIndex, s.potLabel]));
    expect(byIndex.get(1)).toBe('Side Pot 1');
    expect(byIndex.get(0)).toBe('Main Pot');
  });

  it('expands a split pot into one stage per winner, flagged isSplit', () => {
    const pots: PotResult[] = [
      {
        potIndex: 0,
        potAmount: 100,
        winners: [w(3), w(7)],
      },
    ];
    const stages = buildShowdownStages(pots);
    expect(stages).toHaveLength(2);
    expect(stages.map((s) => s.seatPosition)).toEqual([3, 7]);
    expect(stages.every((s) => s.isSplit)).toBe(true);
  });

  it('defaults winningCards to an empty array when the field is absent', () => {
    const pots = [
      {
        potIndex: 0,
        potAmount: 50,
        winners: [
          { seatPosition: 1, userId: 'u1', amount: 50, handDescription: '' },
        ],
      },
    ] as unknown as PotResult[];
    const stages = buildShowdownStages(pots);
    expect(stages[0].winningCards).toEqual([]);
  });
});

describe('formatStageLine1', () => {
  const base: ShowdownStage = {
    potIndex: 0,
    potLabel: '',
    seatPosition: 5,
    userId: 'u5',
    amount: 480,
    handDescription: 'Two Pair',
    isSplit: false,
    winningCards: [],
  };

  it('formats name + amount for a single pot', () => {
    expect(formatStageLine1(base, 'BigSlick')).toBe('BigSlick wins $4.80');
  });

  it('prefixes the pot label when present', () => {
    expect(formatStageLine1({ ...base, potLabel: 'Side Pot 1' }, 'BigSlick')).toBe(
      'Side Pot 1 — BigSlick wins $4.80',
    );
  });

  it('annotates split pots', () => {
    expect(formatStageLine1({ ...base, isSplit: true }, 'BigSlick')).toBe(
      'BigSlick wins $4.80 (split)',
    );
  });
});

describe('formatStageLine2', () => {
  it('returns the hand description unchanged when short', () => {
    expect(
      formatStageLine2({ handDescription: 'Full House' } as ShowdownStage),
    ).toBe('Full House');
  });

  it('returns empty string when there is no description', () => {
    expect(formatStageLine2({ handDescription: '' } as ShowdownStage)).toBe('');
  });

  it('truncates very long descriptions with an ellipsis', () => {
    const long = 'Full House, Aces full of Kings and then some extra words';
    const out = formatStageLine2({ handDescription: long } as ShowdownStage);
    expect(out.length).toBeLessThanOrEqual(40);
    expect(out.endsWith('…')).toBe(true);
  });
});

// helper: minimal winner with required winningCards
function w(seatPosition: number) {
  return {
    seatPosition,
    userId: `u${seatPosition}`,
    amount: 10,
    handDescription: 'Pair',
    winningCards: [],
  };
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- showdown-sequence`
Expected: FAIL — `Cannot find module './showdown-sequence'`.

- [ ] **Step 3: Write the implementation**

Create `src/app/game-lobby/phaser-table/utils/showdown-sequence.ts`:

```ts
import { PotResult } from '../../../game/game-events';
import { Card } from '../../../poker/poker-models';

/** One winning hand to present on the table. A split pot produces one stage per winner. */
export interface ShowdownStage {
  /** Server pot index (0 = main pot). */
  potIndex: number;
  /** '' when the hand had a single pot; otherwise 'Main Pot' / 'Side Pot N'. */
  potLabel: string;
  seatPosition: number;
  userId: string;
  /** Chips awarded to this winner from this pot (cents). */
  amount: number;
  handDescription: string;
  /** True when this pot had more than one winner. */
  isSplit: boolean;
  /** The 5 cards of the best hand; empty for a fold-win (no showdown). */
  winningCards: Card[];
}

const MAX_DESC_CHARS = 40;

function formatCents(cents: number): string {
  return '$' + (cents / 100).toFixed(2);
}

/**
 * Build the ordered list of stages from a showdown's pot results.
 * Order: side pots first (ascending by amount), then the main pot (potIndex 0) last.
 * Each winner of a pot becomes its own stage so only one hand is shown at a time.
 */
export function buildShowdownStages(potResults: PotResult[]): ShowdownStage[] {
  const multiPot = potResults.length > 1;
  const main = potResults.filter((p) => p.potIndex === 0);
  const side = potResults
    .filter((p) => p.potIndex !== 0)
    .sort((a, b) => a.potAmount - b.potAmount);
  const ordered = [...side, ...main];

  const stages: ShowdownStage[] = [];
  for (const pot of ordered) {
    const isSplit = pot.winners.length > 1;
    const potLabel = multiPot
      ? pot.potIndex === 0
        ? 'Main Pot'
        : `Side Pot ${pot.potIndex}`
      : '';
    for (const winner of pot.winners) {
      stages.push({
        potIndex: pot.potIndex,
        potLabel,
        seatPosition: winner.seatPosition,
        userId: winner.userId,
        amount: winner.amount,
        handDescription: winner.handDescription,
        isSplit,
        winningCards: winner.winningCards ?? [],
      });
    }
  }
  return stages;
}

/** Banner line 1: "<label> — <name> wins $X.XX (split)". */
export function formatStageLine1(stage: ShowdownStage, displayName: string): string {
  let s = `${displayName} wins ${formatCents(stage.amount)}`;
  if (stage.isSplit) s += ' (split)';
  return stage.potLabel ? `${stage.potLabel} — ${s}` : s;
}

/** Banner line 2: the hand description, truncated. Empty for fold-wins. */
export function formatStageLine2(stage: ShowdownStage): string {
  const d = stage.handDescription ?? '';
  if (d.length <= MAX_DESC_CHARS) return d;
  return d.substring(0, MAX_DESC_CHARS - 1) + '…';
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- showdown-sequence`
Expected: PASS (all describe blocks green).

- [ ] **Step 5: Commit**

```bash
git add src/app/game-lobby/phaser-table/utils/showdown-sequence.ts src/app/game-lobby/phaser-table/utils/showdown-sequence.spec.ts
git commit -m "feat(phaser): pure showdown-stage builder and banner formatters"
```

---

### Task 3: `matchWinningCards` (pure)

**Files:**
- Create: `src/app/game-lobby/phaser-table/utils/card-match.ts`
- Test: `src/app/game-lobby/phaser-table/utils/card-match.spec.ts`

- [ ] **Step 1: Write the failing test**

Create `src/app/game-lobby/phaser-table/utils/card-match.spec.ts`:

```ts
import { matchWinningCards } from './card-match';
import { Card, CardSuit, CardValue } from '../../../poker/poker-models';
import { SeatCard } from '../../../game/game-models';

function c(value: CardValue, suit: CardSuit): Card {
  return { value, suit };
}
function hole(value: CardValue, suit: CardSuit): SeatCard {
  return { card: c(value, suit), showCard: true };
}

describe('matchWinningCards', () => {
  const board: Card[] = [
    c(CardValue.ACE, CardSuit.HEART),
    c(CardValue.KING, CardSuit.SPADE),
    c(CardValue.KING, CardSuit.CLUB),
    c(CardValue.SEVEN, CardSuit.DIAMOND),
    c(CardValue.TWO, CardSuit.SPADE),
  ];

  it('matches winning cards across the board and the winner hole cards', () => {
    const holeCards = [hole(CardValue.ACE, CardSuit.SPADE), hole(CardValue.ACE, CardSuit.DIAMOND)];
    // Best hand: AA (hole) + A-K-K (board) — two pair Aces & Kings.
    const winning = [
      c(CardValue.ACE, CardSuit.SPADE),
      c(CardValue.ACE, CardSuit.DIAMOND),
      c(CardValue.ACE, CardSuit.HEART),
      c(CardValue.KING, CardSuit.SPADE),
      c(CardValue.KING, CardSuit.CLUB),
    ];
    const result = matchWinningCards(winning, board, holeCards);
    expect(result.communityIndices.sort()).toEqual([0, 1, 2]);
    expect(result.holeSeatCardIndices.sort()).toEqual([0, 1]);
  });

  it('matches a hand that plays entirely from the board', () => {
    const holeCards = [hole(CardValue.THREE, CardSuit.CLUB), hole(CardValue.FOUR, CardSuit.CLUB)];
    const winning = [...board];
    const result = matchWinningCards(winning, board, holeCards);
    expect(result.communityIndices.sort()).toEqual([0, 1, 2, 3, 4]);
    expect(result.holeSeatCardIndices).toEqual([]);
  });

  it('returns empty targets for an empty winning-cards list (fold-win)', () => {
    const result = matchWinningCards([], board, [hole(CardValue.ACE, CardSuit.SPADE)]);
    expect(result.communityIndices).toEqual([]);
    expect(result.holeSeatCardIndices).toEqual([]);
  });

  it('skips a winning card that cannot be found on the table', () => {
    const winning = [c(CardValue.QUEEN, CardSuit.HEART)]; // not on board or in hole
    const result = matchWinningCards(winning, board, null);
    expect(result.communityIndices).toEqual([]);
    expect(result.holeSeatCardIndices).toEqual([]);
  });

  it('tolerates null hole cards', () => {
    const winning = [c(CardValue.KING, CardSuit.SPADE)];
    const result = matchWinningCards(winning, board, null);
    expect(result.communityIndices).toEqual([1]);
    expect(result.holeSeatCardIndices).toEqual([]);
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- card-match`
Expected: FAIL — `Cannot find module './card-match'`.

- [ ] **Step 3: Write the implementation**

Create `src/app/game-lobby/phaser-table/utils/card-match.ts`:

```ts
import { Card } from '../../../poker/poker-models';
import { SeatCard } from '../../../game/game-models';

export interface CardHighlightTargets {
  /** Indices into the community-card row to highlight. */
  communityIndices: number[];
  /** Indices (0/1) into the winner's hole cards to highlight. */
  holeSeatCardIndices: number[];
}

function sameCard(a: Card, b: Card): boolean {
  return a.value === b.value && a.suit === b.suit;
}

/**
 * Resolve a winner's `winningCards` to the on-table sprites that should glow.
 * Each card (value + suit) is unique, so it lands in at most one place: the
 * shared board, or the winner's hole cards. Cards that match nothing are skipped.
 */
export function matchWinningCards(
  winningCards: Card[],
  communityCards: Card[],
  holeCards: SeatCard[] | null,
): CardHighlightTargets {
  const communityIndices: number[] = [];
  const holeSeatCardIndices: number[] = [];

  for (const wc of winningCards) {
    const ci = communityCards.findIndex((c) => sameCard(c, wc));
    if (ci >= 0) {
      if (!communityIndices.includes(ci)) communityIndices.push(ci);
      continue;
    }
    if (holeCards) {
      const hi = holeCards.findIndex((sc) => sameCard(sc.card, wc));
      if (hi >= 0 && !holeSeatCardIndices.includes(hi)) holeSeatCardIndices.push(hi);
    }
  }

  return { communityIndices, holeSeatCardIndices };
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- card-match`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/app/game-lobby/phaser-table/utils/card-match.ts src/app/game-lobby/phaser-table/utils/card-match.spec.ts
git commit -m "feat(phaser): pure winning-card to sprite-index matcher"
```

---

### Task 4: Card highlight + dim on `CardSprite`

**Files:**
- Modify: `src/app/game-lobby/phaser-table/game-objects/card-sprite.ts`

No unit test (Phaser object — verified by running the app in Task 8).

- [ ] **Step 1: Add the gold-glow constant**

In `src/app/game-lobby/phaser-table/game-objects/card-sprite.ts`, after the line `const BACK_COLOR_2 = 0x0e3d5e;` add:

```ts
const HIGHLIGHT_COLOR = 0xf5d678;
const DIM_ALPHA = 0.4;
const HIGHLIGHT_SCALE = 1.08;
```

- [ ] **Step 2: Add a dedicated glow child rendered behind the card**

In the constructor, the first child added is currently `this.shadow`. Add a `highlight` graphic *before* it so it renders behind everything. Change the field declarations block (currently starting `private shadow: Phaser.GameObjects.Graphics;`) to add the field:

```ts
  private highlight: Phaser.GameObjects.Graphics;
  private shadow: Phaser.GameObjects.Graphics;
```

Then in the constructor, immediately after `scene.add.existing(this);`, add (before the existing `this.shadow = ...` block):

```ts
    this.highlight = new Phaser.GameObjects.Graphics(scene);
    this.add(this.highlight);
```

Also add a tween handle field next to the other private fields (e.g. after `private cornerRadius = 5;`):

```ts
  private highlightTween: Phaser.Tweens.Tween | null = null;
```

- [ ] **Step 3: Add the `setHighlight` and `setDimmed` methods**

Add these two public methods to the class (e.g. immediately after the existing `setTint(...)` method):

```ts
  /** Gold pulsing glow + slight scale-up to mark a winning card. */
  setHighlight(on: boolean): void {
    if (this.highlightTween) {
      this.highlightTween.stop();
      this.highlightTween = null;
    }
    this.highlight.clear();
    if (!on) {
      this.setScale(1);
      return;
    }
    const w = this.cardWidth;
    const h = this.cardHeight;
    const r = this.cornerRadius;
    const pad = Math.max(3, Math.round(w * 0.12));
    for (let g = 0; g < 3; g++) {
      const a = 0.5 * (1 - g / 3);
      const gp = pad * (g + 1);
      this.highlight.fillStyle(HIGHLIGHT_COLOR, a);
      this.highlight.fillRoundedRect(-w / 2 - gp, -h / 2 - gp, w + gp * 2, h + gp * 2, r + gp);
    }
    this.setScale(HIGHLIGHT_SCALE);
    this.highlightTween = this.scene.tweens.add({
      targets: this.highlight,
      alpha: { from: 1, to: 0.55 },
      duration: 700,
      yoyo: true,
      repeat: -1,
      ease: 'Sine.easeInOut',
    });
  }

  /** Fade a non-winning card so the highlighted ones stand out. */
  setDimmed(on: boolean): void {
    this.setAlpha(on ? DIM_ALPHA : 1);
  }
```

**Why this survives re-renders:** `showCard`/`drawFace`/`drawBack` only touch `this.shadow`, `this.bg`, `this.rankText`, `this.suitText` — never `this.highlight`, the container scale, or the container alpha. So a highlight or dim set during the showdown persists even when `renderState()` re-runs `updateCards`/`updateSeat`. The presenter (Task 8) is responsible for clearing them on stage change and abort.

- [ ] **Step 4: Verify it type-checks**

Run: `npm run build`
Expected: build succeeds (the new methods are not yet called).

- [ ] **Step 5: Commit**

```bash
git add src/app/game-lobby/phaser-table/game-objects/card-sprite.ts
git commit -m "feat(phaser): add winning-card highlight and dim to CardSprite"
```

---

### Task 5: `WinnerBanner` game object

**Files:**
- Create: `src/app/game-lobby/phaser-table/game-objects/winner-banner.ts`

No unit test (Phaser object — verified in Task 8).

- [ ] **Step 1: Create the banner**

Create `src/app/game-lobby/phaser-table/game-objects/winner-banner.ts`:

```ts
import Phaser from 'phaser';

const BG_COLOR = 0x3a2a0a;
const BG_ALPHA = 0.92;
const BORDER_COLOR = 0xf5d678;
const LINE1_COLOR = '#ffe89a';
const LINE2_COLOR = '#f5d678';
const FADE_IN_MS = 200;
const FADE_OUT_MS = 350;
const PAD_X = 18;
const PAD_Y = 10;
const LINE_GAP = 3;

/**
 * Center-of-table announcement of a pot winner. Owned by the scene; one instance
 * reused across stages. `show` swaps text and fades in; `fadeOut`/`hide` clear it.
 */
export class WinnerBanner extends Phaser.GameObjects.Container {
  private bg: Phaser.GameObjects.Graphics;
  private line1Text: Phaser.GameObjects.Text;
  private line2Text: Phaser.GameObjects.Text;
  private line1Size = 18;
  private line2Size = 13;

  constructor(scene: Phaser.Scene, x: number, y: number) {
    super(scene, x, y);
    scene.add.existing(this);

    this.bg = new Phaser.GameObjects.Graphics(scene);
    this.add(this.bg);

    this.line1Text = new Phaser.GameObjects.Text(scene, 0, 0, '', {
      fontFamily: 'system-ui, sans-serif',
      fontSize: `${this.line1Size}px`,
      fontStyle: 'bold',
      color: LINE1_COLOR,
    }).setOrigin(0.5);
    this.add(this.line1Text);

    this.line2Text = new Phaser.GameObjects.Text(scene, 0, 0, '', {
      fontFamily: 'system-ui, sans-serif',
      fontSize: `${this.line2Size}px`,
      color: LINE2_COLOR,
    }).setOrigin(0.5);
    this.add(this.line2Text);

    this.setVisible(false);
    this.setAlpha(0);
  }

  resize(line1Size: number, line2Size: number): void {
    this.line1Size = line1Size;
    this.line2Size = line2Size;
    this.line1Text.setFontSize(line1Size);
    this.line2Text.setFontSize(line2Size);
  }

  show(line1: string, line2: string): void {
    this.scene.tweens.killTweensOf(this);

    this.line1Text.setText(line1);
    this.line2Text.setText(line2);
    const hasLine2 = line2.length > 0;
    this.line2Text.setVisible(hasLine2);

    const lineGap = hasLine2 ? LINE_GAP : 0;
    const totalTextH = this.line1Text.height + (hasLine2 ? this.line2Text.height + lineGap : 0);
    const totalTextW = Math.max(this.line1Text.width, hasLine2 ? this.line2Text.width : 0);
    const w = totalTextW + PAD_X * 2;
    const h = totalTextH + PAD_Y * 2;

    this.line1Text.setPosition(0, -h / 2 + PAD_Y + this.line1Text.height / 2);
    if (hasLine2) {
      this.line2Text.setPosition(0, h / 2 - PAD_Y - this.line2Text.height / 2);
    }

    this.bg.clear();
    this.bg.fillStyle(BG_COLOR, BG_ALPHA);
    this.bg.fillRoundedRect(-w / 2, -h / 2, w, h, 12);
    this.bg.lineStyle(2, BORDER_COLOR, 1);
    this.bg.strokeRoundedRect(-w / 2, -h / 2, w, h, 12);

    this.setVisible(true);
    this.scene.tweens.add({
      targets: this,
      alpha: 1,
      duration: FADE_IN_MS,
      ease: 'Quad.easeOut',
    });
  }

  fadeOut(): void {
    this.scene.tweens.killTweensOf(this);
    this.scene.tweens.add({
      targets: this,
      alpha: 0,
      duration: FADE_OUT_MS,
      ease: 'Quad.easeIn',
      onComplete: () => this.setVisible(false),
    });
  }

  hide(): void {
    this.scene.tweens.killTweensOf(this);
    this.setAlpha(0);
    this.setVisible(false);
  }
}
```

- [ ] **Step 2: Verify it type-checks**

Run: `npm run build`
Expected: build succeeds (not yet referenced).

- [ ] **Step 3: Commit**

```bash
git add src/app/game-lobby/phaser-table/game-objects/winner-banner.ts
git commit -m "feat(phaser): add center-of-table WinnerBanner game object"
```

---

### Task 6: Winner-seat glow + hole-card highlight on `SeatDisplay`

**Files:**
- Modify: `src/app/game-lobby/phaser-table/game-objects/seat-display.ts`

No unit test (Phaser object — verified in Task 8).

- [ ] **Step 1: Add the winner colours**

After the line `const ACTIVE_GLOW_INTENSITY = 0.25;` add:

```ts
const WINNER_GLOW_COLOR = 0xf5d678;
const WINNER_GLOW_INTENSITY = 0.5;
const WINNER_NAME_COLOR = '#f5d678';
```

- [ ] **Step 2: Add the dedicated winner-glow graphic + flag**

In the field declarations, after `private glow: Phaser.GameObjects.Graphics;` add:

```ts
  private winnerGlow: Phaser.GameObjects.Graphics;
```

And after `private lastPodH = 0;` add:

```ts
  private winnerHighlight = false;
```

In the constructor, the active `glow` is added first (`this.glow = new ...; this.add(this.glow);`). Immediately after that `this.add(this.glow);` line, add the winner glow so it renders just above the active glow but still behind the pod `bg`:

```ts
    this.winnerGlow = new Phaser.GameObjects.Graphics(scene);
    this.add(this.winnerGlow);
```

- [ ] **Step 3: Add the public methods**

Add these methods to the class (e.g. after `getPodHalfHeight()`):

```ts
  /** Gold pulse-free glow + gold name to mark the winning seat. Persists across re-renders. */
  setWinnerHighlight(on: boolean): void {
    this.winnerHighlight = on;
    this.drawWinnerGlow(this.timerPodW, this.timerPodH);
    // Re-tint the name without a full re-render; renderOccupied also honours the flag.
    this.nameText.setColor(on ? WINNER_NAME_COLOR : NAME_COLOR);
  }

  /**
   * Highlight / dim this seat's two hole cards for the showdown.
   * `null` clears both. Otherwise the listed indices glow and the rest dim.
   */
  applyShowdownHighlight(highlightIndices: number[] | null): void {
    const cards = [this.card1, this.card2];
    for (let i = 0; i < cards.length; i++) {
      if (highlightIndices == null) {
        cards[i].setHighlight(false);
        cards[i].setDimmed(false);
      } else if (highlightIndices.includes(i)) {
        cards[i].setDimmed(false);
        cards[i].setHighlight(true);
      } else {
        cards[i].setHighlight(false);
        cards[i].setDimmed(true);
      }
    }
  }

  private drawWinnerGlow(podW: number, podH: number): void {
    this.winnerGlow.clear();
    if (!this.winnerHighlight || podW <= 0 || podH <= 0) return;
    const glowPad = 8;
    for (let g = 0; g < 5; g++) {
      const a = WINNER_GLOW_INTENSITY * (1 - g / 5);
      this.winnerGlow.fillStyle(WINNER_GLOW_COLOR, a);
      this.winnerGlow.fillRoundedRect(
        -podW / 2 - glowPad * (g + 1),
        -podH / 2 - glowPad * (g + 1),
        podW + glowPad * 2 * (g + 1),
        podH + glowPad * 2 * (g + 1),
        this.sizing.podRadius + glowPad * (g + 1),
      );
    }
  }
```

- [ ] **Step 4: Honour the flag inside `renderOccupied` so the glow + name survive re-renders**

In `renderOccupied`, the name colour is currently set in this block:

```ts
    if (actionOverlay) {
      this.nameText.setText(actionOverlay.text);
      this.nameText.setColor(actionOverlay.color);
    } else {
      this.nameText.setText(this.truncateName(playerName));
      this.nameText.setColor(NAME_COLOR);
    }
```

Change the `else` branch so a winner keeps the gold name:

```ts
    if (actionOverlay) {
      this.nameText.setText(actionOverlay.text);
      this.nameText.setColor(actionOverlay.color);
    } else {
      this.nameText.setText(this.truncateName(playerName));
      this.nameText.setColor(this.winnerHighlight ? WINNER_NAME_COLOR : NAME_COLOR);
    }
```

Then, near the end of `renderOccupied`, the method currently ends with:

```ts
    this.lastPodH = podH;

    this.timerPodW = podW;
    this.timerPodH = podH;
    this.timerActive = isActive;
    this.redrawTimer();
  }
```

Add a `drawWinnerGlow` call so the glow is redrawn with fresh pod dimensions on every render:

```ts
    this.lastPodH = podH;

    this.timerPodW = podW;
    this.timerPodH = podH;
    this.timerActive = isActive;
    this.redrawTimer();
    this.drawWinnerGlow(podW, podH);
  }
```

Finally, in `renderEmpty()`, clear the winner glow alongside the existing `this.glow.clear();` so a vacated seat never keeps a stale glow. After the `this.glow.clear();` line in `renderEmpty`, add:

```ts
    this.winnerGlow.clear();
```

(Leave `this.winnerHighlight` for the presenter to reset via `setWinnerHighlight(false)`; clearing the graphic here is the visual safety net for an empty seat.)

- [ ] **Step 5: Verify it type-checks**

Run: `npm run build`
Expected: build succeeds (methods not yet called).

- [ ] **Step 6: Commit**

```bash
git add src/app/game-lobby/phaser-table/game-objects/seat-display.ts
git commit -m "feat(phaser): add winner-seat glow and hole-card highlight to SeatDisplay"
```

---

### Task 7: Board-card highlight on `CommunityCards`

**Files:**
- Modify: `src/app/game-lobby/phaser-table/game-objects/community-cards.ts`

No unit test (Phaser object — verified in Task 8).

- [ ] **Step 1: Add the highlight method**

In `src/app/game-lobby/phaser-table/game-objects/community-cards.ts`, add this method to the class, after `updateCards(...)`:

```ts
  /**
   * Highlight / dim the board for the showdown. `null` clears all cards back to
   * normal. Otherwise the listed indices glow and every other card dims.
   */
  applyShowdownHighlight(highlightIndices: number[] | null): void {
    for (let i = 0; i < MAX_COMMUNITY_CARDS; i++) {
      const card = this.cards[i];
      if (highlightIndices == null) {
        card.setHighlight(false);
        card.setDimmed(false);
      } else if (highlightIndices.includes(i)) {
        card.setDimmed(false);
        card.setHighlight(true);
      } else {
        card.setHighlight(false);
        card.setDimmed(true);
      }
    }
  }
```

(`this.cards` and `MAX_COMMUNITY_CARDS` are already in scope in this file.)

- [ ] **Step 2: Verify it type-checks**

Run: `npm run build`
Expected: build succeeds (not yet called).

- [ ] **Step 3: Commit**

```bash
git add src/app/game-lobby/phaser-table/game-objects/community-cards.ts
git commit -m "feat(phaser): add showdown highlight/dim to CommunityCards"
```

---

### Task 8: Wire the showdown presenter into the scene

**Files:**
- Modify: `src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts`

This task removes all `ActionBadge` usage and adds the banner + highlight presenter. Verified by **running the app** (no unit test). Apply the edits in order; the file must compile after the full task.

- [ ] **Step 1: Update imports**

Replace the import line:

```ts
import { ActionBadge } from '../game-objects/action-badge';
```

with:

```ts
import { WinnerBanner } from '../game-objects/winner-banner';
import { buildShowdownStages, formatStageLine1, formatStageLine2, ShowdownStage } from '../utils/showdown-sequence';
import { matchWinningCards } from '../utils/card-match';
```

Add a `PotResult` import near the other `../../../game/...` imports (used by the presenter's typed fields/params):

```ts
import { PotResult } from '../../../game/game-events';
```

The existing import of `computeBadgeState, badgeTextColor` from `../utils/action-badge-state` stays (still used for the action name-swap overlay).

- [ ] **Step 2: Swap the `ACTION_OVERLAY_MS` constant block**

The file has:

```ts
const ACTION_OVERLAY_MS = 2000;
```

Add the stage duration below it:

```ts
const ACTION_OVERLAY_MS = 2000;
const SHOWDOWN_STAGE_MS = 4000;
```

- [ ] **Step 3: Replace the `actionBadges` field with banner + showdown state**

Remove this field:

```ts
  private actionBadges: ActionBadge[] = [];
```

In the same field group, change the comment and remove the line. Then add a `winnerBanner` field next to `potDisplay`:

```ts
  private potDisplay!: PotDisplay;
  private winnerBanner!: WinnerBanner;
```

Update the stale comment block (currently above `actionOverlays`):

```ts
  // Per-seat action overlay (transient name-replacement for fold/check/call/etc).
  // 'winner' continues to use ActionBadge (sticky, 2-line, pulse).
  private actionOverlays: ({ text: string; color: string } | null)[] = [];
```

to:

```ts
  // Per-seat action overlay (transient name-replacement for fold/check/call/etc).
  private actionOverlays: ({ text: string; color: string } | null)[] = [];
```

Then add the showdown presenter state immediately after the `lastSeenActionSeq` field:

```ts
  private lastSeenActionSeq: number[] = [];

  // Showdown presentation: stages cycle one winning hand at a time.
  private showdownStages: ShowdownStage[] = [];
  private showdownStageIndex = 0;
  private showdownTimer: Phaser.Time.TimerEvent | null = null;
  /** Identity guard: the potResults object currently being presented, or null. */
  private presentedPotResults: PotResult[] | null = null;
```

- [ ] **Step 4: Drop badge creation in `create()`**

In the seat-creation loop, remove these three lines:

```ts
      const badge = new ActionBadge(this, 0, 0);
      badge.setDepth(6);
      this.actionBadges.push(badge);
```

After the `this.potDisplay = new PotDisplay(...)` line, add the banner creation (give it a high depth so it sits above cards and pods):

```ts
    this.potDisplay = new PotDisplay(this, 0, 0).setDepth(2);
    this.winnerBanner = new WinnerBanner(this, 0, 0).setDepth(10);
```

- [ ] **Step 5: Replace badge layout with banner layout in `layoutAll()`**

Remove the entire action-badge layout block:

```ts
    const badgeFontSize = Math.max(10, Math.round(width * 0.008));
    const badgeGap = Math.max(14, Math.round(width * 0.012));
    for (let i = 0; i < MAX_SEATS; i++) {
      this.actionBadges[i].setFontSize(badgeFontSize);
      const sx = this.seatPositions[i].x;
      const sy = this.seatPositions[i].y;
      // Radial-outward unit vector from table center to this seat.
      const ox = sx - this.cx;
      const oy = sy - this.cy;
      const mag = Math.hypot(ox, oy) || 1;
      const ux = ox / mag;
      const uy = oy / mag;
      const podHalfH = this.seats[i].getPodHalfHeight();
      const outwardDist = podHalfH + badgeGap;
      this.actionBadges[i].setPosition(sx + ux * outwardDist, sy + uy * outwardDist);
    }
```

At the end of `layoutAll()` (after the `this.dealerButton.resize(...)` line), add the banner placement — upper-center, above the community cards and clear of the felt edge:

```ts
    this.dealerButtonRadius = Math.max(8, width * 0.008);
    this.dealerButton.resize(this.dealerButtonRadius);

    this.winnerBanner.setPosition(this.cx, this.cy - this.tableRy * 0.62);
    const bannerLine1Size = Math.max(16, Math.round(width * 0.014));
    const bannerLine2Size = Math.max(12, Math.round(width * 0.01));
    this.winnerBanner.resize(bannerLine1Size, bannerLine2Size);
```

- [ ] **Step 6: Detect the showdown edge in `handleTableStateChange`**

The method currently ends with `this.renderState();`. Insert the showdown edge-detection call just before it:

```ts
    this.updateShowdownPresentation(state);
    this.renderState();
  }
```

Then add the method (place it right after `handleTableStateChange`):

```ts
  /**
   * Start the showdown sequence when potResults first appears, and abort it the
   * moment they clear (which happens on the next hand-started). Uses object
   * identity so unrelated state updates within the same showdown don't re-trigger.
   */
  private updateShowdownPresentation(state: TableState | null): void {
    const potResults = state?.potResults ?? null;
    if (potResults && potResults !== this.presentedPotResults) {
      this.presentedPotResults = potResults;
      this.startShowdown(potResults);
    } else if (!potResults && this.presentedPotResults) {
      this.presentedPotResults = null;
      this.abortShowdown();
    }
  }
```

- [ ] **Step 7: Remove the winner-badge block and simplify the overlay branch in `renderState()`**

> Ordering note: this task (scene) must come before Task 9 (removing `'winner'` from the `BadgeKind` union) — the current scene code references `badgeState.kind === 'winner'`, so removing the union member first would break the build. Between committing Task 8 and Task 9, `computeBadgeState` can still return `'winner'`, which would briefly drive a name-swap overlay (`WON $X.XX`). This is a harmless dev-time intermediate that Task 9 eliminates; it is never a shipped state.

In the no-state early-return branch, remove this line:

```ts
      for (const badge of this.actionBadges) badge.hide();
```

and add a showdown clear in its place so a disconnect/reset tears down any active presentation:

```ts
      this.abortShowdown();
```

Next, remove the winner-badge rendering block entirely:

```ts
      // Drive the action badge / overlay from the pure mapping.
      const badgeState = computeBadgeState(pos, ts);
      if (player && badgeState.kind === 'winner') {
        this.actionBadges[idx].applyState(badgeState);
      } else {
        this.actionBadges[idx].hide();
      }

      // For transient action kinds (fold/check/call/bet/raise/all-in),
      // temporarily replace the seat's name with the action message.
      if (
        player &&
        badgeState.kind !== 'none' &&
        badgeState.kind !== 'winner' &&
        this.lastSeenActionSeq[idx] !== badgeState.actionSeq
      ) {
```

replacing it with (keep `computeBadgeState`; it still drives the name-swap overlay):

```ts
      // Drive the transient action name-swap overlay from the pure mapping.
      const badgeState = computeBadgeState(pos, ts);

      // For transient action kinds (fold/check/call/bet/raise/all-in),
      // temporarily replace the seat's name with the action message.
      if (
        player &&
        badgeState.kind !== 'none' &&
        this.lastSeenActionSeq[idx] !== badgeState.actionSeq
      ) {
```

- [ ] **Step 8: Add the presenter methods**

Add these methods to the class (e.g. just before `shutdown()`):

```ts
  private startShowdown(potResults: PotResult[]): void {
    this.abortShowdown();
    this.showdownStages = buildShowdownStages(potResults);
    this.showdownStageIndex = 0;
    if (this.showdownStages.length === 0) return;
    this.presentStage(0);
    this.scheduleNextStage();
  }

  private scheduleNextStage(): void {
    this.showdownTimer = this.time.delayedCall(SHOWDOWN_STAGE_MS, () => {
      this.showdownStageIndex++;
      if (this.showdownStageIndex < this.showdownStages.length) {
        this.presentStage(this.showdownStageIndex);
        this.scheduleNextStage();
      } else {
        this.fadeOutShowdown();
      }
    });
  }

  private presentStage(index: number): void {
    const ts = this.currentTableState;
    if (!ts) return;
    const stage = this.showdownStages[index];
    if (!stage) return;

    const name = this.displayNameForSeat(stage.userId);
    this.winnerBanner.show(formatStageLine1(stage, name), formatStageLine2(stage));

    // Reset any prior stage's highlights before applying this one.
    this.clearShowdownHighlights();

    if (stage.winningCards.length === 0) {
      // Fold-win: no cards to highlight — banner + seat glow only.
      if (stage.seatPosition >= 1 && stage.seatPosition <= MAX_SEATS) {
        this.seats[stage.seatPosition - 1].setWinnerHighlight(true);
      }
      return;
    }

    const targets = matchWinningCards(
      stage.winningCards,
      ts.communityCards,
      ts.seatCards.get(stage.seatPosition) ?? null,
    );

    this.communityCards.applyShowdownHighlight(targets.communityIndices);
    for (let pos = 1; pos <= MAX_SEATS; pos++) {
      const seat = this.seats[pos - 1];
      if (pos === stage.seatPosition) {
        seat.applyShowdownHighlight(targets.holeSeatCardIndices);
        seat.setWinnerHighlight(true);
      } else {
        // Dim other seats' (revealed) hole cards for contrast.
        seat.applyShowdownHighlight([]);
      }
    }
  }

  private fadeOutShowdown(): void {
    this.showdownTimer = null;
    this.winnerBanner.fadeOut();
    this.clearShowdownHighlights();
  }

  private abortShowdown(): void {
    if (this.showdownTimer) {
      this.showdownTimer.remove(false);
      this.showdownTimer = null;
    }
    this.showdownStages = [];
    this.showdownStageIndex = 0;
    this.winnerBanner.hide();
    this.clearShowdownHighlights();
  }

  private clearShowdownHighlights(): void {
    this.communityCards.applyShowdownHighlight(null);
    for (const seat of this.seats) {
      seat.applyShowdownHighlight(null);
      seat.setWinnerHighlight(false);
    }
  }

  private displayNameForSeat(userId: string): string {
    if (userId === this.currentUserId) return 'You';
    const player = this.currentPlayers.find((p) => p.userId === userId);
    return player?.displayName ?? userId;
  }
```

- [ ] **Step 9: Clean up `shutdown()`**

The method currently starts:

```ts
  shutdown(): void {
    for (const badge of this.actionBadges) badge.hide();
    this.clearAllActionOverlays();
```

Replace the badge loop with showdown teardown:

```ts
  shutdown(): void {
    this.abortShowdown();
    this.presentedPotResults = null;
    this.clearAllActionOverlays();
```

- [ ] **Step 10: Verify build + run the app**

Run: `npm run build`
Expected: build succeeds with no references to `ActionBadge` remaining in the scene.

Run: `grep -rn "actionBadges\|ActionBadge" src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts`
Expected: no output.

Then run the app and verify visually (the backend at `localhost:8080` must be running):

Run: `npm start`
Manually confirm at a showdown:
1. The gold winner banner appears upper-center; **no** winner badge appears on the pod.
2. The winning seat glows gold with a gold name.
3. The 5 winning cards (board + hole) glow gold and scale up; all other cards dim.
4. With side pots, the banner + highlights cycle smallest side pot → main pot, ~4s each, then fade.
5. A split pot shows each winner's hand one at a time (never two highlighted hands at once).
6. A fold-win shows the banner + seat glow with no card highlight.
7. Starting the next hand mid-sequence clears the banner and all highlights immediately.

- [ ] **Step 11: Commit**

```bash
git add src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts
git commit -m "feat(phaser): center winner banner + card/seat highlight presenter"
```

---

### Task 9: Remove the winner from the action-badge state mapping

**Files:**
- Modify: `src/app/game-lobby/phaser-table/utils/action-badge-state.ts`
- Modify: `src/app/game-lobby/phaser-table/utils/action-badge-state.spec.ts`

- [ ] **Step 1: Remove the winner cases from the spec first (TDD: define the new contract)**

In `src/app/game-lobby/phaser-table/utils/action-badge-state.spec.ts`, delete the five winner-related tests (everything from `it('returns winner with WON $X.XX ...` through `it('prefers winner over any in-progress action state', ...)`), i.e. the block of tests starting at the comment-free `it('returns winner ...` and ending at the closing `});` of the `prefers winner` test — the last five `it(...)` blocks before the final `});` that closes the `describe`. Leave all action tests (`fold`/`check`/`call`/`bet`/`raise`/`all-in`/`none`) intact.

The file's final lines should now be the `all-in when a bet shoves the stack` test followed by the single closing `});` of the `describe('computeBadgeState', ...)` block.

- [ ] **Step 2: Run the spec to verify it fails to compile/pass**

Run: `npm test -- action-badge-state`
Expected: the suite runs the remaining action tests. They currently still PASS (the implementation still has winner support). This step's purpose is to confirm the trimmed spec is syntactically valid and green before we change the implementation. If it fails to parse, fix the trim.

- [ ] **Step 3: Remove winner support from the implementation**

In `src/app/game-lobby/phaser-table/utils/action-badge-state.ts`:

1. Remove the now-unused import. Change:

```ts
import { TableState } from '../../../game/game-state.service';
import { PotResult, Winner } from '../../../game/game-events';
```

to:

```ts
import { TableState } from '../../../game/game-state.service';
```

2. Remove `'winner'` from the `BadgeKind` union:

```ts
export type BadgeKind =
  | 'none'
  | 'fold'
  | 'check'
  | 'call'
  | 'bet'
  | 'raise'
  | 'all-in';
```

3. Remove the `'winner'` entry from `KIND_TEXT_COLORS`:

```ts
const KIND_TEXT_COLORS: Record<BadgeKind, string> = {
  'none':   '#ffffff',
  'fold':   '#ff5577',
  'check':  '#9eff80',
  'call':   '#5cb8ff',
  'bet':    '#ffd54f',
  'raise':  '#ffb74d',
  'all-in': '#ffeb3b',
};
```

4. Delete the entire `truncateDesc` function and the `MAX_DESC_CHARS` constant (only used by the winner badge), and delete the entire `computeWinnerBadge` function.

5. In `computeBadgeState`, remove the winner precedence block:

```ts
  // 1. Winner takes precedence over any in-progress action state.
  if (tableState.potResults && tableState.potResults.length > 0) {
    const winnerBadge = computeWinnerBadge(seatPosition, tableState.potResults);
    if (winnerBadge) return winnerBadge;
  }

```

so the function body begins directly with the `const la = tableState.lastAction;` logic (renumber the remaining comment from "2." to "1." for tidiness).

6. Update the stale doc comment on `BadgeState.line2` (currently mentions "winner with hand description"):

```ts
  /** Line 2 text. Unused by the current action kinds; always empty. */
  line2: string;
```

- [ ] **Step 4: Run the scoped test suites**

Run: `npm test -- phaser-table`
Expected: PASS. The `action-badge-state` suite passes with action-only cases; `showdown-sequence` and `card-match` pass; nothing references the removed winner code.

(Do not gate on a bare full-suite `npm test` — see Baseline / Preconditions; the pre-existing red specs are unrelated to this change.)

- [ ] **Step 5: Verify the build**

Run: `npm run build`
Expected: build succeeds (this also confirms Task 1's `Winner.winningCards` no longer causes any spec/build issue, since the winner literals were removed).

- [ ] **Step 6: Commit**

```bash
git add src/app/game-lobby/phaser-table/utils/action-badge-state.ts src/app/game-lobby/phaser-table/utils/action-badge-state.spec.ts
git commit -m "refactor(phaser): drop winner kind from action-badge state mapping"
```

---

### Task 10: Delete the now-unused `ActionBadge` game object

**Files:**
- Delete: `src/app/game-lobby/phaser-table/game-objects/action-badge.ts`

- [ ] **Step 1: Confirm there are no remaining references**

Run: `grep -rn "action-badge'\|ActionBadge" src/app --include="*.ts"`
Expected: no output. (`action-badge-state` is a different module and must NOT appear — the grep pattern above intentionally matches only the class import path `action-badge'` and the symbol `ActionBadge`.)

If anything appears, stop — a prior task left a reference; resolve it before deleting.

- [ ] **Step 2: Delete the file**

```bash
git rm src/app/game-lobby/phaser-table/game-objects/action-badge.ts
```

- [ ] **Step 3: Verify build + scoped tests**

Run: `npm run build`
Expected: build succeeds.

Run: `npm test -- phaser-table`
Expected: PASS (this feature's suites). Do not gate on a bare full-suite `npm test` — see Baseline / Preconditions.

- [ ] **Step 4: Commit**

```bash
git commit -m "chore(phaser): delete unused ActionBadge game object"
```

---

## Self-Review Notes

- **Spec coverage:** center banner (Task 5, 8); winner-seat glow (Task 6, 8); highlight 5 winning cards + dim rest (Tasks 4, 6, 7, 8 via `matchWinningCards`); one-hand-at-a-time cycling with split expansion and smallest-side-pot-first / main-pot-last ordering (Task 2, 8); ~4s per stage (Task 8 `SHOWDOWN_STAGE_MS`); fade after final stage (Task 8 `fadeOutShowdown`); `hand-started` hard abort at any time (Task 8 `updateShowdownPresentation` identity guard + `abortShowdown`); fold-win = banner + seat glow only (Task 8 empty-`winningCards` branch); backend `winningCards` field (Task 1); pod-badge winner removed (Tasks 8, 9, 10). All spec sections map to a task.
- **Type consistency:** `ShowdownStage`, `CardHighlightTargets`, `matchWinningCards`, `buildShowdownStages`, `formatStageLine1/2`, `applyShowdownHighlight`, `setWinnerHighlight`, `setHighlight`, `setDimmed` names are used identically across the tasks that define and call them.
- **Persistence design:** highlights are applied directly to sprites and deliberately survive `renderState()` re-renders (glow is a dedicated child; dim is container alpha; winner-seat glow is reasserted in `renderOccupied` from the `winnerHighlight` flag). The presenter owns clearing them on stage change, fade, and abort.
