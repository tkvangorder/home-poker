import { Component, Input } from '@angular/core';

import { PhaserTableComponent } from '../phaser-table/phaser-table.component';
import { TableState, PlayerState } from '../../game/game-state.service';
import { GameStatus } from '../../game/game-models';

@Component({
  selector: 'app-table-view',
  imports: [PhaserTableComponent],
  templateUrl: './table-view.component.html',
  styleUrl: './table-view.component.css',
})
export class TableViewComponent {
  @Input() tableState: TableState | null = null;
  @Input() players: PlayerState[] = [];
  @Input() gameStatus: GameStatus | null = null;
  @Input() currentUserId: string = '';

  get showOverlay(): boolean {
    return this.gameStatus === 'PAUSED' || this.gameStatus === 'BALANCING';
  }

  get overlayText(): string {
    if (this.gameStatus === 'PAUSED') return 'Game Paused';
    if (this.gameStatus === 'BALANCING') return 'Rebalancing Tables...';
    return '';
  }
}
