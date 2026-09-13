# Phaser Table Playground Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone HTML playground (`phaser-table-playground.html`) that renders a mock poker table in Phaser with live controls for every visual parameter, enabling rapid design iteration without an Angular rebuild cycle.

**Architecture:** Single self-contained HTML file at the project root. Loads Phaser 3 from a CDN, no build step. A plain `config` object holds all tunable values; HTML controls mutate `config` via event listeners and call a `rerender()` function. A Phaser scene reads `config` to draw felt (procedural `RenderTexture`), seat pods, cards, chips, pot, and dealer button. Bottom-right panel shows the live `config` as JSON for manual porting back to Angular code.

**Tech Stack:** HTML/CSS/vanilla JS, Phaser 3 (via CDN). No TypeScript, no bundler, no tests.

---

## Reference Material

Engineers executing this plan should have these files open for reference — the playground mirrors their structure and aims to match the CSS version's polish:

- **CSS look to match:** `src/app/game-lobby/css-poker-table/css-poker-table.component.css` — the visual target (green radial-gradient felt, wooden rail, dark pods with gold-glow active state, white cards with colored rank/suit, gold pot pill).
- **CSS markup for structure:** `src/app/game-lobby/css-poker-table/css-poker-table.component.html`.
- **Seat angles:** `src/app/game-lobby/phaser-table/utils/seat-layout.ts` — we'll re-use its seat-angle table in the playground (ported to plain JS).
- **Prior Phaser attempt (for reference, not copied):** `src/app/game-lobby/phaser-table/game-objects/*.ts` — these are the files whose output we're trying to polish.
- **Design spec:** `docs/superpowers/specs/2026-04-17-phaser-table-playground-design.md`.

## Verification Approach

There are no automated tests. Each task ends with a visual check:

1. Open `phaser-table-playground.html` in a browser (Chrome recommended). First-time: `open phaser-table-playground.html` on macOS.
2. Open DevTools console to verify no errors.
3. Refresh after each edit.
4. Confirm the task's "Expected visual" description matches what you see.
5. Commit.

---

## Task 1: Scaffold — HTML shell, layout, Phaser canvas, empty controls panel

**Files:**
- Create: `phaser-table-playground.html`

- [ ] **Step 1: Create the scaffold file**

Create `/Users/tylervangorder/work/angular/angular-poker-client/phaser-table-playground.html`:

```html
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Phaser Table Playground</title>
<style>
  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
  :root {
    --bg: #1a1a1a;
    --panel: #242424;
    --border: #333;
    --text: #e0e0e0;
    --text-dim: #888;
    --accent: #4a9eff;
    --group-bg: #1e1e1e;
  }
  body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif;
    background: var(--bg);
    color: var(--text);
    height: 100vh;
    overflow: hidden;
  }
  .layout {
    display: grid;
    grid-template-columns: 240px 1fr;
    grid-template-rows: 1fr auto;
    height: 100vh;
  }
  .controls {
    grid-row: 1 / 3;
    background: var(--panel);
    border-right: 1px solid var(--border);
    overflow-y: auto;
    padding: 12px;
  }
  .controls h1 {
    font-size: 13px;
    font-weight: 700;
    color: #fff;
    margin-bottom: 10px;
  }
  .group {
    background: var(--group-bg);
    border: 1px solid var(--border);
    border-radius: 6px;
    padding: 10px;
    margin-bottom: 8px;
  }
  .group-title {
    font-size: 10px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.8px;
    color: var(--text-dim);
    cursor: pointer;
    user-select: none;
    margin-bottom: 8px;
  }
  .group.collapsed .group-body { display: none; }
  .control { margin-bottom: 8px; }
  .control:last-child { margin-bottom: 0; }
  .control label {
    display: flex; justify-content: space-between; align-items: center;
    font-size: 11px; color: #aaa; margin-bottom: 3px;
  }
  .control label .val { color: var(--accent); font-family: monospace; }
  .control input[type="range"] { width: 100%; }
  .control input[type="color"] { width: 100%; height: 22px; border: none; padding: 0; background: transparent; }
  .control select, .control input[type="number"] {
    width: 100%; background: #2a2a2a; color: #fff; border: 1px solid #444;
    border-radius: 3px; padding: 3px 6px; font-size: 11px;
  }
  .stage {
    background: #0f0f0f;
    display: flex; align-items: center; justify-content: center;
    padding: 20px;
  }
  #game-canvas {
    width: 100%;
    aspect-ratio: 2.4 / 1;
    background: #0a0a0a;
  }
  .readout {
    grid-column: 2;
    background: #1a1a1a;
    border-top: 1px solid var(--border);
    padding: 8px 16px;
    font-family: monospace;
    font-size: 10px;
    color: #8fbc8f;
    max-height: 110px;
    overflow: auto;
    display: flex; gap: 10px; align-items: flex-start;
  }
  .readout pre { flex: 1; white-space: pre-wrap; word-break: break-all; }
  .readout button {
    background: #2a2a2a; color: #fff; border: 1px solid #444;
    border-radius: 3px; padding: 3px 8px; font-size: 11px; cursor: pointer;
  }
</style>
</head>
<body>
<div class="layout">
  <div class="controls">
    <h1>Phaser Table Playground</h1>
    <!-- control groups will be added here -->
  </div>
  <div class="stage">
    <div id="game-canvas"></div>
  </div>
  <div class="readout">
    <pre id="config-json">// config will render here</pre>
    <button id="copy-btn">Copy</button>
  </div>
</div>

<script src="https://cdn.jsdelivr.net/npm/phaser@3.80.1/dist/phaser.min.js"></script>
<script>
  // ===== Config =====
  const config = {};

  // ===== Phaser scene stub =====
  class TableScene extends Phaser.Scene {
    constructor() { super('TableScene'); }
    create() {
      // nothing yet
    }
  }

  // ===== Boot =====
  const canvasEl = document.getElementById('game-canvas');
  function getCanvasSize() {
    const rect = canvasEl.getBoundingClientRect();
    return { width: Math.round(rect.width), height: Math.round(rect.height) };
  }
  const size = getCanvasSize();
  const game = new Phaser.Game({
    type: Phaser.AUTO,
    parent: canvasEl,
    transparent: true,
    width: size.width,
    height: size.height,
    scale: { mode: Phaser.Scale.RESIZE },
    scene: [TableScene],
    banner: false,
    audio: { noAudio: true },
  });

  // ===== Rerender helper =====
  function rerender() {
    // will be wired per-element in later tasks
    document.getElementById('config-json').textContent = JSON.stringify(config, null, 2);
  }

  // ===== Copy button =====
  document.getElementById('copy-btn').addEventListener('click', () => {
    navigator.clipboard.writeText(JSON.stringify(config, null, 2));
  });

  rerender();
</script>
</body>
</html>
```

- [ ] **Step 2: Open in browser and verify**

Run on macOS: `open /Users/tylervangorder/work/angular/angular-poker-client/phaser-table-playground.html`

Expected visual:
- Dark grey sidebar on left (240px wide) with "Phaser Table Playground" heading, otherwise empty.
- Dark canvas area on right (2.4:1 aspect).
- Bottom-right shows `{}` in green monospace and a "Copy" button.
- No console errors.

- [ ] **Step 3: Commit**

```bash
git add phaser-table-playground.html
git commit -m "feat: scaffold phaser-table playground shell"
```

---

## Task 2: Felt rendering — procedural `RenderTexture`

**Files:**
- Modify: `phaser-table-playground.html`

- [ ] **Step 1: Add `config.felt` and the `drawFelt` function**

In the `<script>` block, replace the `// ===== Config =====` and `// ===== Phaser scene stub =====` sections with:

```javascript
  // ===== Config =====
  const config = {
    felt: {
      baseColor: '#145a30',
      centerHighlight: '#1a6b3c',
      centerAlpha: 0.9,
      centerRadius: 0.4,        // as fraction of felt rx
      railColor: '#3a2a14',
      railInnerColor: '#1c1008',
      railWidth: 14,
      innerLineColor: '#daa520',
      innerLineAlpha: 0.25,
      shadowAlpha: 0.5,
    },
  };

  // ===== Layout helpers =====
  function layout(width, height) {
    const cx = width / 2;
    const cy = height / 2;
    // Felt takes ~72% of canvas width, leaving room for pods on all sides
    const rx = width * 0.36;
    const ry = rx / 2.4;  // 2.4 : 1 aspect
    return { cx, cy, rx, ry };
  }

  // ===== Scene =====
  class TableScene extends Phaser.Scene {
    constructor() { super('TableScene'); }

    create() {
      this.feltTexture = null;
      this.feltImage = null;
      this.drawFelt();
      this.scale.on('resize', () => this.drawFelt());
    }

    drawFelt() {
      const { width, height } = this.scale;
      const { cx, cy, rx, ry } = layout(width, height);
      const f = config.felt;

      // Build an offscreen canvas 2D surface for the gradient
      const pad = f.railWidth + 10;
      const w = Math.round(rx * 2 + pad * 2);
      const h = Math.round(ry * 2 + pad * 2);
      const off = document.createElement('canvas');
      off.width = w; off.height = h;
      const ctx = off.getContext('2d');

      // Drop shadow (soft outer glow)
      ctx.save();
      ctx.shadowColor = `rgba(0,0,0,${f.shadowAlpha})`;
      ctx.shadowBlur = 40;
      ctx.shadowOffsetY = 8;
      ctx.fillStyle = f.baseColor;
      ctx.beginPath();
      ctx.ellipse(w / 2, h / 2, rx, ry, 0, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();

      // Felt gradient
      const grad = ctx.createRadialGradient(
        w / 2 - rx * 0.1, h / 2 - ry * 0.1, 0,
        w / 2, h / 2, Math.max(rx, ry)
      );
      grad.addColorStop(0, hexWithAlpha(f.centerHighlight, f.centerAlpha));
      grad.addColorStop(f.centerRadius, f.baseColor);
      grad.addColorStop(1, darken(f.baseColor, 0.4));
      ctx.save();
      ctx.beginPath();
      ctx.ellipse(w / 2, h / 2, rx, ry, 0, 0, Math.PI * 2);
      ctx.clip();
      ctx.fillStyle = grad;
      ctx.fillRect(0, 0, w, h);
      ctx.restore();

      // Rail ring
      ctx.save();
      ctx.lineWidth = f.railWidth;
      ctx.strokeStyle = f.railColor;
      ctx.beginPath();
      ctx.ellipse(w / 2, h / 2, rx + f.railWidth / 2, ry + f.railWidth / 2, 0, 0, Math.PI * 2);
      ctx.stroke();
      // Rail inner dark border
      ctx.lineWidth = 2;
      ctx.strokeStyle = f.railInnerColor;
      ctx.beginPath();
      ctx.ellipse(w / 2, h / 2, rx, ry, 0, 0, Math.PI * 2);
      ctx.stroke();
      ctx.restore();

      // Gold inner line
      ctx.save();
      ctx.lineWidth = 1;
      ctx.strokeStyle = hexWithAlpha(f.innerLineColor, f.innerLineAlpha);
      ctx.beginPath();
      ctx.ellipse(w / 2, h / 2, rx - 10, ry - 10, 0, 0, Math.PI * 2);
      ctx.stroke();
      ctx.restore();

      // Upload as a Phaser texture
      if (this.textures.exists('felt')) this.textures.remove('felt');
      this.textures.addCanvas('felt', off);

      if (this.feltImage) this.feltImage.destroy();
      this.feltImage = this.add.image(cx, cy, 'felt').setDepth(0);
    }
  }

  // ===== Color helpers =====
  function hexWithAlpha(hex, a) {
    const { r, g, b } = hexToRgb(hex);
    return `rgba(${r},${g},${b},${a})`;
  }
  function hexToRgb(hex) {
    const h = hex.replace('#', '');
    return {
      r: parseInt(h.substring(0, 2), 16),
      g: parseInt(h.substring(2, 4), 16),
      b: parseInt(h.substring(4, 6), 16),
    };
  }
  function darken(hex, amount) {
    const { r, g, b } = hexToRgb(hex);
    const m = 1 - amount;
    return `rgb(${Math.round(r * m)},${Math.round(g * m)},${Math.round(b * m)})`;
  }
```

Also update the `rerender()` function:

```javascript
  function rerender() {
    const scene = game.scene.getScene('TableScene');
    if (scene && scene.drawFelt) scene.drawFelt();
    document.getElementById('config-json').textContent = JSON.stringify(config, null, 2);
  }
```

- [ ] **Step 2: Refresh browser and verify**

Expected visual:
- A dark-green oval felt occupies the center of the canvas with a brown rail ring around it and a faint gold inner line.
- The felt has a subtle radial gradient (slightly brighter near the top-left of center).
- The JSON readout now shows the `felt` object.
- No console errors.

- [ ] **Step 3: Commit**

```bash
git add phaser-table-playground.html
git commit -m "feat: render procedural felt with radial gradient"
```

---

## Task 3: Felt control group — wire live sliders and color pickers

**Files:**
- Modify: `phaser-table-playground.html`

- [ ] **Step 1: Add the Felt control group**

Inside `<div class="controls">`, replace the comment `<!-- control groups will be added here -->` with:

```html
<div class="group" data-group="felt">
  <div class="group-title">▾ Felt</div>
  <div class="group-body">
    <div class="control">
      <label>Base color <span class="val" data-val="felt.baseColor"></span></label>
      <input type="color" data-path="felt.baseColor">
    </div>
    <div class="control">
      <label>Center highlight <span class="val" data-val="felt.centerHighlight"></span></label>
      <input type="color" data-path="felt.centerHighlight">
    </div>
    <div class="control">
      <label>Center alpha <span class="val" data-val="felt.centerAlpha"></span></label>
      <input type="range" min="0" max="1" step="0.05" data-path="felt.centerAlpha">
    </div>
    <div class="control">
      <label>Center radius <span class="val" data-val="felt.centerRadius"></span></label>
      <input type="range" min="0.1" max="1" step="0.05" data-path="felt.centerRadius">
    </div>
    <div class="control">
      <label>Rail color <span class="val" data-val="felt.railColor"></span></label>
      <input type="color" data-path="felt.railColor">
    </div>
    <div class="control">
      <label>Rail inner color <span class="val" data-val="felt.railInnerColor"></span></label>
      <input type="color" data-path="felt.railInnerColor">
    </div>
    <div class="control">
      <label>Rail width <span class="val" data-val="felt.railWidth"></span></label>
      <input type="range" min="0" max="40" step="1" data-path="felt.railWidth">
    </div>
    <div class="control">
      <label>Inner line color <span class="val" data-val="felt.innerLineColor"></span></label>
      <input type="color" data-path="felt.innerLineColor">
    </div>
    <div class="control">
      <label>Inner line alpha <span class="val" data-val="felt.innerLineAlpha"></span></label>
      <input type="range" min="0" max="1" step="0.05" data-path="felt.innerLineAlpha">
    </div>
    <div class="control">
      <label>Shadow alpha <span class="val" data-val="felt.shadowAlpha"></span></label>
      <input type="range" min="0" max="1" step="0.05" data-path="felt.shadowAlpha">
    </div>
  </div>
</div>
```

- [ ] **Step 2: Wire controls to `config` at the bottom of the script block**

Add these helpers and the wire-up, just before the final `rerender();`:

```javascript
  // ===== Config path helpers =====
  function getByPath(obj, path) {
    return path.split('.').reduce((o, k) => (o ? o[k] : undefined), obj);
  }
  function setByPath(obj, path, value) {
    const keys = path.split('.');
    let o = obj;
    for (let i = 0; i < keys.length - 1; i++) o = o[keys[i]];
    o[keys[keys.length - 1]] = value;
  }

  // ===== Bind controls to config =====
  function bindControls() {
    document.querySelectorAll('[data-path]').forEach(input => {
      const path = input.dataset.path;
      const current = getByPath(config, path);
      if (input.type === 'range' || input.type === 'number') {
        input.value = current;
      } else if (input.type === 'color') {
        input.value = current;
      } else if (input.tagName === 'SELECT') {
        input.value = current;
      } else if (input.type === 'checkbox') {
        input.checked = !!current;
      } else {
        input.value = current;
      }
      input.addEventListener('input', () => {
        let v = input.value;
        if (input.type === 'range' || input.type === 'number') v = parseFloat(v);
        if (input.type === 'checkbox') v = input.checked;
        setByPath(config, path, v);
        rerender();
      });
    });
    document.querySelectorAll('[data-val]').forEach(span => {
      // updated by rerender()
    });
    // Group collapse toggles
    document.querySelectorAll('.group-title').forEach(t => {
      t.addEventListener('click', () => {
        const g = t.closest('.group');
        g.classList.toggle('collapsed');
        t.textContent = (g.classList.contains('collapsed') ? '▸ ' : '▾ ') + t.textContent.slice(2);
      });
    });
  }

  function updateValueReadouts() {
    document.querySelectorAll('[data-val]').forEach(span => {
      const v = getByPath(config, span.dataset.val);
      span.textContent = typeof v === 'number' ? (Number.isInteger(v) ? v : v.toFixed(2)) : v;
    });
  }
```

Update `rerender()` to also call `updateValueReadouts()`:

```javascript
  function rerender() {
    const scene = game.scene.getScene('TableScene');
    if (scene && scene.drawFelt) scene.drawFelt();
    updateValueReadouts();
    document.getElementById('config-json').textContent = JSON.stringify(config, null, 2);
  }
```

Call `bindControls()` right before the final `rerender();`:

```javascript
  bindControls();
  rerender();
```

- [ ] **Step 3: Refresh browser and verify**

Expected visual:
- Felt group in the sidebar shows 10 controls (color pickers and sliders) with live values on each label.
- Dragging the "Rail width" slider makes the brown rail ring visibly thicker/thinner.
- Changing "Base color" to bright red turns the felt red.
- Clicking the "▾ Felt" title collapses the group body; clicking again expands it.
- JSON readout updates live as controls move.

- [ ] **Step 4: Commit**

```bash
git add phaser-table-playground.html
git commit -m "feat: wire felt controls with live rerender"
```

---

## Task 4: Mock state object + community cards + pot display + mock-state controls

**Files:**
- Modify: `phaser-table-playground.html`

- [ ] **Step 1: Add `config.state`, `config.cards` (minimal), and `config.pot`**

Inside the `config` object, add:

```javascript
    state: {
      seatCount: 6,            // number of occupied seats, 1..9
      activeSeat: 3,           // 1..9, or 0 for none
      dealerSeat: 1,
      street: 'flop',          // preflop | flop | turn | river | showdown
      potAmount: 1200,         // in cents
      showdownSeat: 0,         // 0 = none, else seat # that reveals hole cards
    },
    cards: {
      faceColor: '#ffffff',
      borderColor: '#000000',
      borderAlpha: 0.12,
      cornerRadius: 5,
      rankFont: 'Georgia, serif',
      rankSize: 22,
      suitSize: 32,
      communityWidth: 72,
      holeWidth: 44,
      backColor1: '#1a5c8a',
      backColor2: '#0e3d5e',
      shadowAlpha: 0.4,
    },
    pot: {
      pillBgAlpha: 0.5,
      accentColor: '#f5d678',
      labelSize: 9,
      amountSize: 15,
      chipSize: 18,
    },
```

- [ ] **Step 2: Add community-card and pot rendering to the scene**

Add these methods to the `TableScene` class (after `drawFelt`):

```javascript
    drawCommunityCards() {
      const { width, height } = this.scale;
      const { cx, cy } = layout(width, height);
      const c = config.cards;
      const s = config.state;
      const cardCount = {
        preflop: 0, flop: 3, turn: 4, river: 5, showdown: 5,
      }[s.street] || 0;

      if (this.communityContainer) this.communityContainer.destroy();
      this.communityContainer = this.add.container(cx, cy - c.communityWidth * 0.15).setDepth(2);

      const rankLabels = ['A', 'K', 'Q', 'J', '10'];
      const suits = ['heart', 'spade', 'diamond', 'club', 'spade'];
      const w = c.communityWidth;
      const h = Math.round(w * 1.4);
      const gap = 8;
      const totalW = 5 * w + 4 * gap;
      const startX = -totalW / 2 + w / 2;

      for (let i = 0; i < 5; i++) {
        const x = startX + i * (w + gap);
        if (i < cardCount) {
          const card = drawCardFace(this, x, 0, w, h, rankLabels[i], suits[i]);
          this.communityContainer.add(card);
        } else {
          const placeholder = this.add.graphics();
          placeholder.lineStyle(1.5, 0xffffff, 0.12);
          placeholder.strokeRoundedRect(x - w / 2, -h / 2, w, h, c.cornerRadius);
          this.communityContainer.add(placeholder);
        }
      }
    }

    drawPot() {
      const { width, height } = this.scale;
      const { cx, cy } = layout(width, height);
      const p = config.pot;
      const c = config.cards;
      const s = config.state;

      if (this.potContainer) this.potContainer.destroy();
      if (s.potAmount <= 0) return;

      const y = cy + c.communityWidth * 0.85;
      this.potContainer = this.add.container(cx, y).setDepth(2);

      const chipSize = p.chipSize;
      const pad = 10;
      const labelText = 'POT';
      const amountText = '$' + (s.potAmount / 100).toFixed(2);
      const label = this.add.text(0, 0, labelText, {
        fontFamily: 'system-ui, sans-serif', fontSize: p.labelSize + 'px',
        color: 'rgba(255,255,255,0.5)', fontStyle: 'bold',
      }).setOrigin(0, 0.5);
      const amount = this.add.text(0, 0, amountText, {
        fontFamily: 'system-ui, sans-serif', fontSize: p.amountSize + 'px',
        color: p.accentColor, fontStyle: 'bold',
      }).setOrigin(0, 0.5);

      const textW = Math.max(label.width, amount.width);
      const pillW = chipSize + pad + textW + pad * 2;
      const pillH = Math.max(chipSize + 8, p.amountSize + p.labelSize + 4);

      const bg = this.add.graphics();
      bg.fillStyle(0x000000, p.pillBgAlpha);
      bg.fillRoundedRect(-pillW / 2, -pillH / 2, pillW, pillH, pillH / 2);
      bg.lineStyle(1, parseInt(p.accentColor.replace('#', ''), 16), 0.25);
      bg.strokeRoundedRect(-pillW / 2, -pillH / 2, pillW, pillH, pillH / 2);

      const chip = this.add.graphics();
      const chipX = -pillW / 2 + pad + chipSize / 2;
      chip.fillStyle(parseInt(p.accentColor.replace('#', ''), 16), 1);
      chip.fillCircle(chipX, 0, chipSize / 2);
      chip.lineStyle(2, 0xc49a38, 1);
      chip.strokeCircle(chipX, 0, chipSize / 2);

      label.setPosition(chipX + chipSize / 2 + pad, -p.amountSize / 2 + 1);
      amount.setPosition(chipX + chipSize / 2 + pad, p.labelSize / 2 + 2);

      this.potContainer.add([bg, chip, label, amount]);
    }
```

Also update `create()` and add a top-level helper `drawCardFace`:

```javascript
    create() {
      this.feltImage = null;
      this.drawAll();
      this.scale.on('resize', () => this.drawAll());
    }

    drawAll() {
      this.drawFelt();
      this.drawCommunityCards();
      this.drawPot();
    }
```

Add this top-level helper after the `TableScene` class:

```javascript
  // ===== Card drawing helpers =====
  const SUIT_SYMBOLS = { heart: '♥', diamond: '♦', club: '♣', spade: '♠' };
  const SUIT_COLORS = { heart: '#dc2626', diamond: '#2563eb', club: '#15803d', spade: '#1e293b' };

  function drawCardFace(scene, x, y, w, h, rank, suit) {
    const c = config.cards;
    const container = scene.add.container(x, y);
    // Shadow
    const shadow = scene.add.graphics();
    shadow.fillStyle(0x000000, c.shadowAlpha);
    shadow.fillRoundedRect(-w / 2 + 1, -h / 2 + 3, w, h, c.cornerRadius);
    // Face
    const bg = scene.add.graphics();
    bg.fillStyle(parseInt(c.faceColor.replace('#', ''), 16), 1);
    bg.fillRoundedRect(-w / 2, -h / 2, w, h, c.cornerRadius);
    bg.lineStyle(1, parseInt(c.borderColor.replace('#', ''), 16), c.borderAlpha);
    bg.strokeRoundedRect(-w / 2, -h / 2, w, h, c.cornerRadius);
    // Rank
    const rankTxt = scene.add.text(0, -h * 0.12, rank, {
      fontFamily: c.rankFont, fontSize: c.rankSize + 'px',
      color: SUIT_COLORS[suit], fontStyle: 'bold',
    }).setOrigin(0.5);
    // Suit
    const suitTxt = scene.add.text(0, h * 0.18, SUIT_SYMBOLS[suit], {
      fontFamily: c.rankFont, fontSize: c.suitSize + 'px',
      color: SUIT_COLORS[suit],
    }).setOrigin(0.5);
    container.add([shadow, bg, rankTxt, suitTxt]);
    return container;
  }
```

Update `rerender()`:

```javascript
  function rerender() {
    const scene = game.scene.getScene('TableScene');
    if (scene && scene.drawAll) scene.drawAll();
    updateValueReadouts();
    document.getElementById('config-json').textContent = JSON.stringify(config, null, 2);
  }
```

- [ ] **Step 3: Add the Mock-state control group**

Inside `<div class="controls">`, after the Felt group, add:

```html
<div class="group" data-group="state">
  <div class="group-title">▾ Mock state</div>
  <div class="group-body">
    <div class="control">
      <label>Seat count <span class="val" data-val="state.seatCount"></span></label>
      <input type="range" min="1" max="9" step="1" data-path="state.seatCount">
    </div>
    <div class="control">
      <label>Active seat # <span class="val" data-val="state.activeSeat"></span></label>
      <input type="range" min="0" max="9" step="1" data-path="state.activeSeat">
    </div>
    <div class="control">
      <label>Dealer seat # <span class="val" data-val="state.dealerSeat"></span></label>
      <input type="range" min="0" max="9" step="1" data-path="state.dealerSeat">
    </div>
    <div class="control">
      <label>Street</label>
      <select data-path="state.street">
        <option value="preflop">Preflop</option>
        <option value="flop">Flop</option>
        <option value="turn">Turn</option>
        <option value="river">River</option>
        <option value="showdown">Showdown</option>
      </select>
    </div>
    <div class="control">
      <label>Pot amount (cents) <span class="val" data-val="state.potAmount"></span></label>
      <input type="range" min="0" max="100000" step="50" data-path="state.potAmount">
    </div>
    <div class="control">
      <label>Showdown seat # <span class="val" data-val="state.showdownSeat"></span></label>
      <input type="range" min="0" max="9" step="1" data-path="state.showdownSeat">
    </div>
  </div>
</div>
```

- [ ] **Step 4: Refresh browser and verify**

Expected visual:
- Three community cards (A♥, K♠, Q♦) centered on the felt, styled with white faces and colored rank/suit.
- Two placeholder outlines to the right of the three cards.
- A gold-accented pot pill below the cards showing `POT $12.00`.
- Changing "Street" to "turn" reveals a fourth card; "river" or "showdown" reveals all five.
- Changing "Pot amount (cents)" updates the pot pill value live; setting to 0 hides the pot.
- No console errors.

- [ ] **Step 5: Commit**

```bash
git add phaser-table-playground.html
git commit -m "feat: render community cards and pot with mock-state controls"
```

---

## Task 5: Seat layout + pod rendering (placeholder avatar, no active glow yet)

**Files:**
- Modify: `phaser-table-playground.html`

- [ ] **Step 1: Add `config.seats` and seat layout helpers**

In `config`, add:

```javascript
    seats: {
      podBg: '#0f0a05',
      podBgAlpha: 0.8,
      podBorderIdle: '#ffffff',
      podBorderIdleAlpha: 0.08,
      podBorderActive: '#f5d678',
      activeGlowIntensity: 0.25,
      podRadius: 12,
      podPadX: 10,
      podPadY: 6,
      avatarSize: 36,
      avatarRingWidth: 2,
      avatarRingColor: '#daa520',
      avatarRingAlpha: 0.3,
      dealerBadgeSize: 16,
      nameSize: 11,
      stackSize: 10,
      nameColor: '#ffffff',
      stackColor: '#ffffff8c',
    },
```

Add this top-level constant and helper (ported from `seat-layout.ts`):

```javascript
  // ===== Seat layout (ported from utils/seat-layout.ts) =====
  const SEAT_ANGLES_DEG = [90, 135, 170, 190, 225, 270, 315, 350, 10];
  const MAX_SEATS = 9;
  function getSeatPositions(cx, cy, rx, ry) {
    return SEAT_ANGLES_DEG.map(deg => {
      const rad = (deg * Math.PI) / 180;
      return { x: cx + rx * Math.cos(rad), y: cy + ry * Math.sin(rad) };
    });
  }

  // ===== Mock data =====
  const MOCK_PLAYERS = [
    { name: 'You',    chips: 24000 },
    { name: 'Sam',    chips: 18000 },
    { name: 'Ari',    chips: 30000 },
    { name: 'Jay',    chips: 22000 },
    { name: 'Kim',    chips: 42000 },
    { name: 'Lee',    chips: 51000 },
    { name: 'Max',    chips: 19000 },
    { name: 'Nia',    chips: 27000 },
    { name: 'Pat',    chips: 12000 },
  ];
```

- [ ] **Step 2: Add pod rendering to the scene**

Add this method to `TableScene` and call it from `drawAll()`:

```javascript
    drawSeats() {
      const { width, height } = this.scale;
      const { cx, cy, rx, ry } = layout(width, height);
      const seatRx = rx * 1.22;
      const seatRy = ry * 1.35;
      const positions = getSeatPositions(cx, cy, seatRx, seatRy);
      const s = config.seats;
      const state = config.state;

      if (this.seatContainers) this.seatContainers.forEach(c => c.destroy());
      this.seatContainers = [];

      for (let i = 0; i < MAX_SEATS; i++) {
        if (i >= state.seatCount) continue;
        const pos = positions[i];
        const player = MOCK_PLAYERS[i];
        const container = this.add.container(pos.x, pos.y).setDepth(5);
        const isActive = (i + 1) === state.activeSeat;

        // Measure text first so pod can size around it
        const name = this.add.text(0, 0, player.name, {
          fontFamily: 'system-ui, sans-serif', fontSize: s.nameSize + 'px',
          color: s.nameColor, fontStyle: 'bold',
        }).setOrigin(0, 0.5);
        const stack = this.add.text(0, 0, '$' + (player.chips / 100).toFixed(2), {
          fontFamily: 'system-ui, sans-serif', fontSize: s.stackSize + 'px',
          color: s.stackColor,
        }).setOrigin(0, 0.5);
        const textW = Math.max(name.width, stack.width);

        const podW = s.avatarSize + s.podPadX + textW + s.podPadX * 2;
        const podH = Math.max(s.avatarSize + s.podPadY * 2, s.nameSize + s.stackSize + 10);

        // Background
        const bg = this.add.graphics();
        bg.fillStyle(parseInt(s.podBg.replace('#', ''), 16), s.podBgAlpha);
        bg.fillRoundedRect(-podW / 2, -podH / 2, podW, podH, s.podRadius);
        const borderColor = isActive
          ? parseInt(s.podBorderActive.replace('#', ''), 16)
          : parseInt(s.podBorderIdle.replace('#', ''), 16);
        const borderAlpha = isActive ? 1 : s.podBorderIdleAlpha;
        bg.lineStyle(1, borderColor, borderAlpha);
        bg.strokeRoundedRect(-podW / 2, -podH / 2, podW, podH, s.podRadius);

        // Avatar placeholder (circle with initial)
        const avX = -podW / 2 + s.podPadY + s.avatarSize / 2;
        const av = this.add.graphics();
        av.fillStyle(0x5a4428, 1);
        av.fillCircle(avX, 0, s.avatarSize / 2);
        av.lineStyle(
          s.avatarRingWidth,
          parseInt(s.avatarRingColor.replace('#', ''), 16),
          s.avatarRingAlpha
        );
        av.strokeCircle(avX, 0, s.avatarSize / 2);
        const initial = this.add.text(avX, 0, player.name[0], {
          fontFamily: 'system-ui, sans-serif', fontSize: Math.round(s.avatarSize * 0.4) + 'px',
          color: '#ffffff', fontStyle: 'bold',
        }).setOrigin(0.5);

        // Position name & stack
        const textX = avX + s.avatarSize / 2 + s.podPadX;
        name.setPosition(textX, -s.stackSize / 2 - 1);
        stack.setPosition(textX, s.nameSize / 2 + 1);

        container.add([bg, av, initial, name, stack]);
        this.seatContainers.push(container);
      }
    }
```

Update `drawAll()`:

```javascript
    drawAll() {
      this.drawFelt();
      this.drawSeats();
      this.drawCommunityCards();
      this.drawPot();
    }
```

- [ ] **Step 3: Add the Seats control group**

Inside `<div class="controls">`, after the Felt group and before Mock state, add:

```html
<div class="group" data-group="seats">
  <div class="group-title">▾ Seats</div>
  <div class="group-body">
    <div class="control">
      <label>Pod bg <span class="val" data-val="seats.podBg"></span></label>
      <input type="color" data-path="seats.podBg">
    </div>
    <div class="control">
      <label>Pod bg alpha <span class="val" data-val="seats.podBgAlpha"></span></label>
      <input type="range" min="0" max="1" step="0.05" data-path="seats.podBgAlpha">
    </div>
    <div class="control">
      <label>Border idle <span class="val" data-val="seats.podBorderIdle"></span></label>
      <input type="color" data-path="seats.podBorderIdle">
    </div>
    <div class="control">
      <label>Border idle alpha <span class="val" data-val="seats.podBorderIdleAlpha"></span></label>
      <input type="range" min="0" max="1" step="0.05" data-path="seats.podBorderIdleAlpha">
    </div>
    <div class="control">
      <label>Border active <span class="val" data-val="seats.podBorderActive"></span></label>
      <input type="color" data-path="seats.podBorderActive">
    </div>
    <div class="control">
      <label>Active glow intensity <span class="val" data-val="seats.activeGlowIntensity"></span></label>
      <input type="range" min="0" max="1" step="0.05" data-path="seats.activeGlowIntensity">
    </div>
    <div class="control">
      <label>Pod radius <span class="val" data-val="seats.podRadius"></span></label>
      <input type="range" min="0" max="30" step="1" data-path="seats.podRadius">
    </div>
    <div class="control">
      <label>Pod pad X <span class="val" data-val="seats.podPadX"></span></label>
      <input type="range" min="0" max="30" step="1" data-path="seats.podPadX">
    </div>
    <div class="control">
      <label>Pod pad Y <span class="val" data-val="seats.podPadY"></span></label>
      <input type="range" min="0" max="20" step="1" data-path="seats.podPadY">
    </div>
    <div class="control">
      <label>Avatar size <span class="val" data-val="seats.avatarSize"></span></label>
      <input type="range" min="16" max="64" step="1" data-path="seats.avatarSize">
    </div>
    <div class="control">
      <label>Avatar ring width <span class="val" data-val="seats.avatarRingWidth"></span></label>
      <input type="range" min="0" max="6" step="0.5" data-path="seats.avatarRingWidth">
    </div>
    <div class="control">
      <label>Avatar ring color <span class="val" data-val="seats.avatarRingColor"></span></label>
      <input type="color" data-path="seats.avatarRingColor">
    </div>
    <div class="control">
      <label>Avatar ring alpha <span class="val" data-val="seats.avatarRingAlpha"></span></label>
      <input type="range" min="0" max="1" step="0.05" data-path="seats.avatarRingAlpha">
    </div>
    <div class="control">
      <label>Name size <span class="val" data-val="seats.nameSize"></span></label>
      <input type="range" min="8" max="18" step="1" data-path="seats.nameSize">
    </div>
    <div class="control">
      <label>Stack size <span class="val" data-val="seats.stackSize"></span></label>
      <input type="range" min="8" max="18" step="1" data-path="seats.stackSize">
    </div>
    <div class="control">
      <label>Name color <span class="val" data-val="seats.nameColor"></span></label>
      <input type="color" data-path="seats.nameColor">
    </div>
  </div>
</div>
```

- [ ] **Step 4: Refresh browser and verify**

Expected visual:
- 6 seat pods arranged around the outside of the felt ellipse (bottom-center, bottom-left, left-lower, left-upper, top-left, top-center based on seat angles).
- Each pod: brown avatar circle with initial letter on left, player name and stack to the right.
- The 3rd seat (Ari) has a gold border (active).
- Reducing "Seat count" to 3 hides the later pods; raising to 9 shows them all.
- Changing pod border colors updates live.

- [ ] **Step 5: Commit**

```bash
git add phaser-table-playground.html
git commit -m "feat: render seat pods with live seats controls"
```

---

## Task 6: Active-seat glow + dealer button

**Files:**
- Modify: `phaser-table-playground.html`

- [ ] **Step 1: Add glow layer behind active pod**

In `drawSeats()`, just before the background graphics for each seat, add the glow-under layer. Replace the existing `// Background` block with:

```javascript
        // Glow (behind pod) for active seat
        if (isActive) {
          const glow = this.add.graphics();
          const glowPad = 8;
          for (let g = 0; g < 4; g++) {
            const a = s.activeGlowIntensity * (1 - g / 4) * 0.5;
            glow.fillStyle(parseInt(s.podBorderActive.replace('#', ''), 16), a);
            glow.fillRoundedRect(
              -podW / 2 - glowPad * (g + 1),
              -podH / 2 - glowPad * (g + 1),
              podW + glowPad * 2 * (g + 1),
              podH + glowPad * 2 * (g + 1),
              s.podRadius + glowPad * (g + 1)
            );
          }
          container.add(glow);
        }

        // Background
        const bg = this.add.graphics();
```

- [ ] **Step 2: Add `drawDealerButton()` and call it from `drawAll()`**

Add to `TableScene`:

```javascript
    drawDealerButton() {
      const { width, height } = this.scale;
      const { cx, cy, rx, ry } = layout(width, height);
      const seatRx = rx * 1.22;
      const seatRy = ry * 1.35;
      const positions = getSeatPositions(cx, cy, seatRx, seatRy);
      const state = config.state;

      if (this.dealerButton) this.dealerButton.destroy();
      if (state.dealerSeat < 1 || state.dealerSeat > MAX_SEATS) return;

      const seatPos = positions[state.dealerSeat - 1];
      const dx = cx - seatPos.x;
      const dy = cy - seatPos.y;
      const dist = Math.sqrt(dx * dx + dy * dy);
      if (dist === 0) return;
      const t = 0.3;
      const bx = seatPos.x + dx * t;
      const by = seatPos.y + dy * t;

      const btn = this.add.container(bx, by).setDepth(4);
      const size = 14;
      const g = this.add.graphics();
      g.fillStyle(0xffffff, 1);
      g.fillCircle(0, 0, size);
      g.lineStyle(1, 0x000000, 0.15);
      g.strokeCircle(0, 0, size);
      const txt = this.add.text(0, 0, 'D', {
        fontFamily: 'system-ui, sans-serif', fontSize: '12px',
        color: '#000000', fontStyle: 'bold',
      }).setOrigin(0.5);
      btn.add([g, txt]);
      this.dealerButton = btn;
    }
```

Update `drawAll()`:

```javascript
    drawAll() {
      this.drawFelt();
      this.drawSeats();
      this.drawCommunityCards();
      this.drawPot();
      this.drawDealerButton();
    }
```

- [ ] **Step 3: Refresh browser and verify**

Expected visual:
- The active seat (Ari by default) has a soft gold glow bleeding out behind its pod.
- A small white "D" button sits on the felt between seat 1 (You) and table center.
- Changing "Active seat #" to a different value moves the glow.
- Changing "Dealer seat #" moves the D button; setting to 0 hides it.
- Changing "Active glow intensity" to 0 removes the glow; to 1 makes it very bright.

- [ ] **Step 4: Commit**

```bash
git add phaser-table-playground.html
git commit -m "feat: active-seat glow and dealer button"
```

---

## Task 7: Real avatar images (from Dicebear) — rotated across seats

**Files:**
- Modify: `phaser-table-playground.html`

- [ ] **Step 1: Preload avatar images and use them in pods**

Add a top-level constant after `MOCK_PLAYERS`:

```javascript
  const AVATAR_URLS = [
    'https://api.dicebear.com/7.x/avataaars/png?seed=you&size=128',
    'https://api.dicebear.com/7.x/avataaars/png?seed=sam&size=128',
    'https://api.dicebear.com/7.x/avataaars/png?seed=ari&size=128',
    'https://api.dicebear.com/7.x/avataaars/png?seed=jay&size=128',
    'https://api.dicebear.com/7.x/avataaars/png?seed=kim&size=128',
    'https://api.dicebear.com/7.x/avataaars/png?seed=lee&size=128',
    'https://api.dicebear.com/7.x/avataaars/png?seed=max&size=128',
    'https://api.dicebear.com/7.x/avataaars/png?seed=nia&size=128',
    'https://api.dicebear.com/7.x/avataaars/png?seed=pat&size=128',
  ];
```

Add `preload()` to `TableScene`:

```javascript
    preload() {
      for (let i = 0; i < AVATAR_URLS.length; i++) {
        this.load.image('avatar-' + i, AVATAR_URLS[i]);
      }
      this.load.on('loaderror', (file) => {
        console.warn('Avatar failed to load:', file.key);
      });
    }
```

In `drawSeats()`, replace the avatar placeholder section (the `av`, `initial` graphics and their `container.add([bg, av, initial, ...])`) with:

```javascript
        // Avatar image (with initial-letter fallback)
        const avX = -podW / 2 + s.podPadY + s.avatarSize / 2;
        const avKey = 'avatar-' + i;
        const avatarNodes = [];
        if (this.textures.exists(avKey)) {
          const img = this.add.image(avX, 0, avKey);
          const scale = s.avatarSize / img.width;
          img.setScale(scale);
          // Circular mask
          const maskG = this.add.graphics();
          maskG.fillCircle(avX, 0, s.avatarSize / 2);
          const mask = maskG.createGeometryMask();
          img.setMask(mask);
          maskG.setVisible(false);
          avatarNodes.push(img, maskG);
        } else {
          const av = this.add.graphics();
          av.fillStyle(0x5a4428, 1);
          av.fillCircle(avX, 0, s.avatarSize / 2);
          const initial = this.add.text(avX, 0, player.name[0], {
            fontFamily: 'system-ui, sans-serif', fontSize: Math.round(s.avatarSize * 0.4) + 'px',
            color: '#ffffff', fontStyle: 'bold',
          }).setOrigin(0.5);
          avatarNodes.push(av, initial);
        }
        // Avatar ring (always on top of image)
        const ring = this.add.graphics();
        ring.lineStyle(
          s.avatarRingWidth,
          parseInt(s.avatarRingColor.replace('#', ''), 16),
          s.avatarRingAlpha
        );
        ring.strokeCircle(avX, 0, s.avatarSize / 2);
        avatarNodes.push(ring);

        // Position name & stack
        const textX = avX + s.avatarSize / 2 + s.podPadX;
        name.setPosition(textX, -s.stackSize / 2 - 1);
        stack.setPosition(textX, s.nameSize / 2 + 1);

        container.add([bg, ...avatarNodes, name, stack]);
```

- [ ] **Step 2: Refresh browser and verify**

Expected visual:
- Each pod now shows a cartoon avatar image (Avataaars style) masked to a circle, with the gold ring around it.
- If the images fail to load (network/CORS), the fallback brown circle + initial letter still renders.

If CORS blocks the Dicebear images in your browser, replace the `AVATAR_URLS` with these fallbacks (pravatar serves CORS-friendly images):

```javascript
  const AVATAR_URLS = [
    'https://i.pravatar.cc/128?u=you',
    'https://i.pravatar.cc/128?u=sam',
    'https://i.pravatar.cc/128?u=ari',
    'https://i.pravatar.cc/128?u=jay',
    'https://i.pravatar.cc/128?u=kim',
    'https://i.pravatar.cc/128?u=lee',
    'https://i.pravatar.cc/128?u=max',
    'https://i.pravatar.cc/128?u=nia',
    'https://i.pravatar.cc/128?u=pat',
  ];
```

- [ ] **Step 3: Commit**

```bash
git add phaser-table-playground.html
git commit -m "feat: load avatar images for seat pods with fallback"
```

---

## Task 8: Hole cards on seat pods + card controls group + showdown reveal

**Files:**
- Modify: `phaser-table-playground.html`

- [ ] **Step 1: Add `drawCardBack` helper**

After `drawCardFace`, add:

```javascript
  function drawCardBack(scene, x, y, w, h) {
    const c = config.cards;
    const container = scene.add.container(x, y);
    const shadow = scene.add.graphics();
    shadow.fillStyle(0x000000, c.shadowAlpha);
    shadow.fillRoundedRect(-w / 2 + 1, -h / 2 + 3, w, h, c.cornerRadius);
    const bg = scene.add.graphics();
    bg.fillStyle(parseInt(c.backColor1.replace('#', ''), 16), 1);
    bg.fillRoundedRect(-w / 2, -h / 2, w, h, c.cornerRadius);
    // Subtle inner border for detail
    bg.lineStyle(1.5, parseInt(c.backColor2.replace('#', ''), 16), 0.7);
    bg.strokeRoundedRect(-w / 2 + 3, -h / 2 + 3, w - 6, h - 6, Math.max(1, c.cornerRadius - 2));
    container.add([shadow, bg]);
    return container;
  }
```

- [ ] **Step 2: Draw hole cards above each pod**

In `drawSeats()`, after `container.add([bg, ...avatarNodes, name, stack]);`, add:

```javascript
        // Hole cards above the pod
        const holeW = config.cards.holeWidth;
        const holeH = Math.round(holeW * 1.4);
        const holeGap = 4;
        const holeY = -podH / 2 - holeH / 2 - 8;
        const isShowdownReveal = (i + 1) === state.showdownSeat;
        const holeRank = ['A', 'K'][i % 2] || 'A';
        const holeSuits = [['heart', 'spade'], ['diamond', 'club']][i % 2];

        for (let c = 0; c < 2; c++) {
          const cx2 = (c === 0 ? -1 : 1) * (holeW / 2 + holeGap / 2);
          let card;
          if (isShowdownReveal) {
            card = drawCardFace(this, cx2, holeY, holeW, holeH, holeRank, holeSuits[c]);
          } else {
            card = drawCardBack(this, cx2, holeY, holeW, holeH);
          }
          container.add(card);
        }
```

- [ ] **Step 3: Add the Cards control group**

Inside `<div class="controls">`, after Seats and before Mock state:

```html
<div class="group" data-group="cards">
  <div class="group-title">▾ Cards</div>
  <div class="group-body">
    <div class="control">
      <label>Face color <span class="val" data-val="cards.faceColor"></span></label>
      <input type="color" data-path="cards.faceColor">
    </div>
    <div class="control">
      <label>Border color <span class="val" data-val="cards.borderColor"></span></label>
      <input type="color" data-path="cards.borderColor">
    </div>
    <div class="control">
      <label>Border alpha <span class="val" data-val="cards.borderAlpha"></span></label>
      <input type="range" min="0" max="1" step="0.02" data-path="cards.borderAlpha">
    </div>
    <div class="control">
      <label>Corner radius <span class="val" data-val="cards.cornerRadius"></span></label>
      <input type="range" min="0" max="20" step="1" data-path="cards.cornerRadius">
    </div>
    <div class="control">
      <label>Rank font</label>
      <select data-path="cards.rankFont">
        <option value="Georgia, serif">Georgia (serif)</option>
        <option value="'Times New Roman', serif">Times</option>
        <option value="system-ui, sans-serif">System sans</option>
        <option value="'Courier New', monospace">Courier</option>
      </select>
    </div>
    <div class="control">
      <label>Rank size <span class="val" data-val="cards.rankSize"></span></label>
      <input type="range" min="8" max="36" step="1" data-path="cards.rankSize">
    </div>
    <div class="control">
      <label>Suit size <span class="val" data-val="cards.suitSize"></span></label>
      <input type="range" min="8" max="48" step="1" data-path="cards.suitSize">
    </div>
    <div class="control">
      <label>Community width <span class="val" data-val="cards.communityWidth"></span></label>
      <input type="range" min="40" max="120" step="1" data-path="cards.communityWidth">
    </div>
    <div class="control">
      <label>Hole width <span class="val" data-val="cards.holeWidth"></span></label>
      <input type="range" min="20" max="80" step="1" data-path="cards.holeWidth">
    </div>
    <div class="control">
      <label>Back color 1 <span class="val" data-val="cards.backColor1"></span></label>
      <input type="color" data-path="cards.backColor1">
    </div>
    <div class="control">
      <label>Back color 2 <span class="val" data-val="cards.backColor2"></span></label>
      <input type="color" data-path="cards.backColor2">
    </div>
    <div class="control">
      <label>Shadow alpha <span class="val" data-val="cards.shadowAlpha"></span></label>
      <input type="range" min="0" max="1" step="0.05" data-path="cards.shadowAlpha">
    </div>
  </div>
</div>
```

- [ ] **Step 4: Refresh browser and verify**

Expected visual:
- Each seat pod has two small blue card backs stacked above it.
- Setting "Showdown seat #" to 3 flips that seat's cards face-up (e.g., A♥ / A♠ for seat 3).
- Changing "Rank font" to Courier updates all rank text across community and (any revealed) hole cards.
- Changing "Hole width" makes all hole cards bigger/smaller uniformly.

- [ ] **Step 5: Commit**

```bash
git add phaser-table-playground.html
git commit -m "feat: hole cards with showdown reveal and card controls"
```

---

## Task 9: Bet chips + Chips/Pot control group

**Files:**
- Modify: `phaser-table-playground.html`

- [ ] **Step 1: Add `config.bets` and extend `config.state` with per-seat bets**

In `config`, replace the existing `pot` block and add a `bets` block:

```javascript
    pot: {
      pillBgAlpha: 0.5,
      accentColor: '#f5d678',
      labelSize: 9,
      amountSize: 15,
      chipSize: 18,
    },
    bets: {
      color1: '#f5d678',
      color2: '#d4a843',
      textColor: '#1a0a00',
      fontSize: 10,
      padX: 8,
      padY: 2,
    },
```

In `config.state`, add:

```javascript
      seatBets: [0, 500, 0, 1000, 0, 2000, 0, 0, 0], // 9 seats, cents
```

- [ ] **Step 2: Draw bet chips near seated players**

Add a method to `TableScene`:

```javascript
    drawBetChips() {
      const { width, height } = this.scale;
      const { cx, cy, rx, ry } = layout(width, height);
      const seatRx = rx * 1.22;
      const seatRy = ry * 1.35;
      const positions = getSeatPositions(cx, cy, seatRx, seatRy);
      const state = config.state;
      const b = config.bets;

      if (this.betChips) this.betChips.forEach(c => c.destroy());
      this.betChips = [];

      for (let i = 0; i < MAX_SEATS; i++) {
        if (i >= state.seatCount) continue;
        const amount = state.seatBets[i] || 0;
        if (amount <= 0) continue;
        const pos = positions[i];
        // Offset bet chip toward the table center from the pod
        const dx = cx - pos.x;
        const dy = cy - pos.y;
        const dist = Math.sqrt(dx * dx + dy * dy) || 1;
        const t = 0.25;
        const bx = pos.x + (dx / dist) * dist * t;
        const by = pos.y + (dy / dist) * dist * t;

        const container = this.add.container(bx, by).setDepth(4);
        const text = '$' + (amount / 100).toFixed(2);
        const txt = this.add.text(0, 0, text, {
          fontFamily: 'system-ui, sans-serif', fontSize: b.fontSize + 'px',
          color: b.textColor, fontStyle: 'bold',
        }).setOrigin(0.5);
        const w = txt.width + b.padX * 2;
        const h = txt.height + b.padY * 2;

        const bg = this.add.graphics();
        bg.fillStyle(parseInt(b.color1.replace('#', ''), 16), 1);
        bg.fillRoundedRect(-w / 2, -h / 2, w, h, h / 2);
        bg.lineStyle(1, parseInt(b.color2.replace('#', ''), 16), 1);
        bg.strokeRoundedRect(-w / 2, -h / 2, w, h, h / 2);

        container.add([bg, txt]);
        this.betChips.push(container);
      }
    }
```

Update `drawAll()`:

```javascript
    drawAll() {
      this.drawFelt();
      this.drawSeats();
      this.drawCommunityCards();
      this.drawPot();
      this.drawBetChips();
      this.drawDealerButton();
    }
```

- [ ] **Step 3: Add the Chips / Pot control group**

Inside `<div class="controls">`, after Cards and before Mock state:

```html
<div class="group" data-group="chips">
  <div class="group-title">▾ Chips / Pot</div>
  <div class="group-body">
    <div class="control">
      <label>Pot pill bg alpha <span class="val" data-val="pot.pillBgAlpha"></span></label>
      <input type="range" min="0" max="1" step="0.05" data-path="pot.pillBgAlpha">
    </div>
    <div class="control">
      <label>Pot accent <span class="val" data-val="pot.accentColor"></span></label>
      <input type="color" data-path="pot.accentColor">
    </div>
    <div class="control">
      <label>Pot label size <span class="val" data-val="pot.labelSize"></span></label>
      <input type="range" min="6" max="16" step="1" data-path="pot.labelSize">
    </div>
    <div class="control">
      <label>Pot amount size <span class="val" data-val="pot.amountSize"></span></label>
      <input type="range" min="10" max="26" step="1" data-path="pot.amountSize">
    </div>
    <div class="control">
      <label>Pot chip size <span class="val" data-val="pot.chipSize"></span></label>
      <input type="range" min="8" max="36" step="1" data-path="pot.chipSize">
    </div>
    <div class="control">
      <label>Bet color 1 <span class="val" data-val="bets.color1"></span></label>
      <input type="color" data-path="bets.color1">
    </div>
    <div class="control">
      <label>Bet color 2 <span class="val" data-val="bets.color2"></span></label>
      <input type="color" data-path="bets.color2">
    </div>
    <div class="control">
      <label>Bet text color <span class="val" data-val="bets.textColor"></span></label>
      <input type="color" data-path="bets.textColor">
    </div>
    <div class="control">
      <label>Bet font size <span class="val" data-val="bets.fontSize"></span></label>
      <input type="range" min="8" max="16" step="1" data-path="bets.fontSize">
    </div>
  </div>
</div>
```

- [ ] **Step 4: Refresh browser and verify**

Expected visual:
- Three bet chips appear between some seats and the table center (seats 2, 4, 6 by default — $5.00 / $10.00 / $20.00).
- Changing "Bet color 1" updates the bet-chip background live.
- Changing "Pot accent" to a different color updates the pot pill chip and amount text.

- [ ] **Step 5: Commit**

```bash
git add phaser-table-playground.html
git commit -m "feat: bet chips and chips/pot control group"
```

---

## Task 10: Typography group — global font override and base scale

**Files:**
- Modify: `phaser-table-playground.html`

- [ ] **Step 1: Add `config.typography`**

In `config`, add:

```javascript
    typography: {
      uiFont: 'system-ui, sans-serif',
      scale: 1.0,  // global multiplier applied to all font sizes
    },
```

- [ ] **Step 2: Replace hardcoded `'system-ui, sans-serif'` font strings and size usages with helpers**

At the bottom of the helpers section (near `hexToRgb`, `darken`), add:

```javascript
  // ===== Typography helpers =====
  function uiFont() { return config.typography.uiFont; }
  function scaled(px) { return Math.round(px * config.typography.scale); }
```

Then find every `fontFamily: 'system-ui, sans-serif'` inside the `TableScene` methods (pot, seats, initial, card text for ranks/suits when they use system-ui, bet chip, dealer button) and replace with `fontFamily: uiFont()`.

For every `fontSize: <number> + 'px'` inside the scene methods (e.g., `s.nameSize`, `s.stackSize`, `p.amountSize`, `p.labelSize`, `b.fontSize`, dealer '12px', initial letter size), wrap the size in `scaled(...)`:

Example changes:

```javascript
    // BEFORE
    const name = this.add.text(0, 0, player.name, {
      fontFamily: 'system-ui, sans-serif', fontSize: s.nameSize + 'px',
      color: s.nameColor, fontStyle: 'bold',
    }).setOrigin(0, 0.5);
    // AFTER
    const name = this.add.text(0, 0, player.name, {
      fontFamily: uiFont(), fontSize: scaled(s.nameSize) + 'px',
      color: s.nameColor, fontStyle: 'bold',
    }).setOrigin(0, 0.5);
```

Apply the same pattern consistently in `drawPot`, `drawSeats` (name, stack, initial fallback), `drawBetChips`, and `drawDealerButton`. The dealer button currently uses a literal `fontSize: '12px'` string — change it to `fontSize: scaled(12) + 'px'`. Leave `drawCardFace`/`drawCardBack` using `config.cards.rankFont` (they have their own font setting — the typography override is for UI chrome, not cards).

- [ ] **Step 3: Add the Typography control group**

Inside `<div class="controls">`, before Mock state:

```html
<div class="group" data-group="typography">
  <div class="group-title">▾ Typography</div>
  <div class="group-body">
    <div class="control">
      <label>UI font</label>
      <select data-path="typography.uiFont">
        <option value="system-ui, sans-serif">System</option>
        <option value="-apple-system, BlinkMacSystemFont, sans-serif">Apple</option>
        <option value="'Inter', sans-serif">Inter</option>
        <option value="'Helvetica Neue', sans-serif">Helvetica</option>
        <option value="Arial, sans-serif">Arial</option>
        <option value="Georgia, serif">Georgia</option>
      </select>
    </div>
    <div class="control">
      <label>Scale <span class="val" data-val="typography.scale"></span></label>
      <input type="range" min="0.7" max="1.5" step="0.05" data-path="typography.scale">
    </div>
  </div>
</div>
```

- [ ] **Step 4: Refresh browser and verify**

Expected visual:
- All pod, pot, bet, and dealer text renders in the default system font.
- Changing "UI font" to Georgia turns player names and pot amount serif (card ranks stay whatever `cards.rankFont` is set to).
- Setting "Scale" to 1.3 enlarges all UI text uniformly; 0.8 shrinks it.

- [ ] **Step 5: Commit**

```bash
git add phaser-table-playground.html
git commit -m "feat: typography group with global font and scale"
```

---

## Task 11: Controls panel collapse toggle

**Files:**
- Modify: `phaser-table-playground.html`

- [ ] **Step 1: Add the toggle button + hidden state CSS**

In the `<style>` block, add after the `.controls` rule:

```css
  .controls.hidden {
    width: 24px;
    padding: 8px 4px;
    overflow: hidden;
  }
  .controls.hidden > :not(.panel-toggle) { display: none; }
  .panel-toggle {
    position: absolute;
    top: 10px; right: 8px;
    background: #2a2a2a;
    color: #aaa;
    border: 1px solid #444;
    border-radius: 3px;
    padding: 1px 6px;
    font-size: 11px;
    cursor: pointer;
    z-index: 10;
  }
  .controls.hidden .panel-toggle {
    right: 4px;
  }
  .layout {
    position: relative;
  }
  .layout:has(.controls.hidden) {
    grid-template-columns: 24px 1fr;
  }
```

In the `<div class="controls">`, just after the `<h1>`, insert:

```html
<button class="panel-toggle" id="panel-toggle" aria-label="Toggle panel">◀</button>
```

- [ ] **Step 2: Wire the toggle and resize Phaser**

At the end of the `<script>` block (after `rerender();`), add:

```javascript
  const panel = document.querySelector('.controls');
  const toggle = document.getElementById('panel-toggle');
  toggle.addEventListener('click', () => {
    panel.classList.toggle('hidden');
    toggle.textContent = panel.classList.contains('hidden') ? '▶' : '◀';
    // Let the grid settle, then tell Phaser to resize
    requestAnimationFrame(() => {
      const s = getCanvasSize();
      game.scale.resize(s.width, s.height);
      rerender();
    });
  });
```

- [ ] **Step 3: Refresh browser and verify**

Expected visual:
- A small "◀" button appears at the top-right of the controls panel.
- Clicking it collapses the sidebar to a 24px-wide strip; Phaser canvas expands to fill and the table re-renders centered.
- The button now shows "▶"; clicking again restores the panel.

- [ ] **Step 4: Commit**

```bash
git add phaser-table-playground.html
git commit -m "feat: collapsible controls panel"
```

---

## Task 12: Final polish — match CSS version as baseline and verify end-to-end

**Files:**
- Modify: `phaser-table-playground.html`

This task is the point of the whole playground: tune the defaults in `config` until the Phaser render, at a glance, looks comparable to `src/app/game-lobby/css-poker-table/`. No code structure changes — only numeric/color defaults.

- [ ] **Step 1: Side-by-side compare**

Run Angular to see the CSS reference: `npm start`, then open `http://localhost:4200` and navigate to a live or mocked game lobby showing the CSS table. Keep it open beside the playground HTML.

- [ ] **Step 2: Tune defaults by eye**

Starting from the current `config` defaults, adjust values in the playground until the Phaser render matches. Likely touch-ups:
- Felt base color & center highlight tones
- Rail width and inner-border color
- Pod radius, padding, and active border color/glow intensity
- Card face color (pure white), rank size, suit size, community card width
- Pot accent (gold #f5d678) and pill alpha
- Typography scale so text looks proportional at the canvas size

When satisfied, copy the JSON from the readout and paste it into the body of the `config` object declaration in the script, replacing the initial values — so the file reloads to the tuned defaults.

- [ ] **Step 3: Final visual verification**

Refresh the playground with the new defaults. Expected visual:
- Green felt with subtle radial gradient, brown rail, thin gold inner line — clearly matching the CSS version.
- Pods with avatar images, dark bg, gold glow on active seat, dealer button on felt near seat 1.
- Community cards (flop) centered with white faces and colored rank/suit.
- Gold pot pill with chip + amount.
- Three bet chips near betting seats.
- Hole cards rendered as blue card backs above each pod; showdown seat reveals faces.
- No console errors.

- [ ] **Step 4: Commit tuned defaults**

```bash
git add phaser-table-playground.html
git commit -m "feat: tune playground defaults to match CSS-version baseline"
```

---

## Self-Review Notes

- The plan delivers the playground described in `docs/superpowers/specs/2026-04-17-phaser-table-playground-design.md` across 12 tasks.
- Non-goals (animations, shaders, presets, tests, responsive, real game state) stayed out.
- Each task has runnable verification (open/refresh the HTML, check described visuals).
- Each task commits.
- Field names stay consistent across tasks (`config.felt`, `config.seats`, `config.cards`, `config.pot`, `config.bets`, `config.state`, `config.typography`).
- The `drawAll()` method is the single re-entry point for rendering; tasks add new draw methods to it incrementally.
