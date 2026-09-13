import {
  buildShowdownStages,
  formatStageLine1,
  formatStageLine2,
  ShowdownStage,
} from './showdown-sequence';
import { PotResult } from '../../../game/game-events';
import { Card, CardSuit, CardValue } from '../../../poker/poker-models';

function card(value: CardValue, suit: CardSuit): Card {
  return { value, suit };
}

describe('buildShowdownStages', () => {
  it('returns a single unlabelled stage for a single-pot single-winner hand', () => {
    const pots: PotResult[] = [
      {
        potIndex: 0,
        potAmount: 50,
        winners: [
          {
            seatPosition: 5,
            userId: 'u5',
            amount: 50,
            handDescription: 'Pair of Aces',
            winningCards: [card(CardValue.ACE, CardSuit.SPADE)],
          },
        ],
      },
    ];
    const stages = buildShowdownStages(pots);
    expect(stages).toHaveLength(1);
    expect(stages[0]).toMatchObject<Partial<ShowdownStage>>({
      potIndex: 0,
      potLabel: '',
      seatPosition: 5,
      userId: 'u5',
      amount: 50,
      handDescription: 'Pair of Aces',
      isSplit: false,
    });
    expect(stages[0].winningCards).toHaveLength(1);
  });

  it('orders side pots ascending by amount, with the main pot last', () => {
    const pots: PotResult[] = [
      { potIndex: 0, potAmount: 100, winners: [w(1)] },
      { potIndex: 2, potAmount: 80, winners: [w(2)] },
      { potIndex: 1, potAmount: 20, winners: [w(3)] },
    ];
    const stages = buildShowdownStages(pots);
    // Side pots by amount ascending (20 then 80), then main pot (100) last.
    expect(stages.map((s) => s.potIndex)).toEqual([1, 2, 0]);
  });

  it('labels stages Main Pot / Side Pot N when there is more than one pot', () => {
    const pots: PotResult[] = [
      { potIndex: 0, potAmount: 100, winners: [w(1)] },
      { potIndex: 1, potAmount: 20, winners: [w(3)] },
    ];
    const stages = buildShowdownStages(pots);
    const byIndex = new Map(stages.map((s) => [s.potIndex, s.potLabel]));
    expect(byIndex.get(1)).toBe('Side Pot 1');
    expect(byIndex.get(0)).toBe('Main Pot');
  });

  it('expands a split pot into one stage per winner, flagged isSplit', () => {
    const pots: PotResult[] = [
      {
        potIndex: 0,
        potAmount: 100,
        winners: [w(3), w(7)],
      },
    ];
    const stages = buildShowdownStages(pots);
    expect(stages).toHaveLength(2);
    expect(stages.map((s) => s.seatPosition)).toEqual([3, 7]);
    expect(stages.every((s) => s.isSplit)).toBe(true);
  });

  it('defaults winningCards to an empty array when the field is absent', () => {
    const pots = [
      {
        potIndex: 0,
        potAmount: 50,
        winners: [
          { seatPosition: 1, userId: 'u1', amount: 50, handDescription: '' },
        ],
      },
    ] as unknown as PotResult[];
    const stages = buildShowdownStages(pots);
    expect(stages[0].winningCards).toEqual([]);
  });
});

describe('formatStageLine1', () => {
  const base: ShowdownStage = {
    potIndex: 0,
    potLabel: '',
    seatPosition: 5,
    userId: 'u5',
    amount: 480,
    handDescription: 'Two Pair',
    isSplit: false,
    winningCards: [],
  };

  it('formats name + amount for a single pot', () => {
    expect(formatStageLine1(base, 'BigSlick')).toBe('BigSlick wins $4.80');
  });

  it('prefixes the pot label when present', () => {
    expect(formatStageLine1({ ...base, potLabel: 'Side Pot 1' }, 'BigSlick')).toBe(
      'Side Pot 1 — BigSlick wins $4.80',
    );
  });

  it('annotates split pots', () => {
    expect(formatStageLine1({ ...base, isSplit: true }, 'BigSlick')).toBe(
      'BigSlick wins $4.80 (split)',
    );
  });
});

describe('formatStageLine2', () => {
  it('returns the hand description unchanged when short', () => {
    expect(
      formatStageLine2({ handDescription: 'Full House' } as ShowdownStage),
    ).toBe('Full House');
  });

  it('returns empty string when there is no description', () => {
    expect(formatStageLine2({ handDescription: '' } as ShowdownStage)).toBe('');
  });

  it('truncates very long descriptions with an ellipsis', () => {
    const long = 'Full House, Aces full of Kings and then some extra words';
    const out = formatStageLine2({ handDescription: long } as ShowdownStage);
    expect(out.length).toBeLessThanOrEqual(40);
    expect(out.endsWith('…')).toBe(true);
  });
});

// helper: minimal winner with required winningCards
function w(seatPosition: number) {
  return {
    seatPosition,
    userId: `u${seatPosition}`,
    amount: 10,
    handDescription: 'Pair',
    winningCards: [],
  };
}
