import Phaser from 'phaser';
import { Card } from '../../../poker/poker-models';
import { CardSprite } from './card-sprite';

const MAX_COMMUNITY_CARDS = 5;
const PLACEHOLDER_ALPHA = 0.12;

export class CommunityCards extends Phaser.GameObjects.Container {
  private cards: CardSprite[] = [];
  private placeholders: Phaser.GameObjects.Graphics;
  private cardWidth = 48;

  constructor(scene: Phaser.Scene, x: number, y: number) {
    super(scene, x, y);
    scene.add.existing(this);

    this.placeholders = new Phaser.GameObjects.Graphics(scene);
    this.add(this.placeholders);

    for (let i = 0; i < MAX_COMMUNITY_CARDS; i++) {
      const card = new CardSprite(scene, 0, 0);
      card.hide();
      this.cards.push(card);
      this.add(card);
    }
  }

  updateCards(communityCards: Card[]): void {
    const spacing = this.cardWidth + 8;
    const totalWidth = (MAX_COMMUNITY_CARDS - 1) * spacing;
    const startX = -totalWidth / 2;
    const h = Math.round(this.cardWidth * 1.4);
    const r = Math.max(3, Math.round(this.cardWidth * 0.1));

    // Placeholder outlines for all 5 positions
    this.placeholders.clear();
    this.placeholders.lineStyle(1.5, 0xffffff, PLACEHOLDER_ALPHA);
    for (let i = 0; i < MAX_COMMUNITY_CARDS; i++) {
      const px = startX + i * spacing;
      this.placeholders.strokeRoundedRect(
        px - this.cardWidth / 2, -h / 2,
        this.cardWidth, h, r
      );
    }

    for (let i = 0; i < MAX_COMMUNITY_CARDS; i++) {
      this.cards[i].setCardSize(this.cardWidth);
      if (i < communityCards.length) {
        this.cards[i].setPosition(startX + i * spacing, 0);
        this.cards[i].showCard({ card: communityCards[i], showCard: true });
      } else {
        this.cards[i].hide();
      }
    }
  }

  /**
   * Highlight / dim the board for the showdown. `null` clears all cards back to
   * normal. Otherwise the listed indices glow and every other card dims.
   */
  applyShowdownHighlight(highlightIndices: number[] | null): void {
    for (let i = 0; i < MAX_COMMUNITY_CARDS; i++) {
      const card = this.cards[i];
      if (highlightIndices == null) {
        card.setHighlight(false);
        card.setDimmed(false);
      } else if (highlightIndices.includes(i)) {
        card.setDimmed(false);
        card.setHighlight(true);
      } else {
        card.setHighlight(false);
        card.setDimmed(true);
      }
    }
  }

  resize(cardWidth: number): void {
    this.cardWidth = cardWidth;
  }
}
