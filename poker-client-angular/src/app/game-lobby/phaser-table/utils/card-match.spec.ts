import { matchWinningCards } from './card-match';
import { Card, CardSuit, CardValue } from '../../../poker/poker-models';
import { SeatCard } from '../../../game/game-models';

function c(value: CardValue, suit: CardSuit): Card {
  return { value, suit };
}
function hole(value: CardValue, suit: CardSuit): SeatCard {
  return { card: c(value, suit), showCard: true };
}

describe('matchWinningCards', () => {
  const board: Card[] = [
    c(CardValue.ACE, CardSuit.HEART),
    c(CardValue.KING, CardSuit.SPADE),
    c(CardValue.KING, CardSuit.CLUB),
    c(CardValue.SEVEN, CardSuit.DIAMOND),
    c(CardValue.TWO, CardSuit.SPADE),
  ];

  it('matches winning cards across the board and the winner hole cards', () => {
    const holeCards = [hole(CardValue.ACE, CardSuit.SPADE), hole(CardValue.ACE, CardSuit.DIAMOND)];
    // Best hand: AA (hole) + A-K-K (board) — two pair Aces & Kings.
    const winning = [
      c(CardValue.ACE, CardSuit.SPADE),
      c(CardValue.ACE, CardSuit.DIAMOND),
      c(CardValue.ACE, CardSuit.HEART),
      c(CardValue.KING, CardSuit.SPADE),
      c(CardValue.KING, CardSuit.CLUB),
    ];
    const result = matchWinningCards(winning, board, holeCards);
    expect(result.communityIndices.sort()).toEqual([0, 1, 2]);
    expect(result.holeSeatCardIndices.sort()).toEqual([0, 1]);
  });

  it('matches a hand that plays entirely from the board', () => {
    const holeCards = [hole(CardValue.THREE, CardSuit.CLUB), hole(CardValue.FOUR, CardSuit.CLUB)];
    const winning = [...board];
    const result = matchWinningCards(winning, board, holeCards);
    expect(result.communityIndices.sort()).toEqual([0, 1, 2, 3, 4]);
    expect(result.holeSeatCardIndices).toEqual([]);
  });

  it('returns empty targets for an empty winning-cards list (fold-win)', () => {
    const result = matchWinningCards([], board, [hole(CardValue.ACE, CardSuit.SPADE)]);
    expect(result.communityIndices).toEqual([]);
    expect(result.holeSeatCardIndices).toEqual([]);
  });

  it('skips a winning card that cannot be found on the table', () => {
    const winning = [c(CardValue.QUEEN, CardSuit.HEART)]; // not on board or in hole
    const result = matchWinningCards(winning, board, null);
    expect(result.communityIndices).toEqual([]);
    expect(result.holeSeatCardIndices).toEqual([]);
  });

  it('tolerates null hole cards', () => {
    const winning = [c(CardValue.KING, CardSuit.SPADE)];
    const result = matchWinningCards(winning, board, null);
    expect(result.communityIndices).toEqual([1]);
    expect(result.holeSeatCardIndices).toEqual([]);
  });
});
