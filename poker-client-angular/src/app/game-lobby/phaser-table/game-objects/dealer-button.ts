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
