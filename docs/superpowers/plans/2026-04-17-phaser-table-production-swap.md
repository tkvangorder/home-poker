# Phaser Table Production Swap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the CSS table renderer with a polished Phaser renderer in production, porting the visuals validated in `phaser-table-playground.html` into `src/app/game-lobby/phaser-table/`.

**Architecture:** Rewrite each game-object (`table-felt`, `card-sprite`, `community-cards`, `pot-display`, `seat-display`, `dealer-button`) plus add a new `bet-chip` game-object. The scene orchestrates state (from `PhaserBridgeService`) and drives a per-frame timer animation off `TableState.actionDeadline`. A temporary dev route (`/phaser-dev`) provides a visual harness during implementation; production is wired up in the final task by changing `table-view.component` to use `<app-phaser-table>`.

**Tech Stack:** Angular 19 (standalone components, `inject()`), Phaser 3, RxJS, TypeScript strict mode.

---

## Reference Material

Engineers executing this plan should have these files open for reference:

- **Visual reference (the target polish):** `phaser-table-playground.html` at the project root — each Phaser game-object mirrors the playground's corresponding `draw*` method.
- **Spec:** `docs/superpowers/specs/2026-04-17-phaser-table-production-swap-design.md`.
- **Playground spec:** `docs/superpowers/specs/2026-04-17-phaser-table-playground-design.md` — context for visual decisions.
- **Game state models:** `src/app/game/game-state.service.ts` — `TableState`, `PlayerState`, `SeatSummary`.
- **Existing phaser-table:** `src/app/game-lobby/phaser-table/` — the scaffolding we're modifying.

## Verification Approach

No automated tests exist for either the CSS or Phaser renderer; this plan does not introduce a test suite. Per-task verification is:

1. **Typecheck:** `npm run build` (or `npx ng build --configuration=development`) — must compile with zero TypeScript errors. Angular's strict mode enforces `strictTemplates` and `strictInjectionParameters`, so type mismatches will surface here.
2. **Visual check:** run `npm start`, navigate to `http://localhost:4200/phaser-dev` (the dev harness from Task 1), confirm the described visual.
3. **Smoke test at the final task:** navigate to a real game lobby and verify the swap did not break anything in live play.

## Scale-Relative Sizing

Per the spec, playground pixel defaults translate to `scale.width` ratios. Each game-object's `resize(width, height)` method computes derived sizes from the ratios. The scene's `layoutAll()` calls `resize` on every object. Use these starter ratios (final tuning during implementation if anything looks off):

| Playground | Ratio |
|---|---|
| `railWidth: 14` | `width * 0.01` |
| `seats.podRadius: 12` | `width * 0.009` |
| `seats.avatarSize: 36` | `width * 0.026` |
| `seats.podPadX: 10` | `width * 0.007` |
| `seats.podPadY: 6` | `width * 0.004` |
| `seats.nameSize: 11` | `Math.max(10, width * 0.008)` |
| `seats.stackSize: 10` | `Math.max(9, width * 0.007)` |
| `cards.communityWidth: 72` | `width * 0.051` |
| `cards.holeWidth: 44` | `width * 0.031` |
| `cards.rankSize: 22` | `Math.max(14, width * 0.016)` |
| `cards.suitSize: 32` | `Math.max(20, width * 0.023)` |
| `pot.chipSize: 18` | `width * 0.013` |
| `pot.amountSize: 15` | `Math.max(13, width * 0.011)` |
| `pot.labelSize: 9` | `Math.max(8, width * 0.007)` |
| `seats.timerHeight: 3` | `Math.max(2, width * 0.002)` |

---

## Task 1: Dev harness route

**Files:**
- Create: `src/app/game-lobby/phaser-table/phaser-dev-page/phaser-dev-page.component.ts`
- Modify: `src/app/app.routes.ts`

- [ ] **Step 1: Create the dev page component**

Create `/Users/tylervangorder/work/angular/angular-poker-client/src/app/game-lobby/phaser-table/phaser-dev-page/phaser-dev-page.component.ts`:

```typescript
import { Component } from '@angular/core';
import { PhaserTableComponent } from '../phaser-table.component';
import { TableState, PlayerState } from '../../../game/game-state.service';
import { CardSuit, CardValue } from '../../../poker/poker-models';

@Component({
  selector: 'app-phaser-dev-page',
  standalone: true,
  imports: [PhaserTableComponent],
  template: `
    <div class="w-screen h-screen bg-neutral-900 p-4">
      <div class="w-full h-full">
        <app-phaser-table
          [tableState]="tableState"
          [players]="players"
          [currentUserId]="currentUserId"
        />
      </div>
    </div>
  `,
})
export class PhaserDevPageComponent {
  readonly currentUserId = 'user-1';

  readonly players: PlayerState[] = [
    { userId: 'user-1', displayName: 'You', chipCount: 24000, tableId: 't1', seatPosition: 1 },
    { userId: 'user-2', displayName: 'Sam', chipCount: 18000, tableId: 't1', seatPosition: 2 },
    { userId: 'user-3', displayName: 'Ari', chipCount: 30000, tableId: 't1', seatPosition: 3 },
    { userId: 'user-4', displayName: 'Jay', chipCount: 22000, tableId: 't1', seatPosition: 4 },
    { userId: 'user-5', displayName: 'Kim', chipCount: 42000, tableId: 't1', seatPosition: 5 },
    { userId: 'user-6', displayName: 'Lee', chipCount: 51000, tableId: 't1', seatPosition: 6 },
  ];

  readonly tableState: TableState = {
    tableId: 't1',
    tableStatus: 'IN_PROGRESS',
    handNumber: 42,
    dealerPosition: 1,
    smallBlindPosition: 2,
    bigBlindPosition: 3,
    smallBlindAmount: 100,
    bigBlindAmount: 200,
    communityCards: [
      { value: CardValue.ACE, suit: CardSuit.HEART },
      { value: CardValue.KING, suit: CardSuit.SPADE },
      { value: CardValue.QUEEN, suit: CardSuit.DIAMOND },
    ],
    pots: [{ amount: 1200, seatPositions: [1, 2, 3, 4, 5, 6] }],
    potTotal: 1200,
    phase: 'FLOP',
    potResults: null,
    seatCards: new Map([
      [1, [
        { card: { value: CardValue.ACE, suit: CardSuit.HEART }, showCard: true },
        { card: { value: CardValue.KING, suit: CardSuit.SPADE }, showCard: true },
      ]],
      [2, [
        { card: { value: CardValue.TWO, suit: CardSuit.SPADE }, showCard: false },
        { card: { value: CardValue.TWO, suit: CardSuit.SPADE }, showCard: false },
      ]],
    ]),
    seatSummaries: new Map([
      [1, { seatPosition: 1, userId: 'user-1', status: 'ACTIVE', chipCount: 24000, currentBetAmount: 0 }],
      [2, { seatPosition: 2, userId: 'user-2', status: 'ACTIVE', chipCount: 18000, currentBetAmount: 500 }],
      [3, { seatPosition: 3, userId: 'user-3', status: 'ACTIVE', chipCount: 30000, currentBetAmount: 0 }],
      [4, { seatPosition: 4, userId: 'user-4', status: 'ACTIVE', chipCount: 22000, currentBetAmount: 1000 }],
      [5, { seatPosition: 5, userId: 'user-5', status: 'ACTIVE', chipCount: 42000, currentBetAmount: 0 }],
      [6, { seatPosition: 6, userId: 'user-6', status: 'ACTIVE', chipCount: 51000, currentBetAmount: 2000 }],
    ]),
    lastAction: null,
    actionPosition: 3,
    actionDeadline: new Date(Date.now() + 18000).toISOString(),
    callAmount: 500,
    currentBet: 500,
    minimumRaise: 200,
  };
}
```

- [ ] **Step 2: Register the route**

Edit `/Users/tylervangorder/work/angular/angular-poker-client/src/app/app.routes.ts`. Replace the file contents with:

```typescript
import { Routes } from '@angular/router';
import { TitlePageComponent } from './title-page/title-page.component';
import { HomePageComponent } from './home-page/home-page.component';
import { GameLobbyComponent } from './game-lobby/game-lobby.component';
import { PhaserDevPageComponent } from './game-lobby/phaser-table/phaser-dev-page/phaser-dev-page.component';
import { authenticationGuard, loggedInGuard } from './auth-guard.service';
export const routes: Routes = [
  {
    path: '',
    component: TitlePageComponent,
    title: 'Chico Degens Poker Club',
    canActivate: [loggedInGuard]
  },
  {
    path: 'home',
    component: HomePageComponent,
    title: 'Chico Degens Poker Club',
    canActivate: [authenticationGuard]
  },
  {
    path: 'game/:gameId',
    component: GameLobbyComponent,
    title: 'Chico Degens Poker Club - Game Lobby',
    canActivate: [authenticationGuard]
  },
  {
    path: 'phaser-dev',
    component: PhaserDevPageComponent,
    title: 'Phaser Table Dev Harness'
  }
];
```

- [ ] **Step 3: Typecheck**

Run: `npm run build`
Expected: compiles with zero errors.

- [ ] **Step 4: Visual check**

Run: `npm start`, then open `http://localhost:4200/phaser-dev`.
Expected: the old flat Phaser table renders (the visuals are ugly — that's what we're fixing in subsequent tasks). No console errors. Six pods around an elliptical felt.

- [ ] **Step 5: Commit**

```bash
git add src/app/game-lobby/phaser-table/phaser-dev-page/phaser-dev-page.component.ts src/app/app.routes.ts
git commit -m "chore: add /phaser-dev harness route for renderer iteration"
```

---

## Task 2: Even seat distribution

**Files:**
- Modify: `src/app/game-lobby/phaser-table/utils/seat-layout.ts`

- [ ] **Step 1: Replace the seat angle table with even distribution**

Replace the contents of `/Users/tylervangorder/work/angular/angular-poker-client/src/app/game-lobby/phaser-table/utils/seat-layout.ts` with:

```typescript
export interface SeatPosition {
  x: number;
  y: number;
}

export const MAX_SEATS = 9;

// Seats are distributed evenly around the ellipse starting at 90° (bottom center,
// where the current user sits) and stepping 40° clockwise.
const START_DEG = 90;
const STEP_DEG = 360 / MAX_SEATS;

export function getSeatPosition(
  index: number,
  cx: number,
  cy: number,
  rx: number,
  ry: number
): SeatPosition {
  const angleDeg = START_DEG + (index % MAX_SEATS) * STEP_DEG;
  const angleRad = (angleDeg * Math.PI) / 180;
  return {
    x: cx + rx * Math.cos(angleRad),
    y: cy + ry * Math.sin(angleRad),
  };
}

export function getAllSeatPositions(
  cx: number,
  cy: number,
  rx: number,
  ry: number
): SeatPosition[] {
  return Array.from({ length: MAX_SEATS }, (_, i) =>
    getSeatPosition(i, cx, cy, rx, ry)
  );
}
```

- [ ] **Step 2: Typecheck**

Run: `npm run build`
Expected: zero errors.

- [ ] **Step 3: Visual check**

Reload `http://localhost:4200/phaser-dev`.
Expected: 6 pods, now evenly distributed around the felt (bottom, bottom-right-ish, upper-right, top, upper-left-ish, bottom-left). Seat 1 still at bottom.

- [ ] **Step 4: Commit**

```bash
git add src/app/game-lobby/phaser-table/utils/seat-layout.ts
git commit -m "feat: switch phaser seat layout to even 40° distribution"
```

---

## Task 3: Polished felt rendering

**Files:**
- Modify: `src/app/game-lobby/phaser-table/game-objects/table-felt.ts`

- [ ] **Step 1: Replace the felt implementation**

Replace the contents of `/Users/tylervangorder/work/angular/angular-poker-client/src/app/game-lobby/phaser-table/game-objects/table-felt.ts` with:

```typescript
import Phaser from 'phaser';

const BASE_COLOR = '#145a30';
const CENTER_HIGHLIGHT = '#1a6b3c';
const CENTER_ALPHA = 0.9;
const CENTER_RADIUS = 0.4;
const RAIL_COLOR = '#3a2a14';
const RAIL_INNER_COLOR = '#1c1008';
const INNER_LINE_COLOR = '#daa520';
const INNER_LINE_ALPHA = 0.25;
const SHADOW_ALPHA = 0.5;
const TEXTURE_KEY = 'felt-texture';

export class TableFelt {
  private image: Phaser.GameObjects.Image | null = null;

  constructor(private scene: Phaser.Scene) {}

  draw(cx: number, cy: number, rx: number, ry: number, railWidth: number): void {
    const pad = railWidth + 10;
    const w = Math.round(rx * 2 + pad * 2);
    const h = Math.round(ry * 2 + pad * 2);

    const off = document.createElement('canvas');
    off.width = w;
    off.height = h;
    const ctx = off.getContext('2d')!;

    // Drop shadow
    ctx.save();
    ctx.shadowColor = `rgba(0,0,0,${SHADOW_ALPHA})`;
    ctx.shadowBlur = 40;
    ctx.shadowOffsetY = 8;
    ctx.fillStyle = BASE_COLOR;
    ctx.beginPath();
    ctx.ellipse(w / 2, h / 2, rx, ry, 0, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();

    // Felt gradient
    const grad = ctx.createRadialGradient(
      w / 2 - rx * 0.1, h / 2 - ry * 0.1, 0,
      w / 2, h / 2, Math.max(rx, ry)
    );
    grad.addColorStop(0, hexWithAlpha(CENTER_HIGHLIGHT, CENTER_ALPHA));
    grad.addColorStop(CENTER_RADIUS, BASE_COLOR);
    grad.addColorStop(1, darken(BASE_COLOR, 0.4));
    ctx.save();
    ctx.beginPath();
    ctx.ellipse(w / 2, h / 2, rx, ry, 0, 0, Math.PI * 2);
    ctx.clip();
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, w, h);
    ctx.restore();

    // Rail ring
    ctx.save();
    ctx.lineWidth = railWidth;
    ctx.strokeStyle = RAIL_COLOR;
    ctx.beginPath();
    ctx.ellipse(w / 2, h / 2, rx + railWidth / 2, ry + railWidth / 2, 0, 0, Math.PI * 2);
    ctx.stroke();
    // Rail inner dark border
    ctx.lineWidth = 2;
    ctx.strokeStyle = RAIL_INNER_COLOR;
    ctx.beginPath();
    ctx.ellipse(w / 2, h / 2, rx, ry, 0, 0, Math.PI * 2);
    ctx.stroke();
    ctx.restore();

    // Gold inner line
    ctx.save();
    ctx.lineWidth = 1;
    ctx.strokeStyle = hexWithAlpha(INNER_LINE_COLOR, INNER_LINE_ALPHA);
    ctx.beginPath();
    ctx.ellipse(w / 2, h / 2, rx - 10, ry - 10, 0, 0, Math.PI * 2);
    ctx.stroke();
    ctx.restore();

    // Upload as Phaser texture
    if (this.scene.textures.exists(TEXTURE_KEY)) {
      this.scene.textures.remove(TEXTURE_KEY);
    }
    this.scene.textures.addCanvas(TEXTURE_KEY, off);

    if (this.image) {
      this.image.destroy();
    }
    this.image = this.scene.add.image(cx, cy, TEXTURE_KEY).setDepth(0);
  }
}

function hexWithAlpha(hex: string, a: number): string {
  const { r, g, b } = hexToRgb(hex);
  return `rgba(${r},${g},${b},${a})`;
}
function hexToRgb(hex: string): { r: number; g: number; b: number } {
  const h = hex.replace('#', '');
  return {
    r: parseInt(h.substring(0, 2), 16),
    g: parseInt(h.substring(2, 4), 16),
    b: parseInt(h.substring(4, 6), 16),
  };
}
function darken(hex: string, amount: number): string {
  const { r, g, b } = hexToRgb(hex);
  const m = 1 - amount;
  return `rgb(${Math.round(r * m)},${Math.round(g * m)},${Math.round(b * m)})`;
}
```

**Note:** The class no longer extends `Phaser.GameObjects.Graphics`. The scene must be updated to pass `railWidth`. We'll handle that wiring in Task 11 (scene orchestration). Until then the dev harness may show an error — that's expected.

- [ ] **Step 2: Update scene's call to `tableFelt.draw`**

Open `/Users/tylervangorder/work/angular/angular-poker-client/src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts`. Find:

```typescript
    this.tableFelt.draw(this.cx, this.cy, this.tableRx, this.tableRy);
```

Replace with:

```typescript
    const railWidth = Math.max(6, width * 0.01);
    this.tableFelt.draw(this.cx, this.cy, this.tableRx, this.tableRy, railWidth);
```

And update `tableRx`/`tableRy` lines. Locate:

```typescript
    this.tableRx = width * 0.38;
    this.tableRy = height * 0.35;
```

Replace with:

```typescript
    this.tableRx = width * 0.30;
    this.tableRy = this.tableRx / 2.4;
```

(This matches the playground's felt sizing: 60% of canvas width, 2.4:1 aspect.)

- [ ] **Step 3: Typecheck**

Run: `npm run build`
Expected: zero errors.

- [ ] **Step 4: Visual check**

Reload `http://localhost:4200/phaser-dev`.
Expected: dark-green oval felt with rail, matching playground's felt look.

- [ ] **Step 5: Commit**

```bash
git add src/app/game-lobby/phaser-table/game-objects/table-felt.ts src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts
git commit -m "feat: polish phaser felt with radial gradient and rail"
```

---

## Task 4: Polished card rendering

**Files:**
- Modify: `src/app/game-lobby/phaser-table/game-objects/card-sprite.ts`

- [ ] **Step 1: Replace the card sprite**

Replace the contents of `/Users/tylervangorder/work/angular/angular-poker-client/src/app/game-lobby/phaser-table/game-objects/card-sprite.ts` with:

```typescript
import Phaser from 'phaser';
import { CardSuit, CardValue } from '../../../poker/poker-models';
import { SeatCard } from '../../../game/game-models';

const SUIT_SYMBOLS: Record<CardSuit, string> = {
  [CardSuit.HEART]: '\u2665',
  [CardSuit.DIAMOND]: '\u2666',
  [CardSuit.CLUB]: '\u2663',
  [CardSuit.SPADE]: '\u2660',
};

const SUIT_COLORS: Record<CardSuit, string> = {
  [CardSuit.HEART]: '#dc2626',
  [CardSuit.DIAMOND]: '#2563eb',
  [CardSuit.CLUB]: '#15803d',
  [CardSuit.SPADE]: '#1e293b',
};

const RANK_LABELS: Record<CardValue, string> = {
  [CardValue.TWO]: '2',
  [CardValue.THREE]: '3',
  [CardValue.FOUR]: '4',
  [CardValue.FIVE]: '5',
  [CardValue.SIX]: '6',
  [CardValue.SEVEN]: '7',
  [CardValue.EIGHT]: '8',
  [CardValue.NINE]: '9',
  [CardValue.TEN]: '10',
  [CardValue.JACK]: 'J',
  [CardValue.QUEEN]: 'Q',
  [CardValue.KING]: 'K',
  [CardValue.ACE]: 'A',
};

const FACE_COLOR = 0xffffff;
const BORDER_COLOR = 0x000000;
const BORDER_ALPHA = 0.12;
const SHADOW_ALPHA = 0.4;
const RANK_FONT = 'Georgia, serif';

const BACK_COLOR_1 = 0x1a5c8a;
const BACK_COLOR_2 = 0x0e3d5e;

export class CardSprite extends Phaser.GameObjects.Container {
  private shadow: Phaser.GameObjects.Graphics;
  private bg: Phaser.GameObjects.Graphics;
  private rankText: Phaser.GameObjects.Text;
  private suitText: Phaser.GameObjects.Text;
  private cardWidth = 40;
  private cardHeight = 56;
  private cornerRadius = 5;

  constructor(scene: Phaser.Scene, x: number, y: number) {
    super(scene, x, y);
    scene.add.existing(this);

    this.shadow = new Phaser.GameObjects.Graphics(scene);
    this.add(this.shadow);

    this.bg = new Phaser.GameObjects.Graphics(scene);
    this.add(this.bg);

    this.rankText = new Phaser.GameObjects.Text(scene, 0, 0, '', {
      fontSize: '18px',
      fontFamily: RANK_FONT,
      fontStyle: 'bold',
      color: '#000000',
      align: 'center',
    }).setOrigin(0.5);
    this.add(this.rankText);

    this.suitText = new Phaser.GameObjects.Text(scene, 0, 0, '', {
      fontSize: '22px',
      fontFamily: RANK_FONT,
      color: '#000000',
      align: 'center',
    }).setOrigin(0.5);
    this.add(this.suitText);

    this.setVisible(false);
  }

  showCard(seatCard: SeatCard): void {
    if (seatCard.showCard) {
      const rank = RANK_LABELS[seatCard.card.value] ?? '?';
      const suit = SUIT_SYMBOLS[seatCard.card.suit] ?? '';
      const color = SUIT_COLORS[seatCard.card.suit] ?? '#000000';
      this.drawFace(rank, suit, color);
    } else {
      this.drawBack();
    }
    this.setVisible(true);
  }

  showBack(): void {
    this.drawBack();
    this.setVisible(true);
  }

  hide(): void {
    this.setVisible(false);
  }

  setCardSize(width: number): void {
    this.cardWidth = width;
    this.cardHeight = Math.round(width * 1.4);
    this.cornerRadius = Math.max(3, Math.round(width * 0.1));
  }

  private drawFace(rank: string, suit: string, color: string): void {
    const w = this.cardWidth;
    const h = this.cardHeight;
    const r = this.cornerRadius;

    this.shadow.clear();
    this.shadow.fillStyle(0x000000, SHADOW_ALPHA);
    this.shadow.fillRoundedRect(-w / 2 + 1, -h / 2 + 3, w, h, r);

    this.bg.clear();
    this.bg.fillStyle(FACE_COLOR, 1);
    this.bg.fillRoundedRect(-w / 2, -h / 2, w, h, r);
    this.bg.lineStyle(1, BORDER_COLOR, BORDER_ALPHA);
    this.bg.strokeRoundedRect(-w / 2, -h / 2, w, h, r);

    const rankSize = Math.max(10, Math.round(w * 0.45));
    this.rankText.setFontSize(rankSize);
    this.rankText.setFontFamily(RANK_FONT);
    this.rankText.setColor(color);
    this.rankText.setText(rank);
    this.rankText.setPosition(0, -h * 0.12);
    this.rankText.setVisible(true);

    const suitSize = Math.max(12, Math.round(w * 0.6));
    this.suitText.setFontSize(suitSize);
    this.suitText.setFontFamily(RANK_FONT);
    this.suitText.setColor(color);
    this.suitText.setText(suit);
    this.suitText.setPosition(0, h * 0.18);
    this.suitText.setVisible(true);
  }

  private drawBack(): void {
    const w = this.cardWidth;
    const h = this.cardHeight;
    const r = this.cornerRadius;

    this.shadow.clear();
    this.shadow.fillStyle(0x000000, SHADOW_ALPHA);
    this.shadow.fillRoundedRect(-w / 2 + 1, -h / 2 + 3, w, h, r);

    this.bg.clear();
    this.bg.fillStyle(BACK_COLOR_1, 1);
    this.bg.fillRoundedRect(-w / 2, -h / 2, w, h, r);
    this.bg.lineStyle(1.5, BACK_COLOR_2, 0.7);
    this.bg.strokeRoundedRect(-w / 2 + 3, -h / 2 + 3, w - 6, h - 6, Math.max(1, r - 2));

    this.rankText.setVisible(false);
    this.suitText.setVisible(false);
  }
}
```

- [ ] **Step 2: Typecheck**

Run: `npm run build`
Expected: zero errors.

- [ ] **Step 3: Visual check**

Reload `http://localhost:4200/phaser-dev`.
Expected: community cards are now white with colored rank (red A, dark spade K, blue Q) and a drop shadow. Hole cards on seat 1 show face-up, seat 2 shows card backs.

- [ ] **Step 4: Commit**

```bash
git add src/app/game-lobby/phaser-table/game-objects/card-sprite.ts
git commit -m "feat: polish phaser cards with white face and colored rank/suit"
```

---

## Task 5: Community cards with placeholders

**Files:**
- Modify: `src/app/game-lobby/phaser-table/game-objects/community-cards.ts`

- [ ] **Step 1: Replace the community cards implementation**

Replace the contents of `/Users/tylervangorder/work/angular/angular-poker-client/src/app/game-lobby/phaser-table/game-objects/community-cards.ts` with:

```typescript
import Phaser from 'phaser';
import { Card } from '../../../poker/poker-models';
import { CardSprite } from './card-sprite';

const MAX_COMMUNITY_CARDS = 5;
const PLACEHOLDER_ALPHA = 0.12;

export class CommunityCards extends Phaser.GameObjects.Container {
  private cards: CardSprite[] = [];
  private placeholders: Phaser.GameObjects.Graphics;
  private cardWidth = 48;

  constructor(scene: Phaser.Scene, x: number, y: number) {
    super(scene, x, y);
    scene.add.existing(this);

    this.placeholders = new Phaser.GameObjects.Graphics(scene);
    this.add(this.placeholders);

    for (let i = 0; i < MAX_COMMUNITY_CARDS; i++) {
      const card = new CardSprite(scene, 0, 0);
      card.hide();
      this.cards.push(card);
      this.add(card);
    }
  }

  updateCards(communityCards: Card[]): void {
    const spacing = this.cardWidth + 8;
    const totalWidth = (MAX_COMMUNITY_CARDS - 1) * spacing;
    const startX = -totalWidth / 2;
    const h = Math.round(this.cardWidth * 1.4);
    const r = Math.max(3, Math.round(this.cardWidth * 0.1));

    // Placeholder outlines for all 5 positions
    this.placeholders.clear();
    this.placeholders.lineStyle(1.5, 0xffffff, PLACEHOLDER_ALPHA);
    for (let i = 0; i < MAX_COMMUNITY_CARDS; i++) {
      const px = startX + i * spacing;
      this.placeholders.strokeRoundedRect(
        px - this.cardWidth / 2, -h / 2,
        this.cardWidth, h, r
      );
    }

    for (let i = 0; i < MAX_COMMUNITY_CARDS; i++) {
      this.cards[i].setCardSize(this.cardWidth);
      if (i < communityCards.length) {
        this.cards[i].setPosition(startX + i * spacing, 0);
        this.cards[i].showCard({ card: communityCards[i], showCard: true });
      } else {
        this.cards[i].hide();
      }
    }
  }

  resize(cardWidth: number): void {
    this.cardWidth = cardWidth;
  }
}
```

- [ ] **Step 2: Typecheck**

Run: `npm run build`
Expected: zero errors.

- [ ] **Step 3: Visual check**

Reload `http://localhost:4200/phaser-dev`.
Expected: three face-up community cards (A♥ K♠ Q♦) centered on the felt; two faint dashed outlines to their right for the turn/river slots.

- [ ] **Step 4: Commit**

```bash
git add src/app/game-lobby/phaser-table/game-objects/community-cards.ts
git commit -m "feat: add dashed placeholders for undealt community cards"
```

---

## Task 6: Polished pot display

**Files:**
- Modify: `src/app/game-lobby/phaser-table/game-objects/pot-display.ts`

- [ ] **Step 1: Replace the pot display implementation**

Replace the contents of `/Users/tylervangorder/work/angular/angular-poker-client/src/app/game-lobby/phaser-table/game-objects/pot-display.ts` with:

```typescript
import Phaser from 'phaser';
import { Pot } from '../../game-models';

const PILL_BG_ALPHA = 0.5;
const ACCENT_COLOR = 0xf5d678;
const ACCENT_COLOR_STR = '#f5d678';
const CHIP_BORDER_COLOR = 0xc49a38;
const LABEL_COLOR = 'rgba(255,255,255,0.5)';

export class PotDisplay extends Phaser.GameObjects.Container {
  private bg: Phaser.GameObjects.Graphics;
  private chip: Phaser.GameObjects.Graphics;
  private label: Phaser.GameObjects.Text;
  private amount: Phaser.GameObjects.Text;
  private chipSize = 18;
  private labelSize = 9;
  private amountSize = 15;

  constructor(scene: Phaser.Scene, x: number, y: number) {
    super(scene, x, y);
    scene.add.existing(this);

    this.bg = new Phaser.GameObjects.Graphics(scene);
    this.add(this.bg);

    this.chip = new Phaser.GameObjects.Graphics(scene);
    this.add(this.chip);

    this.label = new Phaser.GameObjects.Text(scene, 0, 0, 'POT', {
      fontFamily: 'system-ui, sans-serif',
      fontSize: `${this.labelSize}px`,
      color: LABEL_COLOR,
      fontStyle: 'bold',
    }).setOrigin(0, 0.5);
    this.add(this.label);

    this.amount = new Phaser.GameObjects.Text(scene, 0, 0, '', {
      fontFamily: 'system-ui, sans-serif',
      fontSize: `${this.amountSize}px`,
      color: ACCENT_COLOR_STR,
      fontStyle: 'bold',
    }).setOrigin(0, 0.5);
    this.add(this.amount);

    this.setVisible(false);
  }

  updatePots(pots: Pot[]): void {
    const total = pots.reduce((s, p) => s + p.amount, 0);
    if (total <= 0) {
      this.setVisible(false);
      return;
    }

    this.amount.setText('$' + (total / 100).toFixed(2));
    this.redraw();
    this.setVisible(true);
  }

  resize(chipSize: number, labelSize: number, amountSize: number): void {
    this.chipSize = chipSize;
    this.labelSize = labelSize;
    this.amountSize = amountSize;
    this.label.setFontSize(labelSize);
    this.amount.setFontSize(amountSize);
    this.redraw();
  }

  private redraw(): void {
    const pad = 10;
    const chipSize = this.chipSize;
    const textW = Math.max(this.label.width, this.amount.width);
    const pillW = chipSize + pad + textW + pad * 2;
    const pillH = Math.max(chipSize + 8, this.amountSize + this.labelSize + 4);

    this.bg.clear();
    this.bg.fillStyle(0x000000, PILL_BG_ALPHA);
    this.bg.fillRoundedRect(-pillW / 2, -pillH / 2, pillW, pillH, pillH / 2);
    this.bg.lineStyle(1, ACCENT_COLOR, 0.25);
    this.bg.strokeRoundedRect(-pillW / 2, -pillH / 2, pillW, pillH, pillH / 2);

    this.chip.clear();
    const chipX = -pillW / 2 + pad + chipSize / 2;
    this.chip.fillStyle(ACCENT_COLOR, 1);
    this.chip.fillCircle(chipX, 0, chipSize / 2);
    this.chip.lineStyle(2, CHIP_BORDER_COLOR, 1);
    this.chip.strokeCircle(chipX, 0, chipSize / 2);

    this.label.setPosition(chipX + chipSize / 2 + pad, -this.amountSize / 2 + 1);
    this.amount.setPosition(chipX + chipSize / 2 + pad, this.labelSize / 2 + 2);
  }
}
```

- [ ] **Step 2: Update scene to call the new `resize` signature**

Open `/Users/tylervangorder/work/angular/angular-poker-client/src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts`. Find:

```typescript
    const potFontSize = Math.max(11, Math.round(width * 0.014));
    this.potDisplay.setPosition(this.cx, this.cy + cardWidth * 1.0);
    this.potDisplay.setFontSize(potFontSize);
```

Replace with:

```typescript
    this.potDisplay.setPosition(this.cx, this.cy + cardWidth * 1.0);
    const chipSize = Math.max(10, width * 0.013);
    const labelSize = Math.max(8, Math.round(width * 0.007));
    const amountSize = Math.max(13, Math.round(width * 0.011));
    this.potDisplay.resize(chipSize, labelSize, amountSize);
```

- [ ] **Step 3: Typecheck**

Run: `npm run build`
Expected: zero errors.

- [ ] **Step 4: Visual check**

Reload `http://localhost:4200/phaser-dev`.
Expected: gold pot pill below community cards — chip on left, "POT" label above "$12.00" amount.

- [ ] **Step 5: Commit**

```bash
git add src/app/game-lobby/phaser-table/game-objects/pot-display.ts src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts
git commit -m "feat: polish phaser pot display as gold pill with chip"
```

---

## Task 7: Dealer button polish

**Files:**
- Modify: `src/app/game-lobby/phaser-table/game-objects/dealer-button.ts`

- [ ] **Step 1: Replace the dealer button implementation**

Replace the contents of `/Users/tylervangorder/work/angular/angular-poker-client/src/app/game-lobby/phaser-table/game-objects/dealer-button.ts` with:

```typescript
import Phaser from 'phaser';

const FILL_COLOR = 0xffffff;
const BORDER_COLOR = 0x000000;
const BORDER_ALPHA = 0.15;
const LABEL_COLOR = '#000000';

export class DealerButton extends Phaser.GameObjects.Container {
  private circle: Phaser.GameObjects.Graphics;
  private label: Phaser.GameObjects.Text;
  private radius = 10;

  constructor(scene: Phaser.Scene) {
    super(scene, 0, 0);
    scene.add.existing(this);

    this.circle = new Phaser.GameObjects.Graphics(scene);
    this.add(this.circle);

    this.label = new Phaser.GameObjects.Text(scene, 0, 0, 'D', {
      fontSize: '12px',
      fontFamily: 'system-ui, sans-serif',
      fontStyle: 'bold',
      color: LABEL_COLOR,
      align: 'center',
    }).setOrigin(0.5);
    this.add(this.label);

    this.redraw();
    this.setVisible(false);
  }

  show(x: number, y: number): void {
    this.setPosition(x, y);
    this.setVisible(true);
  }

  hide(): void {
    this.setVisible(false);
  }

  resize(radius: number): void {
    this.radius = radius;
    this.label.setFontSize(Math.max(8, Math.round(radius * 0.9)));
    this.redraw();
  }

  private redraw(): void {
    this.circle.clear();
    this.circle.fillStyle(FILL_COLOR, 1);
    this.circle.fillCircle(0, 0, this.radius);
    this.circle.lineStyle(1, BORDER_COLOR, BORDER_ALPHA);
    this.circle.strokeCircle(0, 0, this.radius);
  }
}
```

- [ ] **Step 2: Typecheck**

Run: `npm run build`
Expected: zero errors.

- [ ] **Step 3: Visual check**

Reload `http://localhost:4200/phaser-dev`.
Expected: small white "D" button on the felt between seat 1 (You) and the table center.

- [ ] **Step 4: Commit**

```bash
git add src/app/game-lobby/phaser-table/game-objects/dealer-button.ts
git commit -m "feat: polish phaser dealer button styling"
```

---

## Task 8: Bet chip game object (new)

**Files:**
- Create: `src/app/game-lobby/phaser-table/game-objects/bet-chip.ts`

- [ ] **Step 1: Create the bet-chip game object**

Create `/Users/tylervangorder/work/angular/angular-poker-client/src/app/game-lobby/phaser-table/game-objects/bet-chip.ts`:

```typescript
import Phaser from 'phaser';

const COLOR_1 = 0xf5d678;
const COLOR_2 = 0xd4a843;
const TEXT_COLOR = '#1a0a00';
const PAD_X = 8;
const PAD_Y = 2;

export class BetChip extends Phaser.GameObjects.Container {
  private bg: Phaser.GameObjects.Graphics;
  private text: Phaser.GameObjects.Text;
  private fontSize = 10;

  constructor(scene: Phaser.Scene, x: number, y: number) {
    super(scene, x, y);
    scene.add.existing(this);

    this.bg = new Phaser.GameObjects.Graphics(scene);
    this.add(this.bg);

    this.text = new Phaser.GameObjects.Text(scene, 0, 0, '', {
      fontFamily: 'system-ui, sans-serif',
      fontSize: `${this.fontSize}px`,
      color: TEXT_COLOR,
      fontStyle: 'bold',
    }).setOrigin(0.5);
    this.add(this.text);

    this.setVisible(false);
  }

  setAmount(cents: number): void {
    if (cents <= 0) {
      this.setVisible(false);
      return;
    }
    this.text.setText('$' + (cents / 100).toFixed(2));
    this.redraw();
    this.setVisible(true);
  }

  resize(fontSize: number): void {
    this.fontSize = fontSize;
    this.text.setFontSize(fontSize);
    this.redraw();
  }

  private redraw(): void {
    const w = this.text.width + PAD_X * 2;
    const h = this.text.height + PAD_Y * 2;
    this.bg.clear();
    this.bg.fillStyle(COLOR_1, 1);
    this.bg.fillRoundedRect(-w / 2, -h / 2, w, h, h / 2);
    this.bg.lineStyle(1, COLOR_2, 1);
    this.bg.strokeRoundedRect(-w / 2, -h / 2, w, h, h / 2);
  }
}
```

- [ ] **Step 2: Typecheck**

Run: `npm run build`
Expected: zero errors (file is unused at this point — scene wires it in Task 11).

- [ ] **Step 3: Commit**

```bash
git add src/app/game-lobby/phaser-table/game-objects/bet-chip.ts
git commit -m "feat: add BetChip game object"
```

---

## Task 9: Polished seat pod (structure, avatar, glow)

**Files:**
- Modify: `src/app/game-lobby/phaser-table/game-objects/seat-display.ts`

This task rewrites seat-display to match the playground pod — inline hole cards, avatar circle with initial letter, active-seat glow. Timer bar is added in Task 10.

- [ ] **Step 1: Replace the seat display**

Replace the contents of `/Users/tylervangorder/work/angular/angular-poker-client/src/app/game-lobby/phaser-table/game-objects/seat-display.ts` with:

```typescript
import Phaser from 'phaser';
import { CardSprite } from './card-sprite';
import { SeatCard } from '../../../game/game-models';

const POD_BG = 0x0f0a05;
const POD_BG_ALPHA = 0.8;
const POD_BORDER_IDLE = 0xffffff;
const POD_BORDER_IDLE_ALPHA = 0.08;
const POD_BORDER_ACTIVE = 0xf5d678;
const ACTIVE_GLOW_INTENSITY = 0.25;

const AVATAR_BG = 0x5a4428;
const AVATAR_RING_COLOR = 0xdaa520;
const AVATAR_RING_ALPHA = 0.3;
const AVATAR_INITIAL_COLOR = '#ffffff';
const NAME_COLOR = '#ffffff';
const STACK_COLOR = 'rgba(255,255,255,0.55)';

const EMPTY_COLOR = 0xffffff;
const EMPTY_ALPHA = 0.18;
const EMPTY_COLOR_STR = '#ffffff';

export interface SeatSizing {
  podPadX: number;
  podPadY: number;
  podRadius: number;
  avatarSize: number;
  avatarRingWidth: number;
  nameSize: number;
  stackSize: number;
  holeWidth: number;
  emptySize: number;
}

export class SeatDisplay extends Phaser.GameObjects.Container {
  private bg: Phaser.GameObjects.Graphics;
  private glow: Phaser.GameObjects.Graphics;
  private avatarBg: Phaser.GameObjects.Graphics;
  private avatarRing: Phaser.GameObjects.Graphics;
  private initialText: Phaser.GameObjects.Text;
  private nameText: Phaser.GameObjects.Text;
  private stackText: Phaser.GameObjects.Text;
  private card1: CardSprite;
  private card2: CardSprite;

  // Empty-slot visuals
  private emptyRing: Phaser.GameObjects.Graphics;
  private emptyPlus: Phaser.GameObjects.Text;

  private sizing: SeatSizing = {
    podPadX: 10, podPadY: 6, podRadius: 12,
    avatarSize: 36, avatarRingWidth: 2,
    nameSize: 11, stackSize: 10,
    holeWidth: 44, emptySize: 28,
  };

  constructor(scene: Phaser.Scene, x: number, y: number) {
    super(scene, x, y);
    scene.add.existing(this);

    this.glow = new Phaser.GameObjects.Graphics(scene);
    this.add(this.glow);

    this.bg = new Phaser.GameObjects.Graphics(scene);
    this.add(this.bg);

    this.avatarBg = new Phaser.GameObjects.Graphics(scene);
    this.add(this.avatarBg);

    this.initialText = new Phaser.GameObjects.Text(scene, 0, 0, '', {
      fontFamily: 'system-ui, sans-serif',
      fontSize: '14px',
      fontStyle: 'bold',
      color: AVATAR_INITIAL_COLOR,
    }).setOrigin(0.5);
    this.add(this.initialText);

    this.avatarRing = new Phaser.GameObjects.Graphics(scene);
    this.add(this.avatarRing);

    this.nameText = new Phaser.GameObjects.Text(scene, 0, 0, '', {
      fontFamily: 'system-ui, sans-serif',
      fontSize: '11px',
      fontStyle: 'bold',
      color: NAME_COLOR,
    }).setOrigin(0, 0.5);
    this.add(this.nameText);

    this.stackText = new Phaser.GameObjects.Text(scene, 0, 0, '', {
      fontFamily: 'system-ui, sans-serif',
      fontSize: '10px',
      color: STACK_COLOR,
    }).setOrigin(0, 0.5);
    this.add(this.stackText);

    this.card1 = new CardSprite(scene, 0, 0);
    this.add(this.card1);
    this.card1.hide();

    this.card2 = new CardSprite(scene, 0, 0);
    this.add(this.card2);
    this.card2.hide();

    this.emptyRing = new Phaser.GameObjects.Graphics(scene);
    this.add(this.emptyRing);

    this.emptyPlus = new Phaser.GameObjects.Text(scene, 0, 0, '+', {
      fontFamily: 'system-ui, sans-serif',
      fontSize: '18px',
      fontStyle: 'bold',
      color: EMPTY_COLOR_STR,
    }).setOrigin(0.5).setAlpha(EMPTY_ALPHA);
    this.add(this.emptyPlus);

    this.setVisible(false);
  }

  applySizing(sizing: SeatSizing): void {
    this.sizing = sizing;
  }

  updateSeat(
    playerName: string | null,
    chipCount: number | null,
    cards: SeatCard[] | null,
    isActive: boolean,
  ): void {
    if (!playerName) {
      this.renderEmpty();
      return;
    }
    this.renderOccupied(playerName, chipCount, cards, isActive);
  }

  private renderEmpty(): void {
    this.setVisible(true);
    // Hide occupied visuals
    this.glow.clear();
    this.bg.clear();
    this.avatarBg.clear();
    this.avatarRing.clear();
    this.initialText.setVisible(false);
    this.nameText.setVisible(false);
    this.stackText.setVisible(false);
    this.card1.hide();
    this.card2.hide();
    // Empty placeholder
    const r = this.sizing.emptySize / 2;
    this.emptyRing.clear();
    this.emptyRing.lineStyle(1.5, EMPTY_COLOR, EMPTY_ALPHA);
    this.emptyRing.strokeCircle(0, 0, r);
    this.emptyPlus.setFontSize(Math.round(this.sizing.emptySize * 0.6));
    this.emptyPlus.setVisible(true);
  }

  private renderOccupied(
    playerName: string,
    chipCount: number | null,
    cards: SeatCard[] | null,
    isActive: boolean,
  ): void {
    this.setVisible(true);
    // Hide empty visuals
    this.emptyRing.clear();
    this.emptyPlus.setVisible(false);

    const s = this.sizing;
    const nameFontSize = Math.max(10, s.nameSize);
    const stackFontSize = Math.max(9, s.stackSize);

    this.nameText.setFontSize(nameFontSize);
    this.nameText.setText(this.truncateName(playerName));
    this.stackText.setFontSize(stackFontSize);
    this.stackText.setText(chipCount != null ? this.formatChips(chipCount) : '');

    const textW = Math.max(this.nameText.width, this.stackText.width);
    const holeW = s.holeWidth;
    const holeH = Math.round(holeW * 1.4);
    const holeGap = 3;
    const holeAreaW = holeW * 2 + holeGap + s.podPadX;
    const podW = s.avatarSize + s.podPadX + textW + s.podPadX + holeAreaW + s.podPadX;
    const podH = Math.max(
      s.avatarSize + s.podPadY * 2,
      nameFontSize + stackFontSize + 10,
      holeH + s.podPadY * 2
    );

    // Glow behind active pod
    this.glow.clear();
    if (isActive) {
      const glowPad = 8;
      for (let g = 0; g < 4; g++) {
        const a = ACTIVE_GLOW_INTENSITY * (1 - g / 4) * 0.5;
        this.glow.fillStyle(POD_BORDER_ACTIVE, a);
        this.glow.fillRoundedRect(
          -podW / 2 - glowPad * (g + 1),
          -podH / 2 - glowPad * (g + 1),
          podW + glowPad * 2 * (g + 1),
          podH + glowPad * 2 * (g + 1),
          s.podRadius + glowPad * (g + 1)
        );
      }
    }

    // Background pod
    this.bg.clear();
    this.bg.fillStyle(POD_BG, POD_BG_ALPHA);
    this.bg.fillRoundedRect(-podW / 2, -podH / 2, podW, podH, s.podRadius);
    const borderColor = isActive ? POD_BORDER_ACTIVE : POD_BORDER_IDLE;
    const borderAlpha = isActive ? 1 : POD_BORDER_IDLE_ALPHA;
    this.bg.lineStyle(1, borderColor, borderAlpha);
    this.bg.strokeRoundedRect(-podW / 2, -podH / 2, podW, podH, s.podRadius);

    // Avatar
    const avX = -podW / 2 + s.podPadY + s.avatarSize / 2;
    this.avatarBg.clear();
    this.avatarBg.fillStyle(AVATAR_BG, 1);
    this.avatarBg.fillCircle(avX, 0, s.avatarSize / 2);

    this.initialText.setFontSize(Math.round(s.avatarSize * 0.4));
    this.initialText.setText(playerName.charAt(0));
    this.initialText.setPosition(avX, 0);
    this.initialText.setVisible(true);

    this.avatarRing.clear();
    this.avatarRing.lineStyle(s.avatarRingWidth, AVATAR_RING_COLOR, AVATAR_RING_ALPHA);
    this.avatarRing.strokeCircle(avX, 0, s.avatarSize / 2);

    // Name / stack
    const textX = avX + s.avatarSize / 2 + s.podPadX;
    this.nameText.setPosition(textX, -stackFontSize / 2 - 1);
    this.nameText.setVisible(true);
    this.stackText.setPosition(textX, nameFontSize / 2 + 1);
    this.stackText.setVisible(true);

    // Hole cards inline (right side)
    const holeStartX = textX + textW + s.podPadX;
    const cx1 = holeStartX + holeW / 2;
    const cx2 = cx1 + holeW + holeGap;

    this.card1.setCardSize(holeW);
    this.card1.setPosition(cx1, 0);
    if (cards && cards.length >= 1) {
      this.card1.showCard(cards[0]);
    } else {
      this.card1.hide();
    }

    this.card2.setCardSize(holeW);
    this.card2.setPosition(cx2, 0);
    if (cards && cards.length >= 2) {
      this.card2.showCard(cards[1]);
    } else {
      this.card2.hide();
    }
  }

  private truncateName(name: string): string {
    return name.length > 10 ? name.substring(0, 9) + '\u2026' : name;
  }

  private formatChips(cents: number): string {
    return '$' + (cents / 100).toFixed(2);
  }
}
```

- [ ] **Step 2: Update scene to call `applySizing` instead of old `resize`**

Open `/Users/tylervangorder/work/angular/angular-poker-client/src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts`. Locate the existing seat sizing block:

```typescript
    const seatWidth = Math.max(80, width * 0.09);
    const seatHeight = Math.max(40, height * 0.08);

    for (let i = 0; i < MAX_SEATS; i++) {
      this.seats[i].setPosition(this.seatPositions[i].x, this.seatPositions[i].y);
      this.seats[i].resize(seatWidth, seatHeight);
    }
```

Replace with:

```typescript
    const seatSizing = {
      podPadX: Math.max(6, width * 0.007),
      podPadY: Math.max(4, width * 0.004),
      podRadius: Math.max(8, width * 0.009),
      avatarSize: Math.max(28, width * 0.026),
      avatarRingWidth: 2,
      nameSize: Math.max(10, Math.round(width * 0.008)),
      stackSize: Math.max(9, Math.round(width * 0.007)),
      holeWidth: Math.max(32, width * 0.031),
      emptySize: Math.max(20, width * 0.02),
    };

    for (let i = 0; i < MAX_SEATS; i++) {
      this.seats[i].setPosition(this.seatPositions[i].x, this.seatPositions[i].y);
      this.seats[i].applySizing(seatSizing);
    }
```

- [ ] **Step 3: Typecheck**

Run: `npm run build`
Expected: zero errors.

- [ ] **Step 4: Visual check**

Reload `http://localhost:4200/phaser-dev`.
Expected: six pods around the felt, each with initial-letter avatar circle, name, stack, and hole cards inline on the right. Seat 3 (Ari) has a gold glow. Seats 7, 8, 9 (the unoccupied positions per the dev harness state) show the empty `+` placeholder.

- [ ] **Step 5: Commit**

```bash
git add src/app/game-lobby/phaser-table/game-objects/seat-display.ts src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts
git commit -m "feat: polish phaser seat pods with inline cards, avatar, glow, empty placeholders"
```

---

## Task 10: Timer bar on active seat

**Files:**
- Modify: `src/app/game-lobby/phaser-table/game-objects/seat-display.ts`

- [ ] **Step 1: Add timer bar graphics and `setTimer` method**

In `/Users/tylervangorder/work/angular/angular-poker-client/src/app/game-lobby/phaser-table/game-objects/seat-display.ts`, add these constants near the top:

```typescript
const TIMER_COLOR = 0xf5d678;
const TIMER_TRACK_COLOR = 0x000000;
const TIMER_TRACK_ALPHA = 0.4;
const TIMER_INSET_X = 6;
const TIMER_GAP = 4;
```

Add to the `SeatSizing` interface a new property:

```typescript
  timerHeight: number;
```

Add to the default `sizing` field in the class:

```typescript
    timerHeight: 3,
```

Add a field to the class:

```typescript
  private timer: Phaser.GameObjects.Graphics;
  private timerFrac = 0;
  private timerPodW = 0;
  private timerPodH = 0;
  private timerActive = false;
```

In the constructor, after creating `this.bg`, add:

```typescript
    this.timer = new Phaser.GameObjects.Graphics(scene);
    this.add(this.timer);
```

At the end of `renderOccupied`, after the hole-card block, stash the pod dimensions so the timer knows them, then redraw the timer with current fraction:

```typescript
    this.timerPodW = podW;
    this.timerPodH = podH;
    this.timerActive = isActive;
    this.redrawTimer();
```

In `renderEmpty`, also clear the timer:

```typescript
    this.timer.clear();
    this.timerActive = false;
```

Add a public method:

```typescript
  setTimer(frac: number): void {
    this.timerFrac = Math.max(0, Math.min(1, frac));
    if (this.timerActive) this.redrawTimer();
  }
```

Add a private method:

```typescript
  private redrawTimer(): void {
    this.timer.clear();
    if (!this.timerActive) return;
    const barH = this.sizing.timerHeight;
    const fullW = this.timerPodW - TIMER_INSET_X * 2;
    const barY = this.timerPodH / 2 + TIMER_GAP + barH / 2;
    // Track
    this.timer.fillStyle(TIMER_TRACK_COLOR, TIMER_TRACK_ALPHA);
    this.timer.fillRoundedRect(-fullW / 2, barY - barH / 2, fullW, barH, barH / 2);
    // Progress
    if (this.timerFrac > 0) {
      const w = fullW * this.timerFrac;
      this.timer.fillStyle(TIMER_COLOR, 1);
      this.timer.fillRoundedRect(-fullW / 2, barY - barH / 2, w, barH, barH / 2);
    }
  }
```

- [ ] **Step 2: Update the scene's seat sizing to pass `timerHeight`**

Open `/Users/tylervangorder/work/angular/angular-poker-client/src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts`. Locate the `seatSizing` object (added in Task 9) and add `timerHeight` at the end:

```typescript
    const seatSizing = {
      podPadX: Math.max(6, width * 0.007),
      podPadY: Math.max(4, width * 0.004),
      podRadius: Math.max(8, width * 0.009),
      avatarSize: Math.max(28, width * 0.026),
      avatarRingWidth: 2,
      nameSize: Math.max(10, Math.round(width * 0.008)),
      stackSize: Math.max(9, Math.round(width * 0.007)),
      holeWidth: Math.max(32, width * 0.031),
      emptySize: Math.max(20, width * 0.02),
      timerHeight: Math.max(2, Math.round(width * 0.002)),
    };
```

- [ ] **Step 3: Typecheck**

Run: `npm run build`
Expected: zero errors.

- [ ] **Step 4: Visual check**

Reload `http://localhost:4200/phaser-dev`.
Expected: a gold track with no progress fill (frac defaults to 0) appears below the active pod (seat 3, Ari). Task 11 wires up real `frac` values.

- [ ] **Step 5: Commit**

```bash
git add src/app/game-lobby/phaser-table/game-objects/seat-display.ts src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts
git commit -m "feat: add timer bar graphics to active seat pod"
```

---

## Task 11: Scene orchestration (bet chips, timer tick, empty seats, new sizes)

**Files:**
- Modify: `src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts`

This task wires bet chips through the scene, adds the per-frame `update()` that ticks the timer, and ensures every occupied seat gets called with a `null` player (to render the empty placeholder).

- [ ] **Step 1: Replace the scene file**

Replace the contents of `/Users/tylervangorder/work/angular/angular-poker-client/src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts` with:

```typescript
import Phaser from 'phaser';
import { Subscription } from 'rxjs';
import { PhaserBridgeService } from '../phaser-bridge.service';
import { TableState, PlayerState } from '../../../game/game-state.service';
import { TableFelt } from '../game-objects/table-felt';
import { SeatDisplay } from '../game-objects/seat-display';
import { CommunityCards } from '../game-objects/community-cards';
import { PotDisplay } from '../game-objects/pot-display';
import { DealerButton } from '../game-objects/dealer-button';
import { BetChip } from '../game-objects/bet-chip';
import { getAllSeatPositions, MAX_SEATS, SeatPosition } from '../utils/seat-layout';

export class PokerTableScene extends Phaser.Scene {
  private bridge!: PhaserBridgeService;
  private tableFelt!: TableFelt;
  private seats: SeatDisplay[] = [];
  private betChips: BetChip[] = [];
  private communityCards!: CommunityCards;
  private potDisplay!: PotDisplay;
  private dealerButton!: DealerButton;

  private subscriptions: Subscription[] = [];
  private currentTableState: TableState | null = null;
  private currentPlayers: PlayerState[] = [];
  private currentUserId = '';

  // Layout cache
  private cx = 0;
  private cy = 0;
  private tableRx = 0;
  private tableRy = 0;
  private seatPositions: SeatPosition[] = [];

  // Timer tracking
  private timerDeadlineMs: number | null = null;
  private timerTotalMs = 30000;

  constructor() {
    super({ key: 'PokerTableScene' });
  }

  init(data: { bridge: PhaserBridgeService }): void {
    this.bridge = data.bridge;
  }

  create(): void {
    this.tableFelt = new TableFelt(this);

    for (let i = 0; i < MAX_SEATS; i++) {
      this.seats.push(new SeatDisplay(this, 0, 0));
      this.betChips.push(new BetChip(this, 0, 0));
    }

    this.communityCards = new CommunityCards(this, 0, 0);
    this.potDisplay = new PotDisplay(this, 0, 0);
    this.dealerButton = new DealerButton(this);

    this.layoutAll(this.scale.width, this.scale.height);

    this.scale.on('resize', (gameSize: Phaser.Structs.Size) => {
      this.layoutAll(gameSize.width, gameSize.height);
      this.renderState();
    });

    this.subscriptions.push(
      this.bridge.tableState$.subscribe((state) => {
        this.handleTableStateChange(state);
      }),
      this.bridge.players$.subscribe((players) => {
        this.currentPlayers = players;
        this.renderState();
      }),
      this.bridge.currentUserId$.subscribe((userId) => {
        this.currentUserId = userId;
        this.renderState();
      })
    );
  }

  override update(): void {
    if (this.timerDeadlineMs == null) return;
    const remainingMs = Math.max(0, this.timerDeadlineMs - Date.now());
    const frac = this.timerTotalMs > 0 ? remainingMs / this.timerTotalMs : 0;
    const pos = this.currentTableState?.actionPosition;
    if (pos != null && pos >= 1 && pos <= MAX_SEATS) {
      this.seats[pos - 1].setTimer(frac);
    }
  }

  private handleTableStateChange(state: TableState | null): void {
    this.currentTableState = state;

    if (state?.actionDeadline) {
      const deadlineMs = Date.parse(state.actionDeadline);
      // Latch the first observed remaining duration as the total time, so the
      // bar starts at ~1.0 and ticks down. If deadline is in the past, hide.
      if (deadlineMs > Date.now()) {
        const justObserved = deadlineMs - Date.now();
        if (this.timerDeadlineMs !== deadlineMs) {
          this.timerTotalMs = Math.max(1000, justObserved);
        }
        this.timerDeadlineMs = deadlineMs;
      } else {
        this.timerDeadlineMs = null;
      }
    } else {
      this.timerDeadlineMs = null;
    }

    this.renderState();
  }

  private layoutAll(width: number, height: number): void {
    this.cx = width / 2;
    this.cy = height / 2;
    this.tableRx = width * 0.30;
    this.tableRy = this.tableRx / 2.4;

    const railWidth = Math.max(6, width * 0.01);
    this.tableFelt.draw(this.cx, this.cy, this.tableRx, this.tableRy, railWidth);

    const seatRx = this.tableRx * 1.22;
    const seatRy = this.tableRy * 1.35;
    this.seatPositions = getAllSeatPositions(this.cx, this.cy, seatRx, seatRy);

    const seatSizing = {
      podPadX: Math.max(6, width * 0.007),
      podPadY: Math.max(4, width * 0.004),
      podRadius: Math.max(8, width * 0.009),
      avatarSize: Math.max(28, width * 0.026),
      avatarRingWidth: 2,
      nameSize: Math.max(10, Math.round(width * 0.008)),
      stackSize: Math.max(9, Math.round(width * 0.007)),
      holeWidth: Math.max(32, width * 0.031),
      emptySize: Math.max(20, width * 0.02),
      timerHeight: Math.max(2, Math.round(width * 0.002)),
    };

    const betFontSize = Math.max(9, Math.round(width * 0.008));

    for (let i = 0; i < MAX_SEATS; i++) {
      this.seats[i].setPosition(this.seatPositions[i].x, this.seatPositions[i].y);
      this.seats[i].applySizing(seatSizing);
      // Bet chip sits between pod and table center
      const dx = this.cx - this.seatPositions[i].x;
      const dy = this.cy - this.seatPositions[i].y;
      const t = 0.25;
      this.betChips[i].setPosition(
        this.seatPositions[i].x + dx * t,
        this.seatPositions[i].y + dy * t
      );
      this.betChips[i].resize(betFontSize);
    }

    const communityCardWidth = Math.max(36, width * 0.051);
    this.communityCards.setPosition(this.cx, this.cy - communityCardWidth * 0.15);
    this.communityCards.resize(communityCardWidth);

    this.potDisplay.setPosition(this.cx, this.cy + communityCardWidth * 0.85);
    const chipSize = Math.max(10, width * 0.013);
    const labelSize = Math.max(8, Math.round(width * 0.007));
    const amountSize = Math.max(13, Math.round(width * 0.011));
    this.potDisplay.resize(chipSize, labelSize, amountSize);

    this.dealerButton.resize(Math.max(8, width * 0.008));
  }

  private renderState(): void {
    const ts = this.currentTableState;
    const players = this.currentPlayers;

    if (!ts) {
      for (const seat of this.seats) seat.updateSeat(null, null, null, false);
      for (const chip of this.betChips) chip.setAmount(0);
      this.communityCards.updateCards([]);
      this.potDisplay.updatePots([]);
      this.dealerButton.hide();
      return;
    }

    // Build seatPosition → PlayerState map
    const seatPlayerMap = new Map<number, PlayerState>();
    for (const p of players) {
      if (p.seatPosition != null) seatPlayerMap.set(p.seatPosition, p);
    }

    // Update seats (1-indexed)
    for (let pos = 1; pos <= MAX_SEATS; pos++) {
      const idx = pos - 1;
      const player = seatPlayerMap.get(pos);
      const cards = ts.seatCards.get(pos) ?? null;
      const isActive = ts.actionPosition === pos;

      if (player) {
        const name = player.userId === this.currentUserId ? 'You' : player.displayName;
        this.seats[idx].updateSeat(name, player.chipCount, cards, isActive);
      } else {
        this.seats[idx].updateSeat(null, null, null, false);
      }

      const summary = ts.seatSummaries.get(pos);
      this.betChips[idx].setAmount(summary?.currentBetAmount ?? 0);
    }

    this.communityCards.updateCards(ts.communityCards);
    this.potDisplay.updatePots(ts.pots);

    if (ts.dealerPosition != null && ts.dealerPosition >= 1 && ts.dealerPosition <= MAX_SEATS) {
      const seatPos = this.seatPositions[ts.dealerPosition - 1];
      const dx = this.cx - seatPos.x;
      const dy = this.cy - seatPos.y;
      const dist = Math.sqrt(dx * dx + dy * dy);
      if (dist > 0) {
        const t = 0.3;
        this.dealerButton.show(seatPos.x + dx * t, seatPos.y + dy * t);
      }
    } else {
      this.dealerButton.hide();
    }
  }

  shutdown(): void {
    for (const sub of this.subscriptions) sub.unsubscribe();
    this.subscriptions = [];
  }
}
```

- [ ] **Step 2: Typecheck**

Run: `npm run build`
Expected: zero errors.

- [ ] **Step 3: Visual check**

Reload `http://localhost:4200/phaser-dev`.
Expected:
- Six occupied pods with inline cards, avatars, names, stacks.
- Seats 7–9 show empty `+` placeholders.
- Active seat (3, Ari) has gold glow AND a gold timer bar below that counts down visibly — the dev harness sets `actionDeadline` to 18s from page load. Wait a few seconds, bar shrinks.
- Three gold bet-chip pills near seats 2, 4, 6.
- Gold pot pill below community cards showing `POT $12.00`.
- Dealer button at seat 1.

- [ ] **Step 4: Commit**

```bash
git add src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts
git commit -m "feat: scene orchestration for bet chips, timer tick, empty seats"
```

---

## Task 12: Production swap and dev harness cleanup

**Files:**
- Modify: `src/app/game-lobby/table-view/table-view.component.ts`
- Modify: `src/app/game-lobby/table-view/table-view.component.html`
- Modify: `src/app/app.routes.ts`
- Delete: `src/app/game-lobby/phaser-table/phaser-dev-page/` (directory)
- Modify: `CLAUDE.md`

- [ ] **Step 1: Swap the renderer in table-view.component.ts**

Replace the contents of `/Users/tylervangorder/work/angular/angular-poker-client/src/app/game-lobby/table-view/table-view.component.ts` with:

```typescript
import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PhaserTableComponent } from '../phaser-table/phaser-table.component';
import { TableState, PlayerState } from '../../game/game-state.service';
import { GameStatus } from '../../game/game-models';

@Component({
  selector: 'app-table-view',
  imports: [CommonModule, PhaserTableComponent],
  templateUrl: './table-view.component.html',
  styleUrl: './table-view.component.css',
})
export class TableViewComponent {
  @Input() tableState: TableState | null = null;
  @Input() players: PlayerState[] = [];
  @Input() gameStatus: GameStatus | null = null;
  @Input() currentUserId: string = '';

  get showOverlay(): boolean {
    return this.gameStatus === 'PAUSED' || this.gameStatus === 'BALANCING';
  }

  get overlayText(): string {
    if (this.gameStatus === 'PAUSED') return 'Game Paused';
    if (this.gameStatus === 'BALANCING') return 'Rebalancing Tables...';
    return '';
  }
}
```

- [ ] **Step 2: Update table-view.component.html**

Replace the contents of `/Users/tylervangorder/work/angular/angular-poker-client/src/app/game-lobby/table-view/table-view.component.html` with:

```html
<div class="game-background w-full h-full rounded-lg relative overflow-hidden">
  @if (tableState) {
    <app-phaser-table
      [tableState]="tableState"
      [players]="players"
      [currentUserId]="currentUserId"
    />
  }

  @if (showOverlay) {
    <div class="absolute inset-0 bg-black/60 flex items-center justify-center z-50 rounded-lg">
      <div class="text-center">
        <span class="loading loading-dots loading-lg text-warning mb-2"></span>
        <p class="text-2xl font-bold text-white">{{ overlayText }}</p>
      </div>
    </div>
  }
</div>
```

- [ ] **Step 3: Remove the dev harness route**

Replace the contents of `/Users/tylervangorder/work/angular/angular-poker-client/src/app/app.routes.ts` with:

```typescript
import { Routes } from '@angular/router';
import { TitlePageComponent } from './title-page/title-page.component';
import { HomePageComponent } from './home-page/home-page.component';
import { GameLobbyComponent } from './game-lobby/game-lobby.component';
import { authenticationGuard, loggedInGuard } from './auth-guard.service';
export const routes: Routes = [
  {
    path: '',
    component: TitlePageComponent,
    title: 'Chico Degens Poker Club',
    canActivate: [loggedInGuard]
  },
  {
    path: 'home',
    component: HomePageComponent,
    title: 'Chico Degens Poker Club',
    canActivate: [authenticationGuard]
  },
  {
    path: 'game/:gameId',
    component: GameLobbyComponent,
    title: 'Chico Degens Poker Club - Game Lobby',
    canActivate: [authenticationGuard]
  }
];
```

- [ ] **Step 4: Delete the dev harness component**

Run:

```bash
rm -r /Users/tylervangorder/work/angular/angular-poker-client/src/app/game-lobby/phaser-table/phaser-dev-page
```

- [ ] **Step 5: Update CLAUDE.md**

In `/Users/tylervangorder/work/angular/angular-poker-client/CLAUDE.md`, find the "Don't" bullet about `phaser`:

```markdown
- Don't remove the `phaser` dependency — it's intentionally retained for a planned retry at Phaser-based table rendering once the server basics are solid. Current table renderer is CSS (`game-lobby/css-poker-table/`); a parallel `game-lobby/phaser-table/` exists from the earlier experiment.
```

Replace with:

```markdown
- Don't remove the `phaser` dependency — the production table renderer is `game-lobby/phaser-table/`. The legacy `game-lobby/css-poker-table/` is parked on disk as a short-term rollback and scheduled for deletion in a follow-up.
```

Also update the "Feature Structure" block. Find:

```markdown
│   ├── css-poker-table/ # Current table renderer (CSS/SVG)
│   ├── phaser-table/    # Experimental Phaser renderer (inactive)
```

Replace with:

```markdown
│   ├── phaser-table/    # Active table renderer (Phaser)
│   ├── css-poker-table/ # Legacy CSS renderer (parked, pending deletion)
```

- [ ] **Step 6: Typecheck**

Run: `npm run build`
Expected: zero errors.

- [ ] **Step 7: Smoke test in a real game**

Run: `npm start`.
Navigate to `http://localhost:4200` → log in → create or join a game → observe the table.
Expected:
- Felt, seats, cards, pot, dealer button all render with the polished visuals.
- When it's a player's turn, their pod glows gold and the timer bar counts down.
- Bet amounts show as gold chips.
- Empty seats show `+` placeholders.
- Dragging the browser window smaller scales everything smoothly.

If anything looks wrong, do NOT commit. File a follow-up with specifics; fix before completing this task.

- [ ] **Step 8: Commit**

```bash
git add src/app/game-lobby/table-view/table-view.component.ts src/app/game-lobby/table-view/table-view.component.html src/app/app.routes.ts CLAUDE.md
git add -u src/app/game-lobby/phaser-table/phaser-dev-page
git commit -m "feat: swap production table renderer from CSS to Phaser"
```

---

## Self-Review Notes

- The plan implements every element in the spec: direct swap (Task 12), timer animation (Tasks 10–11), initial-letter fallback avatars (Task 9 — no avatar URL plumbing), empty-seat placeholder (Task 9), even seat distribution (Task 2), CSS renderer files remain on disk (Task 12 updates CLAUDE.md to flag deletion as a follow-up).
- No placeholders, TBDs, or "similar to Task N" references.
- Types are consistent: `SeatSizing` interface defined in Task 9, extended in Task 10 with `timerHeight`, consumed in Task 11 by the scene.
- Every task includes full code and a verification step (typecheck + visual).
- The dev harness route (`/phaser-dev`) is scaffolded in Task 1 and deleted in Task 12 so no dev-only code ships.
