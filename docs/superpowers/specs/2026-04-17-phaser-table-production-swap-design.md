# Phaser Table Production Swap — Design Spec

**Date:** 2026-04-17
**Status:** Design approved, pending implementation plan
**Related:** `docs/superpowers/specs/2026-04-17-phaser-table-playground-design.md` (the playground that validated these visuals)

## Context

The Angular poker client currently renders the poker table with the CSS/HTML component at `src/app/game-lobby/css-poker-table/`. A parallel experimental Phaser renderer exists at `src/app/game-lobby/phaser-table/` but is inactive — it was an earlier attempt that produced a flat, under-polished visual, which is why the CSS version shipped instead.

The `phaser-table-playground.html` built under the prior spec validated that Phaser can match the CSS polish when drawing uses canvas-2D radial-gradient textures (via `RenderTexture`), proper typography, inline hole cards, gold active-seat glow, and a bet-chip pill style. That playground is a disposable design tool — this spec is about bringing those validated visuals into production.

## Goal

Replace the CSS renderer with a polished Phaser renderer in production. Reuse the existing `src/app/game-lobby/phaser-table/` file structure (component, scene, bridge service, game-object files) but rewrite each element's drawing logic to match the playground. All data wiring stays on existing `TableState` / `PlayerState` / `seatSummaries` — no new backend contracts.

## Non-goals

- Card deal animation (from deck to seats).
- Chip slide animation (bets → pot on `betting-round-complete`).
- Avatar images. The initial-letter fallback ships; avatar URL plumbing is a follow-on.
- Mobile / small-viewport tuning. Desktop sizing only, matching current CSS behavior.
- New backend fields.
- Automated tests. The current `phaser-table/` and `css-poker-table/` directories have none; this plan does not introduce a test suite.
- Deletion of `css-poker-table/`. Parked on disk after the swap for short-term rollback; deleted in a follow-up.

## Migration strategy

**Direct swap.** In `table-view.component.html`, change `<app-css-poker-table>` to `<app-phaser-table>` and drop the import in `table-view.component.ts`. The CSS renderer files remain on disk (unused) for short-term rollback. A follow-up cleanup task deletes them after a stable period.

## Decisions summary

| Decision | Choice |
|---|---|
| Migration | Direct swap; CSS files stay parked on disk |
| Timer bar | Animated, driven by per-frame tick off `actionDeadline` |
| Avatars | Initial-letter fallback only; avatar URLs are a separate follow-on |
| Empty seat marker | Shown (faint `+` ring), matching playground prototype |
| Seat angles | Switch production to even 40° distribution (replacing the existing asymmetric 9-angle table) |
| CSS renderer fate | Keep parked on disk short-term; delete in a follow-up |

## File changes

### Rewrites (match playground visuals)

| File | Change |
|---|---|
| `src/app/game-lobby/phaser-table/game-objects/table-felt.ts` | Rewrite to use offscreen canvas-2D with radial gradient, uploaded as `RenderTexture`. Matches playground `drawFelt`. |
| `src/app/game-lobby/phaser-table/game-objects/seat-display.ts` | Hole cards rendered inline inside the pod (to the right of name/stack), pod height wraps the cards, active-seat gold glow (layered translucent rects), timer bar attached below the pod, initial-letter circular avatar. Empty-seat placeholder handled here or in the scene. |
| `src/app/game-lobby/phaser-table/game-objects/card-sprite.ts` | White face, rank and suit in colored text, rounded corners, drop shadow. Back style: solid blue with inner border. |
| `src/app/game-lobby/phaser-table/game-objects/community-cards.ts` | Wire new `card-sprite` styling; dashed outline for undealt slots. |
| `src/app/game-lobby/phaser-table/game-objects/pot-display.ts` | Gold pill with a small chip, "POT" label, and amount. Replaces the current plain-text output. |
| `src/app/game-lobby/phaser-table/game-objects/dealer-button.ts` | Minor font + color alignment with playground. |
| `src/app/game-lobby/phaser-table/utils/seat-layout.ts` | Replace `SEAT_ANGLES_DEG` with computed even 40° distribution starting at 90° (bottom center). |
| `src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts` | Orchestrate new pieces (bet chips, empty-seat placeholders), add `update(time, delta)` per-frame hook for the timer bar. |

### New file

| File | Purpose |
|---|---|
| `src/app/game-lobby/phaser-table/game-objects/bet-chip.ts` | Per-seat bet pill, sourcing amount from `seatSummaries[pos].currentBetAmount`. Positioned between pod and table center. |

### Integration changes

| File | Change |
|---|---|
| `src/app/game-lobby/table-view/table-view.component.html` | Swap `<app-css-poker-table>` → `<app-phaser-table>`. |
| `src/app/game-lobby/table-view/table-view.component.ts` | Replace `CssPokerTableComponent` import with `PhaserTableComponent` in `imports`. |
| `CLAUDE.md` | Flip the "active CSS / parallel Phaser" wording; add a note for the follow-up cleanup of `css-poker-table/`. |

## Scale-relative sizing

Playground defaults (e.g., `railWidth: 14`, `avatarSize: 36`) are fixed pixel values tuned for a ~1400px canvas. Production translates them into ratios of `scale.width` so the table renders correctly at any viewport size. Each game-object exposes a `resize(width, height)` method (most already do) that recomputes derived sizes. The scene's `layoutAll()` calls `resize` on every object.

Starter ratio table (final values tuned during implementation):

| Playground value | Production ratio |
|---|---|
| `railWidth: 14` | `width * 0.01` |
| `seats.podRadius: 12` | `width * 0.009` |
| `seats.avatarSize: 36` | `width * 0.026` |
| `seats.podPadX: 10` | `width * 0.007` |
| `seats.nameSize: 11` | `Math.max(10, width * 0.008)` |
| `seats.stackSize: 10` | `Math.max(9, width * 0.007)` |
| `cards.communityWidth: 72` | `width * 0.051` |
| `cards.holeWidth: 44` | `width * 0.031` |
| `cards.rankSize: 22` | `Math.max(14, width * 0.016)` |
| `pot.chipSize: 18` | `width * 0.013` |
| `pot.amountSize: 15` | `Math.max(13, width * 0.011)` |

Style constants (colors, alphas, font families) stay as module-level `const`s inside each game-object file — they don't need to scale.

## Timer animation

`TableScene` adds an `update(time, delta)` method (Phaser's per-frame hook). When `currentTableState.actionDeadline` is non-null:

```
const now = Date.now();
const deadline = Date.parse(actionDeadline);
const remaining = Math.max(0, deadline - now);
const frac = clampedFrac(remaining, totalMs);
activeSeat.setTimer(frac);
```

When `actionDeadline` is null (action clears), the active seat's timer is hidden.

`totalMs` (the denominator) is not currently exposed by the backend. We latch the max observed `remaining` value when `action-on-player` fires, and use that as the denominator until the next `action-on-player`. This is approximate — if the first tick arrives after some network delay, the bar starts slightly below 100% — but is acceptable for v1. A backend change to expose `timeToActSeconds` is flagged as a follow-up.

Only the timer bar re-renders per frame. Pod background, avatar, name, stack, cards, and other elements stay static until a state event triggers a redraw. Keeps GPU load minimal.

## Data flow

Bridge service (existing) pushes:
- `tableState$` — drives felt (unchanged visual), community cards, pots, dealer position, active seat, action deadline, seat bets (via `seatSummaries[].currentBetAmount`).
- `players$` — drives seat names, chip counts, user-to-seat mapping.
- `currentUserId$` — drives the "You" label swap for the current user's seat.

Scene subscribes to all three (existing) and calls `renderState()` when any changes. `update(time, delta)` handles the timer tick independent of state events.

## Seat layout change

`utils/seat-layout.ts` currently exports a fixed `SEAT_ANGLES_DEG = [90, 135, 170, 190, 225, 270, 315, 350, 10]` — asymmetric angles that cluster seats on the sides. Replace with an evenly-spaced computation starting at 90° (bottom center) with 360/9 = 40° spacing:

```typescript
const MAX_SEATS = 9;
const STEP_DEG = 360 / MAX_SEATS;
const START_DEG = 90;
// seat i (0-indexed) → angle START_DEG + i * STEP_DEG
```

Seat 1 stays at the bottom center; seats 2–9 redistribute. No game-state migration needed since seat positions are server-assigned indexes, not visual angles.

## Empty-seat placeholder

For each of the 9 seat positions not currently occupied by a player, render a faint placeholder at the seat position: a circular outline (`alpha 0.18`) with a small `+` glyph. Styling lives in a module-level constants block inside `seat-display.ts`. No config panel — values are static in production.

## Risks & open questions

- **Time-to-act denominator.** If the first frame after `action-on-player` arrives late (network jitter), the latched denominator is slightly low and the bar starts at < 100%. Acceptable for v1; if noticeably wrong in practice, request the backend to expose `timeToActSeconds` on `action-on-player`.
- **RenderTexture on Canvas vs WebGL.** Playground ran on `Phaser.AUTO` (picks WebGL when available, Canvas otherwise) and worked. If production hits a browser where the `addCanvas` texture path renders oddly, fall back to layered `Graphics` ellipses for the felt.
- **Font availability.** Playground used system fonts. Any non-system font used for card ranks needs a `<link>` in `src/index.html`; game-object code references the family name only.
- **First paint.** Phaser initializes slightly later than CSS renders (WebGL context creation). If the empty-canvas flash on slow devices is noticeable, add a one-frame loading overlay — out of scope unless QA flags it.
