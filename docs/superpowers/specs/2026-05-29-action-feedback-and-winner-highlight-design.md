# Action Feedback & Winner Highlighting — Design

## Problem

The Phaser table renders state accurately but communicates poorly when something happens:

- When a player acts, the only feedback is the chip stack growing on the felt and a plain-text line scrolling in the message log. There is no per-seat indication that *that specific player* just folded / called / raised. The "next to act" pointer silently jumps to a new seat.
- When a hand ends, winners are announced only as text in the message log (`Alice won $42.50 (Two Pair)`). The table itself shows no highlight, the cards stay on the felt without context, and side-pot winners are indistinguishable from main-pot winners.
- Folded seats look identical to active seats once the betting-round indicator clears, making it hard to see at a glance who is still in the hand.

This makes the table feel unresponsive and forces players to read the log to follow the action.

## Goals

1. Every player action produces an obvious, short-lived per-seat indicator naming what they did and (where relevant) how much.
2. The next-to-act seat is unambiguous even to a new viewer, not just to someone who already knows the glow convention.
3. Folded seats are visually de-emphasized so live seats stand out.
4. At showdown, winners are highlighted on the table with the amount won and the winning hand description. Side-pot winners are clearly attributed to the correct pot.
5. The message log records action history (currently only state-transition events are logged), and winner messages are styled distinctly from routine log lines.

## Non-Goals

- Pot-chip animation flying to the winner. Deferred — nice polish, not load-bearing.
- Sound effects.
- Modal / overlay celebrations.
- Replay scrubbing of the showdown.
- All-in equity / odds display.
- Touching the legacy `game-lobby/css-poker-table/` renderer.

## What's Already in Place

The state layer already contains nearly everything we need; this is mostly a render problem.

- `TableState.lastAction = { seatPosition, action }` is set on every `player-acted` event and cleared on `betting-round-complete` / `hand-complete`. Currently unused by any view.
- `TableState.potResults: PotResult[] | null` is populated by `showdown-result` and persists until the next `hand-started`. Each `PotResult` has `potIndex`, `potAmount`, and `winners[]` with `seatPosition`, `userId`, `amount`, `handDescription`. Currently used only to emit message-log lines.
- `TableState.seatSummaries` per-seat status (`ACTIVE` / `FOLDED` / `ALL_IN` / etc.) is already kept up to date.
- `SeatCard.showCard` already controls the post-hand reveal; winners' and showdown losers' cards face up automatically.
- The seat pod has a golden-glow "active" treatment and a timer bar.

## Design

### 1. New state: `actionSeq`

`lastAction` alone is not enough — two consecutive folds by different seats produce the same shape if both fold, and the subscriber must re-trigger the badge animation each time. Add an integer sequence number that monotonically increments on every action.

```ts
interface TableState {
  // ...existing fields
  lastAction: { seatPosition: number; action: string } | null;
  actionSeq: number;
}
```

Increment rules in `game-state.service.ts`:
- `hand-started` → reset `actionSeq = 0`.
- `player-acted` → set `lastAction`, `actionSeq += 1`.
- `player-timed-out` → set `lastAction` to the default action, `actionSeq += 1`.
- `betting-round-complete` / `hand-complete` → clear `lastAction = null` (existing behavior). `actionSeq` is left untouched; it monotonically increases within a hand and only resets at `hand-started`.

This is the only state change required by the feature.

### 2. New game object: `ActionBadge`

A reusable Phaser container, one per seat, owned by `PokerTableScene`. Renders one of these states:

| State | Visual | Trigger |
|---|---|---|
| `hidden` | invisible | default |
| `to-act` | small "TO ACT" pill, neutral gold | seat is `actionPosition` and has not yet acted this round |
| `fold` | "FOLD" pill, red | `lastAction.action === 'fold'` on this seat |
| `check` | "CHECK" pill, neutral white | `lastAction.action === 'check'` on this seat |
| `call` | "CALL $X" pill, blue | `lastAction.action === 'call'` |
| `bet` | "BET $X" pill, gold | `lastAction.action === 'bet'` |
| `raise` | "RAISE → $X" pill, gold | `lastAction.action === 'raise'` |
| `all-in` | "ALL IN" pill, gold with subtle pulse | resulting status `ALL_IN`, overrides `bet`/`call`/`raise` label |
| `winner` | two-line: `WON $X.XX` (or `WON POT N $X.XX`) on line 1, hand description on line 2; strong gold glow attached to seat pod | seat appears in `potResults` |

Anchor: positioned just outside the seat pod, on the **table-edge** side (opposite the bet chip, which sits on the felt-center side). The scene calculates the anchor from seat coords; the seat exposes its current pod bounds.

Behavior:
- **Action labels** (`fold`/`check`/`call`/`bet`/`raise`/`all-in`) auto-fade after **2500 ms**, OR earlier if another seat's badge becomes active (only one action badge visible at a time, so the eye is anchored to the most recent actor).
- **`to-act`** is not time-limited — it persists for the duration of `actionPosition === seatPos` and is replaced by the action badge the moment that seat acts.
- **`winner`** is not time-limited — it persists until the next `hand-started` clears `potResults`.

Amount in the badge text is derived from the chip delta in `seatSummaries` (the same derivation the existing state code does for `currentBetAmount`).

### 3. Folded seat treatment

When `seatSummaries.get(pos).status === 'FOLDED'`:
- Pod background alpha 0.8 → 0.4.
- Hole-card sprites get a greyscale tint and scale to 0.85.
- A small muted "FOLDED" label appears under the stack text (does **not** replace the stack — the stack stays visible because it's still meaningful at showdown).

Resets to normal on `hand-started`.

### 4. Next-to-act emphasis

Keep the existing golden glow + timer bar. Add the `to-act` action badge above the seat as a redundant, explicit cue. The pill is replaced by the seat's action badge the instant they act.

### 5. Winner highlighting at showdown

When `potResults != null`:

For each winner across all pots:
- **Pod glow**: alpha bumped from 0.25 → 0.5, with a 1.2 s `yoyo` alpha-pulse tween. Distinct from the "active" glow by intensity and pulse.
- **Winner badge** above the pod:
  - **Hand had a single pot**: `WON $42.50` / `Full House, Aces full of Kings`. No pot number shown.
  - **Hand had multiple pots, seat wins one**: `WON POT 2 — $12.50` / hand description. Pots are labeled by 1-based index of their position in `potResults` (POT 1 = main pot).
  - **Hand had multiple pots, seat wins more than one**: aggregate to `WON $54.00 (2 pots)` / hand description from the largest pot. (Rare; tested but not visually optimized.)
- **No-showdown win** (everyone else folded): server still emits `showdown-result` with one winner. If `handDescription` is empty or missing, badge shows only line 1 (`WON $X.XX`) and omits line 2.

For each losing seat with `SeatCard.showCard === true` (revealed but lost):
- Pod alpha 0.5, no glow.
- Their `handDescription` is **not** rendered on the table — only winners get descriptions, to keep the table readable. Full per-seat hand details remain in the message log.

Mucked seats (folded earlier, no reveal) keep their folded styling.

Persistence: from `showdown-result` until the next `hand-started`.

### 6. Messages panel changes

`messages-panel.component.html` learns to render a distinct row for showdown wins:
- Left accent border + bold `WINNER:` prefix using DaisyUI's `accent` color. No emoji.
- Today's winner messages are `GameMessageEvent` with a plain string. To classify them without parsing the message text, introduce an optional `kind` discriminator on synthetic client-generated messages: `kind?: 'showdown' | 'action'`. This is internal to the client; the wire `GameMessageEvent` type is unchanged. Existing untagged messages render as today's default style.
- Showdown wins are tagged `kind: 'showdown'`. The new action-history lines (next paragraph) are tagged `kind: 'action'` and rendered slightly muted to distinguish ephemeral play-by-play from structural events (hand started, player joined, etc.).

Add log lines for actions in `player-acted` handling:
- `fold` → `Alice folds`
- `check` → `Alice checks`
- `call` → `Alice calls $5.00`
- `bet` → `Alice bets $20.00`
- `raise` → `Alice raises to $20.00`
- All-in suffix when `resultingStatus === 'ALL_IN'`: append ` (all in)`.

Use `getDisplayName(userId)` and `LangUtils.formatCurrency` consistently.

### 7. Component / file impact

| File | Change |
|---|---|
| `src/app/game/game-state.service.ts` | Add `actionSeq` to `TableState`; increment on `player-acted` and `player-timed-out`; reset on `hand-started`. Emit action-history log lines on `player-acted`. Tag showdown winner messages with `kind: 'showdown'`. |
| `src/app/game/game-state.service.spec.ts` | Cover `actionSeq` increment / reset; action-message text per action type; showdown-message `kind` tag. |
| `src/app/game-lobby/phaser-table/game-objects/action-badge.ts` | **New.** Pure render-from-state Phaser container. Internal fade-tween for action states; pulse for `winner`; no fade for `to-act` / `winner`. |
| `src/app/game-lobby/phaser-table/game-objects/seat-display.ts` | Folded-seat dimming; expose a `getBadgeAnchor()` returning the world coords for the badge anchor; greyscale tint on hole cards when folded. |
| `src/app/game-lobby/phaser-table/scenes/poker-table.scene.ts` | Instantiate one `ActionBadge` per seat. Drive badge state from `(tableState, actionSeq, potResults)`. Track current "fresh" action seat so old badges fade when a new one fires. |
| `src/app/game-lobby/phaser-table/utils/action-badge-state.ts` | **New.** Pure function `computeBadgeState(seatPos, tableState, currentUserId): BadgeState`. Unit-tested independently of Phaser. |
| `src/app/game-lobby/phaser-table/utils/action-badge-state.spec.ts` | **New.** Tests for the pure function. |
| `src/app/game-lobby/messages-panel/messages-panel.component.ts` | Read `kind` discriminator; expose a styling helper. |
| `src/app/game-lobby/messages-panel/messages-panel.component.html` | Distinct row template for `kind === 'showdown'`. |
| `src/app/game-lobby/messages-panel/messages-panel.component.spec.ts` | Cover showdown styling. |

Total: 2 new files, ~7 modified.

### 8. Testing strategy

- **State service**: unit tests around `actionSeq`, action-message generation, message `kind` tagging.
- **Pure render mapping**: `computeBadgeState` covers the truth table (action × resultingStatus × isActionPosition × potResults presence). This is the bulk of the logic and is Phaser-free.
- **Phaser game objects**: smoke tests asserting `setVisible(true/false)` and text content after `applyState()` — verified manually in the browser for visual polish.
- **Messages panel**: snapshot/text test of the showdown row.

No e2e changes.

### 9. Animation specifics

All tweens use Phaser's built-in tween manager.

- Action-badge fade-in: alpha 0 → 1 over 150 ms (`Quad.easeOut`).
- Action-badge fade-out: alpha 1 → 0 over 200 ms after the 2500 ms hold.
- Winner-glow pulse: tween glow alpha 0.5 ↔ 0.7 over 1200 ms, `yoyo: true`, `repeat: -1`. Stop and reset on `hand-started`.
- All-in pulse: same shape as winner-glow, weaker amplitude (0.9 ↔ 1.0) on badge text alpha.

### 10. Trade-offs / risks

- **Badge anchor crowding**: with all-in + side pots, several seats can show "WINNER" badges simultaneously. The pods are spaced around an ellipse, so collisions only happen with neighboring winners. If neighbors both win, badges may visually touch. Acceptable for now; revisit if it actually looks bad.
- **Multi-pot single-winner aggregation**: chosen approach is to display total + hand description. Alternative: stack two badges per pot. The aggregated form is simpler and the multi-pot single-winner case is rare; revisit if feedback says it's confusing.
- **Long hand descriptions** (e.g. "Full House, Aces full of Kings"): may need truncation at narrow widths. The badge width is sized by the longer of line 1 / line 2; we'll clamp line 2 to ~28 chars with ellipsis. Full text remains in the message log.

## Future Work

- Animate pot chips flying to winners (Approach E from brainstorming).
- Tooltip / hover on truncated hand descriptions.
- User setting to suppress action badges for fast games.
- Sound effects (toggleable).
