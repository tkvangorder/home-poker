# Pot-Winner Highlight — Design

**Date:** 2026-05-31
**Status:** Draft (pending review)
**Author:** Tyler Van Gorder (with Claude)

## Problem

At showdown the Phaser table announces the winner with a sticky gold badge on the
winning player's **pod** (built in the 2026-05-29 work). It tells you *who* won and
*what* they had, but it doesn't show *why* — the cards that made the hand — and a
pod-anchored message is easy to miss at the moment the pot is awarded. With side
pots and split pots, a single pod badge also can't tell the full story of who won
what.

## Goals

- Move the winner announcement from the pod to a prominent **center-of-table banner**.
- Make the winning hand legible: **highlight the 5 cards** that formed it and **glow
  the winning seat**.
- Tell the complete story when there are multiple pots / multiple winners, **one
  winning hand at a time**.
- Keep the table visually calm — no layout jumps, dim the non-winning cards for contrast.

## Non-Goals

- No betting/command-flow changes.
- No new dependencies.
- No work in the legacy CSS table (`css-poker-table/`), which is parked for deletion.
- No client-side hand evaluation — the winning cards come from the backend.

## Backend Contract (already shipped)

The `showdown-result` event's `Winner` now carries the concrete cards of the best
hand. Confirmed from the live spec at `http://localhost:8080/command-event-spec.md`:

| Field             | Type        | Description |
|-------------------|-------------|-------------|
| `seatPosition`    | int         | Winning player's 1-indexed seat |
| `userId`          | String      | Winning player's ID |
| `amount`          | int         | Chips awarded |
| `handDescription` | String      | e.g. "Full House, Aces over Kings" |
| `winningCards`    | List<Card>  | The concrete cards (value + suit) forming the winner's best five-card hand. **Empty when the pot was won without a showdown** (e.g. everyone else folded). |

`Card` is the existing client shape `{ value: CardValue; suit: CardSuit }`
(`poker/poker-models.ts`) — enum values `TWO`…`ACE` and `HEART`/`DIAMOND`/`CLUB`/`SPADE`.
Cards are matched to on-table sprites by `value` + `suit` equality.

`PotResult` is unchanged: `{ potIndex: number; potAmount: number; winners: Winner[] }`,
where `potIndex 0` is the main pot.

## Design

### Stage model — one winning hand at a time

The display is a **sequence of stages**, where each stage shows exactly one winning
hand. Stages are derived from `potResults`:

- **Pot order:** side pots first by `potAmount` **ascending**, then the **main pot
  (`potIndex 0`) last**.
- **Within a pot:** one stage **per winner**. A split pot (multiple winners on one
  pot) therefore becomes multiple stages — never two highlighted hands on screen at once.

Each stage carries: the winning seat position, the amount, the hand description, the
pot label (e.g. "Main Pot" / "Side Pot 1"), whether the pot is split, and the
winner's `winningCards`.

### Each stage renders

- **Center banner** on the upper felt, above the community cards so it never covers a
  highlighted card. Lines: winner name + amount, then hand description; pot label when
  there is more than one pot; "(split)" annotation when the pot was split.
- **Winning seat:** gold border + name rendered in gold.
- **Cards:** the stage's 5 `winningCards` glow + lift; **all other** community and
  hole-card sprites dim for contrast.

### Timing & lifecycle

- Each stage holds **~4s**, then advances to the next. Timing is **per stage**, so a
  showdown with N winning hands runs ≈ N × ~4s.
- After the final (main-pot) stage, the banner and all highlights **fade out** and the
  table rests in its normal post-hand state until the next hand.
- **The `hand-started` event is the hard terminator.** It can arrive at any point —
  mid-stage, between stages, or after the sequence has already faded. Whenever it
  arrives, the sequence is cut short immediately: any running stage timer is cancelled,
  the banner is removed, and all card/seat highlights reset, so the new hand renders
  clean. The per-stage timing above only governs pacing *within* the gap before the
  next hand; it never delays or competes with a new hand.

### Fold-win (no showdown)

When `winningCards` is empty (pot won by fold), the stage shows the **banner + seat
glow only**, with no card highlight. This is the correct presentation for that case,
not a degraded fallback. (Hole/board cards are typically not revealed in this case.)

## Units (all in `phaser-table/`)

New, with a tested pure-logic seam kept separate from Phaser rendering:

- **`utils/showdown-sequence.ts`** (pure, unit-tested) —
  `buildShowdownStages(potResults, nameLookup)` → ordered `ShowdownStage[]`
  (pot ordering, per-winner split expansion, labels, amounts, descriptions, cards).
- **`utils/card-match.ts`** (pure, unit-tested) — given a stage's `winningCards`, the
  community cards, and the winner's hole cards, resolve which sprites to highlight:
  `{ communityIndices: number[]; holeCardRefs: ... }`. Matches by `value` + `suit`.
- **`game-objects/winner-banner.ts`** — center banner game object: `show(...)`,
  `fadeOut()`, `destroy()`. Gold palette consistent with the existing winner styling.

Changed:

- **`game-objects/card-sprite.ts`** — add `setHighlight(on)` (gold glow + lift) and
  `setDimmed(on)`.
- **`game-objects/seat-display.ts`** — add `setWinnerHighlight(on)` (gold border + gold
  name). Remove nothing else here.
- **`scenes/poker-table.scene.ts`** — a small showdown presenter: consume
  `buildShowdownStages`, drive per-stage Phaser timers, apply highlight/dim/banner per
  stage, handle fade-out and abort-on-new-hand.
- **`game/game-events.ts`** — add `winningCards: Card[]` to the `Winner` interface.

Removed (supersedes the 2026-05-29 sticky-badge winner):

- `'winner'` kind and its palette/pulse in **`game-objects/action-badge.ts`**.
- `computeWinnerBadge` and winner branches in **`utils/action-badge-state.ts`**.
- Winner cases in **`utils/action-badge-state.spec.ts`**.

The name-swap action overlay (fold/check/call/bet/raise/all-in) is **unchanged**.

## Edge Cases

- **Split pot** (one pot, multiple winners): expands to one stage per winner; one hand
  highlighted at a time.
- **No side pots:** single main-pot stage, then fade.
- **Fold-win:** empty `winningCards` → banner + seat glow, no card highlight.
- **Unmatched card** (shouldn't occur under the contract): skipped defensively, no crash.
- **New hand at any time (`hand-started`):** immediate abort + full reset of banner,
  card highlights, and seat glow — regardless of which stage (if any) is showing.

## Testing

- **Jest unit tests** for `showdown-sequence` (pot ordering, main-pot-last, split
  expansion, labels/formatting, empty `winningCards`) and `card-match` (value+suit
  resolution across board + hole cards, unmatched-card handling), mirroring the
  existing `action-badge-state.spec.ts` pattern.
- Phaser rendering kept thin over the tested logic; **manual verification** in the
  running app (`npm start`) against a real showdown, a split pot, and a fold-win.
