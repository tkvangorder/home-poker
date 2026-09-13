import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { PlayerAction } from '../../game/game-commands';
import { TableState } from '../../game/game-state.service';
import { LangUtils } from '../../lib/lang.utils';

export type StepUnit = 'BB' | 'SB';
export const STEP_UNIT_STORAGE_KEY = 'pokerActionPanel.stepUnit';

@Component({
  selector: 'app-action-panel',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './action-panel.component.html',
})
export class ActionPanelComponent implements OnChanges, OnInit {
  @Input() tableState: TableState | null = null;
  @Input() playerChipCount: number = 0;
  @Input() seatPosition: number | null = null;

  @Output() playerAction = new EventEmitter<PlayerAction>();

  betAmount: number = 0;
  minBet: number = 0;
  maxBet: number = 0;
  step: number = 0;
  stepUnit: StepUnit = 'BB';

  ngOnInit(): void {
    const saved = this.readStoredStepUnit();
    if (saved) this.stepUnit = saved;
  }

  get isMyTurn(): boolean {
    return (
      this.tableState != null &&
      this.seatPosition != null &&
      this.tableState.actionPosition === this.seatPosition
    );
  }

  /** True when this seat owes chips to match the current bet. False when the seat has already
   *  matched (e.g. BB after SB calls) — in that case Check is the correct action. */
  get hasBetToCall(): boolean {
    return this.callAmount > 0;
  }

  get callAmount(): number {
    return this.tableState?.callAmount ?? 0;
  }

  /** Chips the player will actually spend to call — capped to their stack so a short stack
   *  goes all-in instead of trying to commit more chips than they have. */
  get effectiveCallAmount(): number {
    return Math.min(this.callAmount, this.playerChipCount);
  }

  /** True when the player has enough chips to make a legal bet/raise (even an undersized
   *  all-in shove). When facing a bet, they need at least 1 chip beyond the call; when no
   *  bet is open, any chips at all suffice. */
  get canSubmitBet(): boolean {
    if (this.hasOpenBet) {
      return this.playerChipCount > this.callAmount;
    }
    return this.playerChipCount > 0;
  }

  /** True when there is an outstanding bet on the table (open or matched). Discriminates
   *  Raise from Bet — independent of whether *this* seat still owes chips. */
  get hasOpenBet(): boolean {
    return (this.tableState?.currentBet ?? 0) > 0;
  }

  get totalPot(): number {
    if (!this.tableState?.pots) return 0;
    return this.tableState.pots.reduce((sum, p) => sum + p.amount, 0);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['tableState'] || changes['playerChipCount']) {
      const prevTs = changes['tableState']?.previousValue as TableState | null | undefined;
      const wasMyTurn = prevTs?.actionPosition === this.seatPosition;
      this.recalculateBetRange();
      if (this.isMyTurn && !wasMyTurn) {
        this.betAmount = this.defaultBetAmount;
      }
    }
  }

  fold(): void {
    this.playerAction.emit({ type: 'fold' });
  }

  checkOrCall(): void {
    if (this.hasBetToCall) {
      this.playerAction.emit({ type: 'call', amount: this.effectiveCallAmount });
    } else {
      this.playerAction.emit({ type: 'check' });
    }
  }

  submitBet(): void {
    if (this.hasOpenBet) {
      this.playerAction.emit({ type: 'raise', amount: this.betAmount });
    } else {
      this.playerAction.emit({ type: 'bet', amount: this.betAmount });
    }
  }

  setMin(): void {
    this.betAmount = this.minBet;
  }

  setHalfPot(): void {
    this.betAmount = this.clampBet(Math.floor(this.totalPot / 2));
  }

  setFullPot(): void {
    this.betAmount = this.clampBet(this.totalPot);
  }

  setMax(): void {
    this.betAmount = this.maxBet;
  }

  bumpBet(direction: 1 | -1): void {
    const bb = this.tableState?.bigBlindAmount || 1;
    this.betAmount = this.clampBet(this.betAmount + direction * bb);
  }

  toggleStepUnit(): void {
    this.stepUnit = this.stepUnit === 'BB' ? 'SB' : 'BB';
    this.persistStepUnit(this.stepUnit);
    this.recalculateBetRange();
  }

  formatCents(cents: number): string {
    return LangUtils.formatCurrency(cents);
  }

  private recalculateBetRange(): void {
    const ts = this.tableState;
    if (!ts) return;

    const blind = this.stepUnit === 'BB' ? ts.bigBlindAmount : ts.smallBlindAmount;
    this.step = blind || 1;

    // The wire-level bet/raise amount is the total "to" level for this round, not the increment.
    // For a raise this means `currentBet + minimumRaise`; the player's chip cost is whatever
    // they haven't already committed this round (their seat's currentBetAmount). The all-in
    // ceiling is therefore stack + chips already on the felt.
    const mySeatCommitted = this.mySeatCurrentBetAmount;
    if (this.hasOpenBet) {
      const minRaiseIncrement = ts.minimumRaise || ts.bigBlindAmount || 1;
      this.minBet = ts.currentBet + minRaiseIncrement;
    } else {
      this.minBet = ts.bigBlindAmount || 1;
    }

    this.maxBet = this.playerChipCount + mySeatCommitted;
    this.minBet = Math.min(this.minBet, this.maxBet);
    this.betAmount = this.clampBet(this.betAmount || this.defaultBetAmount);
  }

  /** Default seed for the bet/raise input — the smallest legal action, clamped to the player's
   *  stack so a short stack auto-shoves rather than producing an illegal value. */
  private get defaultBetAmount(): number {
    return this.minBet;
  }

  private get mySeatCurrentBetAmount(): number {
    if (this.seatPosition == null) return 0;
    return this.tableState?.seatSummaries.get(this.seatPosition)?.currentBetAmount ?? 0;
  }

  private clampBet(value: number): number {
    return Math.max(this.minBet, Math.min(value, this.maxBet));
  }

  private readStoredStepUnit(): StepUnit | null {
    try {
      const value = localStorage.getItem(STEP_UNIT_STORAGE_KEY);
      return value === 'BB' || value === 'SB' ? value : null;
    } catch {
      return null;
    }
  }

  private persistStepUnit(unit: StepUnit): void {
    try {
      localStorage.setItem(STEP_UNIT_STORAGE_KEY, unit);
    } catch {
      // ignore persistence failures (e.g. storage quota, private mode)
    }
  }
}
