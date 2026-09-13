import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SimpleChange } from '@angular/core';

import {
  ActionPanelComponent,
  STEP_UNIT_STORAGE_KEY,
} from './action-panel.component';
import { TableState } from '../../game/game-state.service';
import { PlayerAction } from '../../game/game-commands';

function tableState(overrides: Partial<TableState>): TableState {
  return {
    tableId: 't',
    tableStatus: 'PLAYING',
    handNumber: 1,
    dealerPosition: 1,
    smallBlindPosition: 2,
    bigBlindPosition: 3,
    smallBlindAmount: 25,
    bigBlindAmount: 50,
    communityCards: [],
    pots: [],
    potTotal: 0,
    phase: 'PRE_FLOP_BETTING',
    potResults: null,
    seatCards: new Map(),
    seatSummaries: new Map(),
    lastAction: null,
    actionPosition: 3,
    actionDeadline: null,
    callAmount: 0,
    currentBet: 50,
    minimumRaise: 50,
    ...overrides,
  };
}

function setInputs(
  fixture: ComponentFixture<ActionPanelComponent>,
  cmp: ActionPanelComponent,
  ts: TableState,
  chips: number,
  seat: number
): void {
  cmp.tableState = ts;
  cmp.playerChipCount = chips;
  cmp.seatPosition = seat;
  cmp.ngOnChanges({
    tableState: new SimpleChange(null, ts, true),
    playerChipCount: new SimpleChange(null, chips, true),
  });
  fixture.detectChanges();
}

describe('ActionPanelComponent', () => {
  let fixture: ComponentFixture<ActionPanelComponent>;
  let cmp: ActionPanelComponent;
  let emitted: PlayerAction[];

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [ActionPanelComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(ActionPanelComponent);
    cmp = fixture.componentInstance;
    emitted = [];
    cmp.playerAction.subscribe((a) => emitted.push(a));
  });

  describe('BB option to check after SB calls (the reported bug)', () => {
    it('hasBetToCall is false when this seat already matched the bet', () => {
      const ts = tableState({ currentBet: 50, callAmount: 0, actionPosition: 3 });
      setInputs(fixture, cmp, ts, 1000, 3);
      expect(cmp.hasBetToCall).toBe(false);
    });

    it('still labels the second action button "Raise" because there is an open bet', () => {
      const ts = tableState({ currentBet: 50, callAmount: 0, actionPosition: 3 });
      setInputs(fixture, cmp, ts, 1000, 3);
      expect(cmp.hasOpenBet).toBe(true);
    });

    it('emits a check action when the call button is clicked', () => {
      const ts = tableState({ currentBet: 50, callAmount: 0, actionPosition: 3 });
      setInputs(fixture, cmp, ts, 1000, 3);
      cmp.checkOrCall();
      expect(emitted).toEqual([{ type: 'check' }]);
    });

    it('emits a raise (not a bet) when submitBet is clicked, since there is an open bet', () => {
      const ts = tableState({ currentBet: 50, callAmount: 0, actionPosition: 3 });
      setInputs(fixture, cmp, ts, 1000, 3);
      cmp.betAmount = 100;
      cmp.submitBet();
      expect(emitted).toEqual([{ type: 'raise', amount: 100 }]);
    });
  });

  describe('Outstanding call', () => {
    it('emits a call with the seat-specific amount, not the table currentBet', () => {
      const ts = tableState({ currentBet: 50, callAmount: 25, actionPosition: 2 });
      setInputs(fixture, cmp, ts, 1000, 2);
      expect(cmp.hasBetToCall).toBe(true);
      expect(cmp.callAmount).toBe(25);
      cmp.checkOrCall();
      expect(emitted).toEqual([{ type: 'call', amount: 25 }]);
    });
  });

  describe('No bet on table', () => {
    it('shows Check + Bet (not Raise) and emits bet on submit', () => {
      const ts = tableState({
        phase: 'FLOP_BETTING',
        currentBet: 0,
        callAmount: 0,
        minimumRaise: 50,
        actionPosition: 3,
      });
      setInputs(fixture, cmp, ts, 1000, 3);
      expect(cmp.hasBetToCall).toBe(false);
      expect(cmp.hasOpenBet).toBe(false);
      cmp.checkOrCall();
      expect(emitted).toEqual([{ type: 'check' }]);

      emitted.length = 0;
      cmp.betAmount = 200;
      cmp.submitBet();
      expect(emitted).toEqual([{ type: 'bet', amount: 200 }]);
    });
  });

  describe('preset bet sizing', () => {
    it('Min snaps to the minimum legal raise-to amount (currentBet + minimumRaise)', () => {
      const ts = tableState({ currentBet: 50, callAmount: 25, minimumRaise: 100 });
      setInputs(fixture, cmp, ts, 1000, 2);
      cmp.setMin();
      expect(cmp.betAmount).toBe(150);
    });

    it('½ Pot snaps to half the total pot, clamped to legal range', () => {
      const ts = tableState({
        phase: 'FLOP_BETTING',
        currentBet: 0,
        callAmount: 0,
        pots: [{ amount: 400, seatPositions: [2, 3] }],
        minimumRaise: 50,
      });
      setInputs(fixture, cmp, ts, 1000, 3);
      cmp.setHalfPot();
      expect(cmp.betAmount).toBe(200);
    });

    it('Pot snaps to the full pot, clamped to legal range', () => {
      const ts = tableState({
        phase: 'FLOP_BETTING',
        currentBet: 0,
        callAmount: 0,
        pots: [{ amount: 300, seatPositions: [2, 3] }],
        minimumRaise: 50,
      });
      setInputs(fixture, cmp, ts, 1000, 3);
      cmp.setFullPot();
      expect(cmp.betAmount).toBe(300);
    });

    it('Max snaps to the player chip count (all-in)', () => {
      const ts = tableState({ currentBet: 50, callAmount: 25, minimumRaise: 100 });
      setInputs(fixture, cmp, ts, 750, 2);
      cmp.setMax();
      expect(cmp.betAmount).toBe(750);
    });
  });

  describe('raise sizing', () => {
    it('the reported bug: blinds 25/50, preflop default raise is 100 (not 50)', () => {
      // BB has posted 50; UTG is first to act facing currentBet=50, minimumRaise=50.
      const ts = tableState({
        smallBlindAmount: 25,
        bigBlindAmount: 50,
        currentBet: 50,
        callAmount: 50,
        minimumRaise: 50,
        actionPosition: 4,
      });
      setInputs(fixture, cmp, ts, 1000, 4);
      expect(cmp.betAmount).toBe(100);
    });

    it('minimum raise-to is currentBet + minimumRaise', () => {
      const ts = tableState({
        currentBet: 200,
        callAmount: 200,
        minimumRaise: 100,
        actionPosition: 3,
      });
      setInputs(fixture, cmp, ts, 1000, 3);
      expect(cmp.minBet).toBe(300);
      expect(cmp.betAmount).toBe(300);
    });

    it('max raise-to includes chips already committed this round (BB all-in)', () => {
      // Big blind seat (3) has 50 already on the felt and 950 behind. All-in raise-to = 1000.
      const ts = tableState({
        currentBet: 50,
        callAmount: 0,
        minimumRaise: 50,
        actionPosition: 3,
        seatSummaries: new Map([
          [3, { seatPosition: 3, userId: 'u', status: 'ACTIVE' as const, chipCount: 950, currentBetAmount: 50 }],
        ]),
      });
      setInputs(fixture, cmp, ts, 950, 3);
      expect(cmp.maxBet).toBe(1000);
      cmp.setMax();
      expect(cmp.betAmount).toBe(1000);
    });

    it('collapses min raise to all-in when the player cannot make the min raise', () => {
      // Min legal raise-to would be 50 + 200 = 250; player only has 150 behind and nothing in.
      const ts = tableState({
        currentBet: 50,
        callAmount: 50,
        minimumRaise: 200,
        actionPosition: 3,
      });
      setInputs(fixture, cmp, ts, 150, 3);
      expect(cmp.maxBet).toBe(150);
      expect(cmp.minBet).toBe(150);
      expect(cmp.betAmount).toBe(150);
    });

    it('resets the default to the smallest legal raise when action transitions to this seat', () => {
      const tsOther = tableState({
        currentBet: 50,
        callAmount: 50,
        minimumRaise: 50,
        actionPosition: 2, // someone else acts
      });
      setInputs(fixture, cmp, tsOther, 1000, 3);

      cmp.betAmount = 777;
      const tsMine = tableState({
        currentBet: 50,
        callAmount: 50,
        minimumRaise: 50,
        actionPosition: 3,
      });
      cmp.tableState = tsMine;
      cmp.ngOnChanges({
        tableState: new SimpleChange(tsOther, tsMine, false),
      });
      expect(cmp.betAmount).toBe(100);
    });

    it("preserves the user's bet adjustment across event ticks during this seat's turn", () => {
      const tsMine = tableState({
        currentBet: 50,
        callAmount: 50,
        minimumRaise: 50,
        actionPosition: 3,
      });
      setInputs(fixture, cmp, tsMine, 1000, 3);
      cmp.betAmount = 250; // user adjusted

      const tsMine2 = tableState({
        currentBet: 50,
        callAmount: 50,
        minimumRaise: 50,
        actionPosition: 3,
      });
      cmp.tableState = tsMine2;
      cmp.ngOnChanges({
        tableState: new SimpleChange(tsMine, tsMine2, false),
      });
      expect(cmp.betAmount).toBe(250);
    });
  });

  describe('short stack vs current bet', () => {
    it('caps the call amount to the player stack when chips < callAmount', () => {
      const ts = tableState({ currentBet: 200, callAmount: 200, minimumRaise: 50, actionPosition: 3 });
      setInputs(fixture, cmp, ts, 75, 3);
      expect(cmp.effectiveCallAmount).toBe(75);
      cmp.checkOrCall();
      expect(emitted).toEqual([{ type: 'call', amount: 75 }]);
    });

    it('emits the full call amount when the player can cover it', () => {
      const ts = tableState({ currentBet: 200, callAmount: 200, minimumRaise: 50, actionPosition: 3 });
      setInputs(fixture, cmp, ts, 1000, 3);
      cmp.checkOrCall();
      expect(emitted).toEqual([{ type: 'call', amount: 200 }]);
    });

    it('disables the bet/raise button when chips do not exceed the call amount', () => {
      // Player has exactly the call amount — calling goes all-in, raising is impossible.
      const ts = tableState({ currentBet: 200, callAmount: 200, minimumRaise: 50, actionPosition: 3 });
      setInputs(fixture, cmp, ts, 200, 3);
      expect(cmp.canSubmitBet).toBe(false);

      // Even less — still can't raise.
      setInputs(fixture, cmp, ts, 75, 3);
      expect(cmp.canSubmitBet).toBe(false);
    });

    it('allows raising when the player has at least one chip beyond the call', () => {
      const ts = tableState({ currentBet: 200, callAmount: 200, minimumRaise: 50, actionPosition: 3 });
      setInputs(fixture, cmp, ts, 201, 3);
      expect(cmp.canSubmitBet).toBe(true);
    });

    it('allows betting whenever the player has any chips and no bet is open', () => {
      const ts = tableState({
        phase: 'FLOP_BETTING',
        currentBet: 0,
        callAmount: 0,
        minimumRaise: 50,
        actionPosition: 3,
      });
      setInputs(fixture, cmp, ts, 1, 3);
      expect(cmp.canSubmitBet).toBe(true);

      setInputs(fixture, cmp, ts, 0, 3);
      expect(cmp.canSubmitBet).toBe(false);
    });
  });

  describe('slider step unit toggle', () => {
    it('defaults to BB step', () => {
      const ts = tableState({ smallBlindAmount: 25, bigBlindAmount: 50 });
      setInputs(fixture, cmp, ts, 1000, 3);
      expect(cmp.stepUnit).toBe('BB');
      expect(cmp.step).toBe(50);
    });

    it('switches step to small blind on toggle', () => {
      const ts = tableState({ smallBlindAmount: 25, bigBlindAmount: 50 });
      setInputs(fixture, cmp, ts, 1000, 3);
      cmp.toggleStepUnit();
      expect(cmp.stepUnit).toBe('SB');
      expect(cmp.step).toBe(25);
    });

    it('persists the step unit choice across sessions', () => {
      const ts = tableState({ smallBlindAmount: 25, bigBlindAmount: 50 });
      setInputs(fixture, cmp, ts, 1000, 3);
      cmp.toggleStepUnit();
      expect(localStorage.getItem(STEP_UNIT_STORAGE_KEY)).toBe('SB');

      // New component instance — should pick up the saved preference.
      const fixture2 = TestBed.createComponent(ActionPanelComponent);
      const cmp2 = fixture2.componentInstance;
      cmp2.tableState = ts;
      cmp2.playerChipCount = 1000;
      cmp2.seatPosition = 3;
      fixture2.detectChanges();
      expect(cmp2.stepUnit).toBe('SB');
    });
  });
});
