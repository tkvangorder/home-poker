import Phaser from 'phaser';
import { CardSprite } from './card-sprite';
import { SeatCard } from '../../../game/game-models';

const POD_BG = 0x0f0a05;
const POD_BG_ALPHA = 0.8;
const POD_BG_FOLDED_ALPHA = 0.4;
const FOLDED_TEXT_COLOR = 'rgba(255,255,255,0.35)';
const FOLDED_TAG_GAP = 2;
const POD_BORDER_IDLE = 0xffffff;
const POD_BORDER_IDLE_ALPHA = 0.08;
const POD_BORDER_ACTIVE = 0xf5d678;
const ACTIVE_GLOW_INTENSITY = 0.25;
const WINNER_GLOW_COLOR = 0xf5d678;
const WINNER_GLOW_INTENSITY = 0.5;
const WINNER_NAME_COLOR = '#f5d678';

const AVATAR_BG = 0x5a4428;
const AVATAR_RING_COLOR = 0xdaa520;
const AVATAR_RING_ALPHA = 0.3;
const AVATAR_INITIAL_COLOR = '#ffffff';
const NAME_COLOR = '#ffffff';
const STACK_COLOR = 'rgba(255,255,255,0.55)';

const EMPTY_COLOR = 0xffffff;
const EMPTY_ALPHA = 0.18;
const EMPTY_COLOR_STR = '#ffffff';

const TIMER_COLOR = 0xf5d678;
const TIMER_TRACK_COLOR = 0x000000;
const TIMER_TRACK_ALPHA = 0.4;
const TIMER_INSET_X = 6;
const TIMER_GAP = 4;

export interface SeatSizing {
  podPadX: number;
  podPadY: number;
  podRadius: number;
  avatarSize: number;
  avatarRingWidth: number;
  nameSize: number;
  stackSize: number;
  holeWidth: number;
  emptySize: number;
  timerHeight: number;
}

export class SeatDisplay extends Phaser.GameObjects.Container {
  private bg: Phaser.GameObjects.Graphics;
  private glow: Phaser.GameObjects.Graphics;
  private winnerGlow: Phaser.GameObjects.Graphics;
  private avatarBg: Phaser.GameObjects.Graphics;
  private avatarRing: Phaser.GameObjects.Graphics;
  private initialText: Phaser.GameObjects.Text;
  private nameText: Phaser.GameObjects.Text;
  private stackText: Phaser.GameObjects.Text;
  private card1: CardSprite;
  private card2: CardSprite;
  private foldedTag: Phaser.GameObjects.Text;
  private lastPodH = 0;
  private winnerHighlight = false;

  // Empty-slot visuals
  private emptyRing: Phaser.GameObjects.Graphics;
  private emptyPlus: Phaser.GameObjects.Text;

  private timer: Phaser.GameObjects.Graphics;
  private timerFrac = 0;
  private timerPodW = 0;
  private timerPodH = 0;
  private timerActive = false;

  private sizing: SeatSizing = {
    podPadX: 10, podPadY: 6, podRadius: 12,
    avatarSize: 36, avatarRingWidth: 2,
    nameSize: 11, stackSize: 10,
    holeWidth: 44, emptySize: 28,
    timerHeight: 3,
  };

  constructor(scene: Phaser.Scene, x: number, y: number) {
    super(scene, x, y);
    scene.add.existing(this);

    this.glow = new Phaser.GameObjects.Graphics(scene);
    this.add(this.glow);

    this.winnerGlow = new Phaser.GameObjects.Graphics(scene);
    this.add(this.winnerGlow);

    this.bg = new Phaser.GameObjects.Graphics(scene);
    this.add(this.bg);

    this.timer = new Phaser.GameObjects.Graphics(scene);
    this.add(this.timer);

    this.avatarBg = new Phaser.GameObjects.Graphics(scene);
    this.add(this.avatarBg);

    this.initialText = new Phaser.GameObjects.Text(scene, 0, 0, '', {
      fontFamily: 'system-ui, sans-serif',
      fontSize: '14px',
      fontStyle: 'bold',
      color: AVATAR_INITIAL_COLOR,
    }).setOrigin(0.5);
    this.add(this.initialText);

    this.avatarRing = new Phaser.GameObjects.Graphics(scene);
    this.add(this.avatarRing);

    this.nameText = new Phaser.GameObjects.Text(scene, 0, 0, '', {
      fontFamily: 'system-ui, sans-serif',
      fontSize: '11px',
      fontStyle: 'bold',
      color: NAME_COLOR,
    }).setOrigin(0, 0.5);
    this.add(this.nameText);

    this.stackText = new Phaser.GameObjects.Text(scene, 0, 0, '', {
      fontFamily: 'system-ui, sans-serif',
      fontSize: '10px',
      color: STACK_COLOR,
    }).setOrigin(0, 0.5);
    this.add(this.stackText);

    this.foldedTag = new Phaser.GameObjects.Text(scene, 0, 0, 'FOLDED', {
      fontFamily: 'system-ui, sans-serif',
      fontSize: '9px',
      fontStyle: 'bold',
      color: FOLDED_TEXT_COLOR,
    }).setOrigin(0, 0.5);
    this.add(this.foldedTag);
    this.foldedTag.setVisible(false);

    this.card1 = new CardSprite(scene, 0, 0);
    this.add(this.card1);
    this.card1.hide();

    this.card2 = new CardSprite(scene, 0, 0);
    this.add(this.card2);
    this.card2.hide();

    this.emptyRing = new Phaser.GameObjects.Graphics(scene);
    this.add(this.emptyRing);

    this.emptyPlus = new Phaser.GameObjects.Text(scene, 0, 0, '+', {
      fontFamily: 'system-ui, sans-serif',
      fontSize: '18px',
      fontStyle: 'bold',
      color: EMPTY_COLOR_STR,
    }).setOrigin(0.5).setAlpha(EMPTY_ALPHA);
    this.add(this.emptyPlus);

    this.setVisible(false);
  }

  applySizing(sizing: SeatSizing): void {
    this.sizing = sizing;
  }

  updateSeat(
    playerName: string | null,
    chipCount: number | null,
    cards: SeatCard[] | null,
    isActive: boolean,
    isFolded: boolean,
    actionOverlay: { text: string; color: string } | null = null,
  ): void {
    if (!playerName) {
      this.renderEmpty();
      return;
    }
    this.renderOccupied(playerName, chipCount, cards, isActive, isFolded, actionOverlay);
  }

  setTimer(frac: number): void {
    this.timerFrac = Math.max(0, Math.min(1, frac));
    if (this.timerActive) this.redrawTimer();
  }

  private redrawTimer(): void {
    this.timer.clear();
    if (!this.timerActive) return;
    const barH = this.sizing.timerHeight;
    const fullW = this.timerPodW - TIMER_INSET_X * 2;
    const barY = this.timerPodH / 2 + TIMER_GAP + barH / 2;
    this.timer.fillStyle(TIMER_TRACK_COLOR, TIMER_TRACK_ALPHA);
    this.timer.fillRoundedRect(-fullW / 2, barY - barH / 2, fullW, barH, barH / 2);
    if (this.timerFrac > 0) {
      const w = fullW * this.timerFrac;
      this.timer.fillStyle(TIMER_COLOR, 1);
      this.timer.fillRoundedRect(-fullW / 2, barY - barH / 2, w, barH, barH / 2);
    }
  }

  private renderEmpty(): void {
    this.setVisible(true);
    this.glow.clear();
    this.winnerGlow.clear();
    this.bg.clear();
    this.avatarBg.clear();
    this.avatarRing.clear();
    this.initialText.setVisible(false);
    this.nameText.setVisible(false);
    this.stackText.setVisible(false);
    this.foldedTag.setVisible(false);
    this.card1.hide();
    this.card2.hide();
    this.timer.clear();
    this.timerActive = false;
    const r = this.sizing.emptySize / 2;
    this.emptyRing.clear();
    this.emptyRing.lineStyle(1.5, EMPTY_COLOR, EMPTY_ALPHA);
    this.emptyRing.strokeCircle(0, 0, r);
    this.emptyPlus.setFontSize(Math.round(this.sizing.emptySize * 0.6));
    this.emptyPlus.setVisible(true);
  }

  private renderOccupied(
    playerName: string,
    chipCount: number | null,
    cards: SeatCard[] | null,
    isActive: boolean,
    isFolded: boolean,
    actionOverlay: { text: string; color: string } | null,
  ): void {
    this.setVisible(true);
    this.emptyRing.clear();
    this.emptyPlus.setVisible(false);

    const s = this.sizing;
    const nameFontSize = Math.max(10, s.nameSize);
    const stackFontSize = Math.max(9, s.stackSize);

    this.nameText.setFontSize(nameFontSize);
    // Measure the widest realistic action message so the pod reserves space
    // for it. Without this floor, the pod resizes when an overlay swaps in.
    this.nameText.setText('RAISE → $9999.99');
    const reservedTextW = this.nameText.width;

    if (actionOverlay) {
      this.nameText.setText(actionOverlay.text);
      this.nameText.setColor(actionOverlay.color);
    } else {
      this.nameText.setText(this.truncateName(playerName));
      this.nameText.setColor(this.winnerHighlight ? WINNER_NAME_COLOR : NAME_COLOR);
    }
    this.stackText.setFontSize(stackFontSize);
    this.stackText.setText(chipCount != null ? this.formatChips(chipCount) : '');

    const textW = Math.max(this.nameText.width, this.stackText.width, reservedTextW);
    const holeW = s.holeWidth;
    const holeH = Math.round(holeW * 1.4);
    const holeGap = 3;
    const holeAreaW = holeW * 2 + holeGap + s.podPadX;
    const podW = s.avatarSize + s.podPadX + textW + s.podPadX + holeAreaW + s.podPadX;
    const podH = Math.max(
      s.avatarSize + s.podPadY * 2,
      nameFontSize + stackFontSize + 10,
      holeH + s.podPadY * 2
    );

    // Glow behind active pod
    this.glow.clear();
    if (isActive) {
      const glowPad = 8;
      for (let g = 0; g < 4; g++) {
        const a = ACTIVE_GLOW_INTENSITY * (1 - g / 4) * 0.5;
        this.glow.fillStyle(POD_BORDER_ACTIVE, a);
        this.glow.fillRoundedRect(
          -podW / 2 - glowPad * (g + 1),
          -podH / 2 - glowPad * (g + 1),
          podW + glowPad * 2 * (g + 1),
          podH + glowPad * 2 * (g + 1),
          s.podRadius + glowPad * (g + 1)
        );
      }
    }

    // Background pod
    this.bg.clear();
    const podAlpha = isFolded ? POD_BG_FOLDED_ALPHA : POD_BG_ALPHA;
    this.bg.fillStyle(POD_BG, podAlpha);
    this.bg.fillRoundedRect(-podW / 2, -podH / 2, podW, podH, s.podRadius);
    const borderColor = isActive ? POD_BORDER_ACTIVE : POD_BORDER_IDLE;
    const borderAlpha = isActive ? 1 : POD_BORDER_IDLE_ALPHA;
    this.bg.lineStyle(1, borderColor, borderAlpha);
    this.bg.strokeRoundedRect(-podW / 2, -podH / 2, podW, podH, s.podRadius);

    // Avatar
    const avX = -podW / 2 + s.podPadY + s.avatarSize / 2;
    this.avatarBg.clear();
    this.avatarBg.fillStyle(AVATAR_BG, 1);
    this.avatarBg.fillCircle(avX, 0, s.avatarSize / 2);

    this.initialText.setFontSize(Math.round(s.avatarSize * 0.4));
    this.initialText.setText(playerName.charAt(0));
    this.initialText.setPosition(avX, 0);
    this.initialText.setVisible(true);

    this.avatarRing.clear();
    this.avatarRing.lineStyle(s.avatarRingWidth, AVATAR_RING_COLOR, AVATAR_RING_ALPHA);
    this.avatarRing.strokeCircle(avX, 0, s.avatarSize / 2);

    // Name / stack
    const textX = avX + s.avatarSize / 2 + s.podPadX;
    this.nameText.setPosition(textX, -stackFontSize / 2 - 1);
    this.nameText.setVisible(true);
    this.stackText.setPosition(textX, nameFontSize / 2 + 1);
    this.stackText.setVisible(true);

    // Hole cards inline (right side)
    const holeStartX = textX + textW + s.podPadX;
    const cx1 = holeStartX + holeW / 2;
    const cx2 = cx1 + holeW + holeGap;

    this.card1.setCardSize(holeW);
    this.card1.setPosition(cx1, 0);
    if (cards && cards.length >= 1) {
      this.card1.showCard(cards[0]);
    } else {
      this.card1.hide();
    }

    this.card2.setCardSize(holeW);
    this.card2.setPosition(cx2, 0);
    if (cards && cards.length >= 2) {
      this.card2.showCard(cards[1]);
    } else {
      this.card2.hide();
    }

    // Folded tag below the stack text
    if (isFolded) {
      this.foldedTag.setFontSize(Math.max(8, Math.round(stackFontSize * 0.85)));
      this.foldedTag.setPosition(textX, nameFontSize / 2 + stackFontSize + FOLDED_TAG_GAP);
      this.foldedTag.setVisible(true);
    } else {
      this.foldedTag.setVisible(false);
    }

    // Greyscale tint on hole cards when folded
    const cardTint = isFolded ? 0x808080 : 0xffffff;
    this.card1.setTint(cardTint);
    this.card2.setTint(cardTint);

    this.lastPodH = podH;

    this.timerPodW = podW;
    this.timerPodH = podH;
    this.timerActive = isActive;
    this.redrawTimer();
    this.drawWinnerGlow(podW, podH);
  }

  /** Half-height of the current pod, in pixels, for external placement (e.g. action badges). */
  getPodHalfHeight(): number {
    return this.lastPodH / 2;
  }

  /** Gold pulse-free glow + gold name to mark the winning seat. Persists across re-renders. */
  setWinnerHighlight(on: boolean): void {
    this.winnerHighlight = on;
    this.drawWinnerGlow(this.timerPodW, this.timerPodH);
    // Re-tint the name without a full re-render; renderOccupied also honours the flag.
    this.nameText.setColor(on ? WINNER_NAME_COLOR : NAME_COLOR);
  }

  /**
   * Highlight / dim this seat's two hole cards for the showdown.
   * `null` clears both. Otherwise the listed indices glow and the rest dim.
   */
  applyShowdownHighlight(highlightIndices: number[] | null): void {
    const cards = [this.card1, this.card2];
    for (let i = 0; i < cards.length; i++) {
      if (highlightIndices == null) {
        cards[i].setHighlight(false);
        cards[i].setDimmed(false);
      } else if (highlightIndices.includes(i)) {
        cards[i].setDimmed(false);
        cards[i].setHighlight(true);
      } else {
        cards[i].setHighlight(false);
        cards[i].setDimmed(true);
      }
    }
  }

  private drawWinnerGlow(podW: number, podH: number): void {
    this.winnerGlow.clear();
    if (!this.winnerHighlight || podW <= 0 || podH <= 0) return;
    const glowPad = 8;
    for (let g = 0; g < 5; g++) {
      const a = WINNER_GLOW_INTENSITY * (1 - g / 5);
      this.winnerGlow.fillStyle(WINNER_GLOW_COLOR, a);
      this.winnerGlow.fillRoundedRect(
        -podW / 2 - glowPad * (g + 1),
        -podH / 2 - glowPad * (g + 1),
        podW + glowPad * 2 * (g + 1),
        podH + glowPad * 2 * (g + 1),
        this.sizing.podRadius + glowPad * (g + 1),
      );
    }
  }

  private truncateName(name: string): string {
    return name.length > 10 ? name.substring(0, 9) + '\u2026' : name;
  }

  private formatChips(cents: number): string {
    return '$' + (cents / 100).toFixed(2);
  }
}
