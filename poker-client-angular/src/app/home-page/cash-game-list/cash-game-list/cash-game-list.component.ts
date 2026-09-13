import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CashGameService } from '../../../game/cash-game.service';
import { CashGameDetails, GameStatus } from '../../../game/game-models';
import { LangUtils } from '../../../lib/lang.utils';

@Component({
    selector: 'app-cash-game-list',
    imports: [CommonModule],
    templateUrl: './cash-game-list.component.html'
})
export class CashGameListComponent {
  private cashGameService = inject(CashGameService);

  games$ = this.cashGameService.getGames();
  selectedGame$ = this.cashGameService.getSelectedGame();

  selectGame(game: CashGameDetails): void {
    this.cashGameService.selectGame(game);
  }

  formatBlinds(game: CashGameDetails): string {
    return `${LangUtils.formatCurrency(game.smallBlind)}/${LangUtils.formatCurrency(game.bigBlind)}`;
  }

  formatBuyIn(game: CashGameDetails): string {
    return LangUtils.formatCurrency(game.maxBuyIn);
  }

  statusBadgeClass(status: GameStatus): string {
    switch (status) {
      case 'ACTIVE':
      case 'SEATING':
        return 'badge-success';
      case 'SCHEDULED':
        return 'badge-info';
      case 'PAUSED':
      case 'BALANCING':
        return 'badge-warning';
      case 'COMPLETED':
        return 'badge-ghost';
      default:
        return 'badge-ghost';
    }
  }

  statusLabel(status: GameStatus): string {
    return status.charAt(0) + status.slice(1).toLowerCase();
  }
}
