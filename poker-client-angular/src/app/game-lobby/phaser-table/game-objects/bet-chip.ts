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
  private podW = 0;

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

  /** Half-width of the visible pod, in pixels, for external placement (e.g. dealer button). 0 when hidden. */
  getHalfWidth(): number {
    return this.visible ? this.podW / 2 : 0;
  }

  private redraw(): void {
    const w = this.text.width + PAD_X * 2;
    const h = this.text.height + PAD_Y * 2;
    this.podW = w;
    this.bg.clear();
    this.bg.fillStyle(COLOR_1, 1);
    this.bg.fillRoundedRect(-w / 2, -h / 2, w, h, h / 2);
    this.bg.lineStyle(1, COLOR_2, 1);
    this.bg.strokeRoundedRect(-w / 2, -h / 2, w, h, h / 2);
  }
}
