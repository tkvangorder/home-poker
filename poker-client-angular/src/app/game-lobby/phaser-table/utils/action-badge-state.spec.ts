import { computeBadgeState, BadgeState } from './action-badge-state';
import { TableState } from '../../../game/game-state.service';
import { SeatSummary } from '../../../game/game-events';

function makeTableState(overrides: Partial<TableState> = {}): TableState {
  return {
    tableId: 't1',
    tableStatus: 'PLAYING',
    handNumber: 1,
    dealerPosition: 1,
    smallBlindPosition: 2,
    bigBlindPosition: 3,
    smallBlindAmount: 5,
    bigBlindAmount: 10,
    communityCards: [],
    pots: [],
    potTotal: 0,
    phase: 'PRE_FLOP_BETTING',
    potResults: null,
    seatCards: new Map(),
    seatSummaries: new Map<number, SeatSummary>(),
    lastAction: null,
    actionPosition: null,
    actionDeadline: null,
    callAmount: 0,
    currentBet: 0,
    minimumRaise: 0,
    actionSeq: 0,
    ...overrides,
  };
}

function summary(
  seatPosition: number,
  status: SeatSummary['status'],
  chipCount: number,
  currentBetAmount: number,
): SeatSummary {
  return { seatPosition, userId: `u${seatPosition}`, status, chipCount, currentBetAmount };
}

describe('computeBadgeState', () => {
  it('returns none for a null table state', () => {
    expect(computeBadgeState(1, null).kind).toBe('none');
  });

  it('returns none for a seat that is not the most recent actor', () => {
    const ts = makeTableState({
      actionPosition: 2,
      lastAction: { seatPosition: 3, action: 'call' },
      actionSeq: 1,
    });
    expect(computeBadgeState(5, ts).kind).toBe('none');
  });

  it('returns none for the seat on the clock — the pod glow conveys "your turn"', () => {
    const ts = makeTableState({ actionPosition: 2, lastAction: null });
    expect(computeBadgeState(2, ts).kind).toBe('none');
  });

  it('still returns none for the action position when another seat acted last', () => {
    const ts = makeTableState({
      actionPosition: 2,
      lastAction: { seatPosition: 5, action: 'call' },
      actionSeq: 3,
    });
    expect(computeBadgeState(2, ts).kind).toBe('none');
  });

  it('shows the action for a seat that is both actionPosition and the most recent actor', () => {
    // This brief window exists between `player-acted` and the next `action-on-player`:
    // actionPosition still points at the seat that just acted.
    const ts = makeTableState({
      actionPosition: 5,
      lastAction: { seatPosition: 5, action: 'fold' },
      actionSeq: 2,
    });
    expect(computeBadgeState(5, ts).kind).toBe('fold');
  });

  it('returns fold for the seat that just folded', () => {
    const ts = makeTableState({
      actionPosition: 3,
      lastAction: { seatPosition: 5, action: 'fold' },
      actionSeq: 4,
    });
    expect(computeBadgeState(5, ts)).toEqual<BadgeState>({
      kind: 'fold', line1: 'FOLD', line2: '', actionSeq: 4,
    });
  });

  it('returns check for the seat that just checked', () => {
    const ts = makeTableState({
      lastAction: { seatPosition: 5, action: 'check' },
      actionSeq: 7,
    });
    expect(computeBadgeState(5, ts).kind).toBe('check');
    expect(computeBadgeState(5, ts).line1).toBe('CHECK');
  });

  it('returns call with the chips spent for the seat that just called', () => {
    // Seat 5 had 100 chips, currentBetAmount 0. Now has 95 chips, currentBetAmount 5.
    const ts = makeTableState({
      lastAction: { seatPosition: 5, action: 'call' },
      actionSeq: 8,
      seatSummaries: new Map([[5, summary(5, 'ACTIVE', 95, 5)]]),
    });
    expect(computeBadgeState(5, ts)).toMatchObject({
      kind: 'call', line1: 'CALL $0.05',
    });
  });

  it('returns bet with the total bet amount', () => {
    const ts = makeTableState({
      lastAction: { seatPosition: 5, action: 'bet' },
      currentBet: 20,
      actionSeq: 9,
      seatSummaries: new Map([[5, summary(5, 'ACTIVE', 80, 20)]]),
    });
    expect(computeBadgeState(5, ts)).toMatchObject({
      kind: 'bet', line1: 'BET $0.20',
    });
  });

  it('returns raise with the new total bet amount', () => {
    const ts = makeTableState({
      lastAction: { seatPosition: 5, action: 'raise' },
      currentBet: 50,
      actionSeq: 10,
      seatSummaries: new Map([[5, summary(5, 'ACTIVE', 50, 50)]]),
    });
    expect(computeBadgeState(5, ts)).toMatchObject({
      kind: 'raise', line1: 'RAISE → $0.50',
    });
  });

  it('upgrades to all-in when the resulting status is ALL_IN', () => {
    const ts = makeTableState({
      lastAction: { seatPosition: 5, action: 'raise' },
      currentBet: 80,
      actionSeq: 11,
      seatSummaries: new Map([[5, summary(5, 'ALL_IN', 0, 80)]]),
    });
    expect(computeBadgeState(5, ts).kind).toBe('all-in');
    expect(computeBadgeState(5, ts).line1).toBe('ALL IN');
  });

  it('upgrades to all-in when a call shoves the stack', () => {
    const ts = makeTableState({
      lastAction: { seatPosition: 5, action: 'call' },
      actionSeq: 12,
      seatSummaries: new Map([[5, summary(5, 'ALL_IN', 0, 50)]]),
    });
    expect(computeBadgeState(5, ts).kind).toBe('all-in');
    expect(computeBadgeState(5, ts).line1).toBe('ALL IN');
  });

  it('upgrades to all-in when a bet shoves the stack', () => {
    const ts = makeTableState({
      lastAction: { seatPosition: 5, action: 'bet' },
      currentBet: 30,
      actionSeq: 13,
      seatSummaries: new Map([[5, summary(5, 'ALL_IN', 0, 30)]]),
    });
    expect(computeBadgeState(5, ts).kind).toBe('all-in');
    expect(computeBadgeState(5, ts).line1).toBe('ALL IN');
  });
});
