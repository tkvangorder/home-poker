import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PlayerState } from '../../game/game-state.service';
import { LangUtils } from '../../lib/lang.utils';

@Component({
  selector: 'app-leaderboard-panel',
  imports: [CommonModule, FormsModule],
  templateUrl: './leaderboard-panel.component.html',
})
export class LeaderboardPanelComponent {
  @Input() players: PlayerState[] = [];
  @Input() currentUserId: string = '';
  @Input() showBuyIn = true;
  @Input() maxBuyIn: number = 0;
  @Output() buyIn = new EventEmitter<number>();

  buyInAmount: number | null = null;

  get isRegistered(): boolean {
    return this.players.some(p => p.userId === this.currentUserId);
  }

  get currentPlayerChipCount(): number {
    return this.players.find(p => p.userId === this.currentUserId)?.chipCount ?? 0;
  }

  get remainingBuyIn(): number {
    if (this.maxBuyIn <= 0) return 0;
    return Math.max(0, this.maxBuyIn - this.currentPlayerChipCount);
  }

  get remainingBuyInDollars(): number {
    return this.remainingBuyIn / 100;
  }

  get isBuyInValid(): boolean {
    if (!this.buyInAmount || this.buyInAmount <= 0) return false;
    if (this.maxBuyIn > 0 && this.buyInAmount > this.remainingBuyInDollars) return false;
    return true;
  }

  setMaxBuyIn(): void {
    this.buyInAmount = this.remainingBuyInDollars;
  }


  onBuyIn(): void {
    if (this.buyInAmount && this.buyInAmount > 0) {
      this.buyIn.emit(this.buyInAmount);
      this.buyInAmount = null;
    }
  }

  formatChips(cents: number): string {
    return LangUtils.formatCurrency(cents);
  }
}
