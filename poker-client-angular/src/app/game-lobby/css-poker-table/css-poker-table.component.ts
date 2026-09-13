import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule, CurrencyPipe, NgClass } from '@angular/common';
import { TableState, PlayerState } from '../../game/game-state.service';
import { SeatCard } from '../../game/game-models';
import { CardSuit, CardValue } from '../../poker/poker-models';

const MAX_SEATS = 9;
const MAX_COMMUNITY_CARDS = 5;

const SUIT_CSS_CLASS: Record<CardSuit, string> = {
  [CardSuit.HEART]: 'suit-heart',
  [CardSuit.DIAMOND]: 'suit-diamond',
  [CardSuit.CLUB]: 'suit-club',
  [CardSuit.SPADE]: 'suit-spade',
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

/**
 * CSS class for each seat position (1-indexed, matching server wire format).
 * Position 1 = hero (bottom center-left), going clockwise.
 * Array index = seatPosition - 1.
 */
const SEAT_POSITION_CLASSES: string[] = [
  'seat-bottom-left',    // seat 1: hero (bottom center-left)
  'seat-left-lower',     // seat 2: bottom-left
  'seat-left-upper',     // seat 3: left-lower
  'seat-top-left',       // seat 4: left-upper / top-left
  'seat-top-center',     // seat 5: top center
  'seat-top-right',      // seat 6: top-right
  'seat-right-upper',    // seat 7: right-upper
  'seat-right-lower',    // seat 8: right-lower
  'seat-bottom-right',   // seat 9: bottom-right
];

interface CardViewModel {
  rank: string;
  suitClass: string;
  visible: boolean;
}

interface SeatPlayerViewModel {
  displayName: string;
  initial: string;
  chipCount: number;
}

interface SeatViewModel {
  positionClass: string;
  player: SeatPlayerViewModel | null;
  cards: CardViewModel[];
  isActive: boolean;
  isDealer: boolean;
  currentBet: number;
}

/**
 * Dealer button positions on the rail for each seat position (1-indexed).
 * Values are [top%, left%] placing the button on the table edge
 * between the seat and center, with offset to avoid overlap.
 * Array index = seatPosition - 1.
 */
const DEALER_BUTTON_POSITIONS: [number, number][] = [
  [88, 38],   // seat 1: bottom-left (hero)
  [78, 8],    // seat 2: left-lower
  [25, 8],    // seat 3: left-upper
  [8, 28],    // seat 4: top-left
  [5, 50],    // seat 5: top center
  [8, 72],    // seat 6: top-right
  [25, 92],   // seat 7: right-upper
  [78, 92],   // seat 8: right-lower
  [88, 62],   // seat 9: bottom-right
];

@Component({
  selector: 'app-css-poker-table',
  standalone: true,
  imports: [CommonModule, CurrencyPipe, NgClass],
  templateUrl: './css-poker-table.component.html',
  styleUrl: './css-poker-table.component.css',
})
export class CssPokerTableComponent implements OnChanges {
  @Input({ required: true }) tableState!: TableState;
  @Input({ required: true }) players!: PlayerState[];
  @Input() currentUserId = '';

  communityCardSlots: (CardViewModel | null)[] = [];
  potViewModels: { label: string; amount: number }[] = [];
  seatViewModels: SeatViewModel[] = [];
  dealerButtonStyle: { top: string; left: string } | null = null;

  ngOnChanges(): void {
    this.buildCommunityCards();
    this.buildPot();
    this.buildSeats();
    this.buildDealerButton();
  }

  private buildCommunityCards(): void {
    const cards = this.tableState?.communityCards ?? [];
    this.communityCardSlots = [];
    for (let i = 0; i < MAX_COMMUNITY_CARDS; i++) {
      if (i < cards.length) {
        const card = cards[i];
        this.communityCardSlots.push({
          rank: RANK_LABELS[card.value] ?? '?',
          suitClass: SUIT_CSS_CLASS[card.suit] ?? '',
          visible: true,
        });
      } else {
        this.communityCardSlots.push(null);
      }
    }
  }

  private buildPot(): void {
    const pots = this.tableState?.pots ?? [];
    this.potViewModels = pots
      .filter(p => p.amount > 0)
      .map((p, i, arr) => ({
        label: arr.length === 1 ? 'Pot' : i === 0 ? 'Main' : `Side ${i}`,
        amount: p.amount / 100,
      }));
  }

  private buildSeats(): void {
    const ts = this.tableState;
    const players = this.players ?? [];

    const seatPlayerMap = new Map<number, PlayerState>();
    for (const p of players) {
      if (p.seatPosition != null) {
        seatPlayerMap.set(p.seatPosition, p);
      }
    }

    this.seatViewModels = [];
    // Seat positions are 1-indexed: 1..MAX_SEATS
    for (let pos = 1; pos <= MAX_SEATS; pos++) {
      const player = seatPlayerMap.get(pos);
      const seatCards = ts?.seatCards.get(pos) ?? null;
      const isActive = ts?.actionPosition === pos;
      const isDealer = ts?.dealerPosition === pos;
      const summary = ts?.seatSummaries.get(pos) ?? null;
      const currentBet = (summary?.currentBetAmount ?? 0) / 100;

      if (player) {
        const displayName = player.userId === this.currentUserId
          ? 'You'
          : player.displayName;

        this.seatViewModels.push({
          positionClass: SEAT_POSITION_CLASSES[pos - 1],
          player: {
            displayName: displayName.length > 10 ? displayName.substring(0, 9) + '\u2026' : displayName,
            initial: displayName.charAt(0).toUpperCase(),
            chipCount: player.chipCount / 100,
          },
          cards: this.buildHoleCards(seatCards, player.userId === this.currentUserId),
          isActive,
          isDealer,
          currentBet,
        });
      } else {
        this.seatViewModels.push({
          positionClass: SEAT_POSITION_CLASSES[pos - 1],
          player: null,
          cards: [],
          isActive: false,
          isDealer: false,
          currentBet: 0,
        });
      }
    }
  }

  private buildDealerButton(): void {
    const pos = this.tableState?.dealerPosition ?? null;
    if (pos != null && pos >= 1 && pos <= MAX_SEATS) {
      const [top, left] = DEALER_BUTTON_POSITIONS[pos - 1];
      this.dealerButtonStyle = { top: `${top}%`, left: `${left}%` };
    } else {
      this.dealerButtonStyle = null;
    }
  }

  private buildHoleCards(seatCards: SeatCard[] | null, isCurrentUser: boolean): CardViewModel[] {
    if (!seatCards || seatCards.length === 0) return [];

    return seatCards.map(sc => {
      if (sc.showCard || isCurrentUser) {
        return {
          rank: RANK_LABELS[sc.card.value] ?? '?',
          suitClass: SUIT_CSS_CLASS[sc.card.suit] ?? '',
          visible: true,
        };
      }
      return { rank: '', suitClass: '', visible: false };
    });
  }
}
