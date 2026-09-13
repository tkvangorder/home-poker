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
