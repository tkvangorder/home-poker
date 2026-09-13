import { BehaviorSubject } from 'rxjs';
import { TableState, PlayerState } from '../../game/game-state.service';

export class PhaserBridgeService {
  readonly tableState$ = new BehaviorSubject<TableState | null>(null);
  readonly players$ = new BehaviorSubject<PlayerState[]>([]);
  readonly currentUserId$ = new BehaviorSubject<string>('');

  updateTableState(state: TableState | null): void {
    this.tableState$.next(state);
  }

  updatePlayers(players: PlayerState[]): void {
    this.players$.next(players);
  }

  updateCurrentUserId(userId: string): void {
    this.currentUserId$.next(userId);
  }

  destroy(): void {
    this.tableState$.complete();
    this.players$.complete();
    this.currentUserId$.complete();
  }
}
