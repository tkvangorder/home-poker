import Phaser from 'phaser';
import { Pot } from '../../../game/game-models';

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
