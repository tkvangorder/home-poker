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
