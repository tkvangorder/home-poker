import { Card } from '../../../poker/poker-models';
import { SeatCard } from '../../../game/game-models';

export interface CardHighlightTargets {
  /** Indices into the community-card row to highlight. */
  communityIndices: number[];
  /** Indices (0/1) into the winner's hole cards to highlight. */
  holeSeatCardIndices: number[];
}

function sameCard(a: Card, b: Card): boolean {
  return a.value === b.value && a.suit === b.suit;
}

/**
 * Resolve a winner's `winningCards` to the on-table sprites that should glow.
 * Each card (value + suit) is unique, so it lands in at most one place: the
 * shared board, or the winner's hole cards. Cards that match nothing are skipped.
 */
export function matchWinningCards(
  winningCards: Card[],
  communityCards: Card[],
  holeCards: SeatCard[] | null,
): CardHighlightTargets {
  const communityIndices: number[] = [];
  const holeSeatCardIndices: number[] = [];

  for (const wc of winningCards) {
    const ci = communityCards.findIndex((c) => sameCard(c, wc));
    if (ci >= 0) {
      if (!communityIndices.includes(ci)) communityIndices.push(ci);
      continue;
    }
    if (holeCards) {
      const hi = holeCards.findIndex((sc) => sameCard(sc.card, wc));
      if (hi >= 0 && !holeSeatCardIndices.includes(hi)) holeSeatCardIndices.push(hi);
    }
  }

  return { communityIndices, holeSeatCardIndices };
}
