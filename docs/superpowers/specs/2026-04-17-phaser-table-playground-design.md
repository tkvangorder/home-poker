# Phaser Table Playground — Design Spec

**Date:** 2026-04-17
**Status:** Design approved, pending implementation plan

## Context

The Angular poker client has two poker-table renderers:

- `src/app/game-lobby/css-poker-table/` — current, polished CSS/HTML renderer.
- `src/app/game-lobby/phaser-table/` — earlier Phaser experiment, inactive. It looks flat next to the CSS version because it uses only `Phaser.GameObjects.Graphics` primitives with flat fills — no gradients, no baked shadows, no textures, no typography polish.

We want to revisit Phaser as a table renderer. Before touching the Angular component, we need a fast design-iteration surface that lets us make visual decisions (colors, gradients, shadows, typography, sizing) without a full Angular rebuild cycle.

## Goal

Build a standalone, self-contained HTML playground (`phaser-table-playground.html` at the project root) that renders a mock poker table in Phaser and exposes live controls for every visual parameter. Use it to close the polish gap with the CSS version. Once a look is validated, translate the values by hand into the Angular `phaser-table` code.

## Non-goals

- Animations (card deal, chip-to-pot slide, bet pulses). Parity first; motion is a follow-on.
- WebGL shaders and particle effects.
- Sound.
- Real game-state wiring (no WebSocket, no REST).
- Responsive/mobile layout. Desktop viewport only.
- Named preset save/load. Single-session state; refresh resets defaults.
- Automated tests on the playground HTML — it's a disposable design tool.
- Automatic code generation to port values back to Angular.

## Visual direction

Parity first, then enhance. Step 1 is matching the polish of the CSS version in Phaser. Step 2 (a separate future effort) is adding Phaser-only capabilities (animations, textures, effects).

## File & structure

- **One file:** `phaser-table-playground.html` at the project root (alongside the existing `poker-table-playground.html`).
- **No build step.** Loads Phaser 3 from a CDN. No Angular, no TypeScript, no bundler.
- **Inline `<style>` and `<script>`.** Self-contained so it's easy to share or throw away.
- **State model:** a single plain-object `config` holds all tunable values. Controls mutate `config` via `addEventListener`. A small pub-sub emits a `configChanged` event; the Phaser scene listens and re-renders affected elements.

## Layout

Three-region desktop layout:

- **Left column — controls panel.** Fixed width ~240px. Scrolls independently. Contains collapsible groups (one per element: Felt, Seats, Cards, Chips/Pot, Typography, Mock state). A ◀ toggle collapses the panel to a thin edge tab to free up canvas room while evaluating polish.
- **Right main — Phaser canvas.** 2.4 : 1 aspect ratio (matches production). The felt ellipse fills ~72% of the canvas width so seat pods (which extend outside the ellipse by ~15%) have room on all four sides.
- **Bottom-right — config readout.** Monospace block showing the live `config` object as pretty-printed JSON. "Copy" button puts it on the clipboard for manual translation back into Angular code.

## Phaser rendering approach

Hybrid: procedural via `RenderTexture` for anything with tunable parameters, pre-baked image slots for fixed high-detail things.

| Element | Approach |
|---|---|
| Felt | Procedural `RenderTexture` drawn via canvas 2D radial-gradient. Redrawn when felt knobs change. |
| Rail | Procedural brown gradient by default; image slot so a wood-grain PNG can drop in later without changing the playground shape. |
| Card face | `Graphics` with white fill + rounded rect + a second offset `Graphics` behind for drop shadow. `Phaser.Text` for rank and suit. |
| Card back | Procedural rounded rect with inner border rect, mirroring the CSS diamond-weave pattern. Swappable to a PNG slot later. |
| Seat pod | `Graphics` container — rounded rect bg + border. Active glow is a feathered alpha-layered graphic behind the pod. |
| Avatar | `Phaser.Image` with a circular mask. Defaults to placeholder images (pravatar/dicebear) rotated across seats. Initial-letter fallback for empty slots. |
| Chip / bet-chip | `Graphics` circle + inner highlight. Optional PNG sprite slot. |
| Pot pill | `Graphics` pill bg + small procedural chip + `Text`. |
| Dealer button | Procedural small circle with "D" text. |

## Knobs (controls)

Grouped by element. All values live in the `config` object and are reflected in the JSON readout.

### Felt
- Base color
- Center highlight color & intensity
- Center highlight radius
- Rail width
- Rail inner-border color & alpha
- Outer glow / shadow

### Seats (player pill)
- Pod bg color & alpha
- Border color (idle / active)
- Active glow intensity
- Border radius
- Pod padding
- Avatar size
- Avatar ring width & color
- Dealer badge size
- Name font size
- Stack font size
- Text colors

### Cards
- Rank font family (Georgia / Inter / system)
- Rank font size
- Suit symbol size
- Face bg color
- Border color & width
- Corner radius
- Hole-card size
- Community-card size
- Card-back pattern color
- Card drop shadow

### Chips / Pot / Bet
- Pot pill bg alpha
- Pot accent color (gold default)
- Pot label / amount font size
- Bet-chip gradient colors
- Bet-chip font size

### Typography
- Global font family override (applies to pod, pot, bet)
- Base scale multiplier

### Mock state
- Seat count (1–9 — how many of the 9 positions are occupied, to exercise empty-chair rendering)
- Active player seat #
- Dealer seat #
- Street (preflop / flop / turn / river / showdown)
- Pot amount
- Per-seat current bet
- One-seat showdown toggle (reveal hole cards)

## Porting findings back to Angular

- The JSON in the config readout is copied by hand.
- Translation targets: the constants and style literals inside `src/app/game-lobby/phaser-table/game-objects/*.ts` (e.g., `ACTIVE_COLOR`, font sizes in `SeatDisplay`, gradient stops in `TableFelt`), and the layout ratios in `poker-table.scene.ts`.
- Translation requires judgment: the playground uses flat pixel numbers; the production code may want values tied to `scale.width` for responsiveness.
- The playground file stays in the repo root so it can be re-opened anytime to tune further.

## Risk / open questions

- **Canvas-2D gradients in `RenderTexture`:** need to confirm Phaser's `RenderTexture` accepts a canvas-drawn source cleanly on both WebGL and Canvas renderers. If awkward, fall back to layered `Graphics` ellipses approximating a gradient.
- **Font availability:** Georgia is system-available on macOS; "Inter" and similar web fonts will need a `<link>` to Google Fonts at the top of the playground.
- **Avatar CORS:** if pravatar/dicebear URLs hit CORS issues when loading as Phaser textures, we'll either proxy through a local file or ship a handful of inline data-URL placeholders.
