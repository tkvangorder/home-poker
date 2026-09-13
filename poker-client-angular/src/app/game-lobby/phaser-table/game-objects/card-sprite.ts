import Phaser from 'phaser';
import { CardSuit, CardValue } from '../../../poker/poker-models';
import { SeatCard } from '../../../game/game-models';

const SUIT_SYMBOLS: Record<CardSuit, string> = {
  [CardSuit.HEART]: '\u2665',
  [CardSuit.DIAMOND]: '\u2666',
  [CardSuit.CLUB]: '\u2663',
  [CardSuit.SPADE]: '\u2660',
};

const SUIT_COLORS: Record<CardSuit, string> = {
  [CardSuit.HEART]: '#dc2626',
  [CardSuit.DIAMOND]: '#2563eb',
  [CardSuit.CLUB]: '#15803d',
  [CardSuit.SPADE]: '#1e293b',
};

const RANK_LABELS: Record<CardValue, string> = {
  [CardValue.TWO]: '2',
  [CardValue.THREE]: '3',
  [CardValue.FOUR]: '4',
  [CardValue.FIVE]: '5',
  [CardValue.SIX]: '6',
  [CardValue.SEVEN]: '7',
  [CardValue.EIGHT]: '8',
  [CardValue.NINE]: '9',
  [CardValue.TEN]: '10',
  [CardValue.JACK]: 'J',
  [CardValue.QUEEN]: 'Q',
  [CardValue.KING]: 'K',
  [CardValue.ACE]: 'A',
};

const FACE_COLOR = 0xffffff;
const BORDER_COLOR = 0x000000;
const BORDER_ALPHA = 0.12;
const SHADOW_ALPHA = 0.4;
const RANK_FONT = 'Georgia, serif';

const BACK_COLOR_1 = 0x1a5c8a;
const BACK_COLOR_2 = 0x0e3d5e;

const HIGHLIGHT_COLOR = 0xf5d678;
const DIM_ALPHA = 0.4;
const HIGHLIGHT_SCALE = 1.08;

export class CardSprite extends Phaser.GameObjects.Container {
  private highlight: Phaser.GameObjects.Graphics;
  private shadow: Phaser.GameObjects.Graphics;
  private bg: Phaser.GameObjects.Graphics;
  private rankText: Phaser.GameObjects.Text;
  private suitText: Phaser.GameObjects.Text;
  private cardWidth = 40;
  private cardHeight = 56;
  private cornerRadius = 5;
  private highlightTween: Phaser.Tweens.Tween | null = null;

  constructor(scene: Phaser.Scene, x: number, y: number) {
    super(scene, x, y);
    scene.add.existing(this);

    this.highlight = new Phaser.GameObjects.Graphics(scene);
    this.add(this.highlight);

    this.shadow = new Phaser.GameObjects.Graphics(scene);
    this.add(this.shadow);

    this.bg = new Phaser.GameObjects.Graphics(scene);
    this.add(this.bg);

    this.rankText = new Phaser.GameObjects.Text(scene, 0, 0, '', {
      fontSize: '18px',
      fontFamily: RANK_FONT,
      fontStyle: 'bold',
      color: '#000000',
      align: 'center',
    }).setOrigin(0.5);
    this.add(this.rankText);

    this.suitText = new Phaser.GameObjects.Text(scene, 0, 0, '', {
      fontSize: '22px',
      fontFamily: RANK_FONT,
      color: '#000000',
      align: 'center',
    }).setOrigin(0.5);
    this.add(this.suitText);

    this.setVisible(false);
  }

  showCard(seatCard: SeatCard): void {
    if (seatCard.showCard) {
      const rank = RANK_LABELS[seatCard.card.value] ?? '?';
      const suit = SUIT_SYMBOLS[seatCard.card.suit] ?? '';
      const color = SUIT_COLORS[seatCard.card.suit] ?? '#000000';
      this.drawFace(rank, suit, color);
    } else {
      this.drawBack();
    }
    this.setVisible(true);
  }

  showBack(): void {
    this.drawBack();
    this.setVisible(true);
  }

  hide(): void {
    this.setVisible(false);
  }

  setTint(color: number): void {
    this.iterate((child: Phaser.GameObjects.GameObject) => {
      const tintable = child as Phaser.GameObjects.GameObject & { setTint?: (c: number) => void };
      if (typeof tintable.setTint === 'function') {
        tintable.setTint(color);
      }
    });
  }

  /** Gold pulsing glow + slight scale-up to mark a winning card. */
  setHighlight(on: boolean): void {
    if (this.highlightTween) {
      this.highlightTween.stop();
      this.highlightTween = null;
    }
    this.highlight.clear();
    if (!on) {
      this.setScale(1);
      return;
    }
    const w = this.cardWidth;
    const h = this.cardHeight;
    const r = this.cornerRadius;
    const pad = Math.max(3, Math.round(w * 0.12));
    for (let g = 0; g < 3; g++) {
      const a = 0.5 * (1 - g / 3);
      const gp = pad * (g + 1);
      this.highlight.fillStyle(HIGHLIGHT_COLOR, a);
      this.highlight.fillRoundedRect(-w / 2 - gp, -h / 2 - gp, w + gp * 2, h + gp * 2, r + gp);
    }
    this.setScale(HIGHLIGHT_SCALE);
    this.highlightTween = this.scene.tweens.add({
      targets: this.highlight,
      alpha: { from: 1, to: 0.55 },
      duration: 700,
      yoyo: true,
      repeat: -1,
      ease: 'Sine.easeInOut',
    });
  }

  /** Fade a non-winning card so the highlighted ones stand out. */
  setDimmed(on: boolean): void {
    this.setAlpha(on ? DIM_ALPHA : 1);
  }

  setCardSize(width: number): void {
    this.cardWidth = width;
    this.cardHeight = Math.round(width * 1.4);
    this.cornerRadius = Math.max(3, Math.round(width * 0.1));
  }

  private drawFace(rank: string, suit: string, color: string): void {
    const w = this.cardWidth;
    const h = this.cardHeight;
    const r = this.cornerRadius;

    this.shadow.clear();
    this.shadow.fillStyle(0x000000, SHADOW_ALPHA);
    this.shadow.fillRoundedRect(-w / 2 + 1, -h / 2 + 3, w, h, r);

    this.bg.clear();
    this.bg.fillStyle(FACE_COLOR, 1);
    this.bg.fillRoundedRect(-w / 2, -h / 2, w, h, r);
    this.bg.lineStyle(1, BORDER_COLOR, BORDER_ALPHA);
    this.bg.strokeRoundedRect(-w / 2, -h / 2, w, h, r);

    const rankSize = Math.max(10, Math.round(w * 0.45));
    this.rankText.setFontSize(rankSize);
    this.rankText.setFontFamily(RANK_FONT);
    this.rankText.setColor(color);
    this.rankText.setText(rank);
    this.rankText.setPosition(0, -h * 0.12);
    this.rankText.setVisible(true);

    const suitSize = Math.max(12, Math.round(w * 0.6));
    this.suitText.setFontSize(suitSize);
    this.suitText.setFontFamily(RANK_FONT);
    this.suitText.setColor(color);
    this.suitText.setText(suit);
    this.suitText.setPosition(0, h * 0.18);
    this.suitText.setVisible(true);
  }

  private drawBack(): void {
    const w = this.cardWidth;
    const h = this.cardHeight;
    const r = this.cornerRadius;

    this.shadow.clear();
    this.shadow.fillStyle(0x000000, SHADOW_ALPHA);
    this.shadow.fillRoundedRect(-w / 2 + 1, -h / 2 + 3, w, h, r);

    this.bg.clear();
    this.bg.fillStyle(BACK_COLOR_1, 1);
    this.bg.fillRoundedRect(-w / 2, -h / 2, w, h, r);
    this.bg.lineStyle(1.5, BACK_COLOR_2, 0.7);
    this.bg.strokeRoundedRect(-w / 2 + 3, -h / 2 + 3, w - 6, h - 6, Math.max(1, r - 2));

    this.rankText.setVisible(false);
    this.suitText.setVisible(false);
  }
}
