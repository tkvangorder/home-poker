import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { GameStateService, PlayerState, TableState } from '../game/game-state.service';
import { GameWebSocketService, ConnectionStatus } from '../game/game-websocket.service';
import { UserService } from '../user/user-service';
import { GameStatus } from '../game/game-models';
import { PokerRestClient } from '../rest/poker-rest-client';
import { LangUtils } from '../lib/lang.utils';
import { PlayerAction } from '../game/game-commands';
import { LeaderboardPanelComponent } from './leaderboard-panel/leaderboard-panel.component';
import { MessagesPanelComponent } from './messages-panel/messages-panel.component';
import { TableViewComponent } from './table-view/table-view.component';
import { ActionPanelComponent } from './action-panel/action-panel.component';
import { Observable, combineLatest, map, distinctUntilChanged } from 'rxjs';

type ActiveTab = 'table' | 'leaderboard' | 'messages';

@Component({
  selector: 'app-game-lobby',
  imports: [CommonModule, FormsModule, LeaderboardPanelComponent, MessagesPanelComponent, TableViewComponent, ActionPanelComponent],
  templateUrl: './game-lobby.component.html',
})
export class GameLobbyComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private gameStateService = inject(GameStateService);
  private webSocketService = inject(GameWebSocketService);
  private userService = inject(UserService);
  private pokerClient = inject(PokerRestClient);

  gameId = '';
  maxBuyIn = 0;
  gameStatus$ = this.gameStateService.getGameStatus();
  players$ = this.gameStateService.getPlayers();
  tables$ = this.gameStateService.getTables();
  messages$ = this.gameStateService.getMessages();
  connectionStatus$ = this.webSocketService.getConnectionStatus();

  activeTab: ActiveTab = 'leaderboard';
  selectedTableId: string | null = null;

  myTableId$!: Observable<string | null>;
  myTableState$!: Observable<TableState | null>;
  myChipCount$!: Observable<number>;
  mySeatPosition$!: Observable<number | null>;

  get isAdmin(): boolean {
    const user = this.userService.getCurrentUser();
    return user?.roles?.includes('ADMIN') ?? false;
  }

  get currentUserId(): string {
    return this.userService.getCurrentUser()?.id ?? '';
  }

  ngOnInit(): void {
    this.gameId = this.route.snapshot.paramMap.get('gameId') ?? '';
    if (this.gameId) {
      this.gameStateService.connectToGame(this.gameId);
      this.pokerClient.getGame(this.gameId).subscribe(details => {
        this.maxBuyIn = details.maxBuyIn;
      });
    }

    this.myTableId$ = this.players$.pipe(
      map(players => players.get(this.currentUserId)?.tableId ?? null),
      distinctUntilChanged()
    );

    this.myTableState$ = combineLatest([this.tables$, this.myTableId$]).pipe(
      map(([tables, myTableId]) => {
        const tableId = this.selectedTableId ?? myTableId;
        return tableId ? (tables.get(tableId) ?? null) : null;
      })
    );

    this.myChipCount$ = this.players$.pipe(
      map(players => players.get(this.currentUserId)?.chipCount ?? 0),
      distinctUntilChanged()
    );

    this.mySeatPosition$ = this.players$.pipe(
      map(players => players.get(this.currentUserId)?.seatPosition ?? null),
      distinctUntilChanged()
    );
  }

  ngOnDestroy(): void {
    this.gameStateService.disconnect();
  }

  isActiveGame(status: GameStatus | null): boolean {
    return status === 'ACTIVE' || status === 'BALANCING' || status === 'PAUSED';
  }

  isPreGame(status: GameStatus | null): boolean {
    return status === 'SCHEDULED' || status === 'SEATING' || status === null;
  }

  sendGameCommand(commandId: string): void {
    this.webSocketService.sendCommand({
      commandId: commandId as any,
      gameId: this.gameId,
    });
  }


  buyIn(amount: number): void {
    this.webSocketService.sendCommand({
      commandId: 'buy-in',
      gameId: this.gameId,
      amount: LangUtils.asCents(amount),
    });
  }

  sendPlayerAction(action: PlayerAction, tableId: string): void {
    this.webSocketService.sendCommand({
      commandId: 'player-action-command',
      gameId: this.gameId,
      tableId,
      action,
    });
  }

  leaveGame(): void {
    this.webSocketService.sendCommand({
      commandId: 'leave-game',
      gameId: this.gameId,
    });
    this.router.navigate(['/home']);
  }

  selectTable(tableId: string): void {
    this.selectedTableId = tableId;
  }

  setActiveTab(tab: ActiveTab): void {
    this.activeTab = tab;
  }

  getPlayersArray(players: Map<string, PlayerState> | null): PlayerState[] {
    if (!players) return [];
    return Array.from(players.values()).sort((a, b) => b.chipCount - a.chipCount);
  }

  getTablesArray(tables: Map<string, TableState>): TableState[] {
    return Array.from(tables.values());
  }

  formatChips(cents: number): string {
    return LangUtils.formatCurrency(cents);
  }

  getStatusBadgeClass(status: GameStatus | null): string {
    switch (status) {
      case 'ACTIVE': return 'badge-success';
      case 'PAUSED': return 'badge-warning';
      case 'SEATING': return 'badge-info';
      case 'SCHEDULED': return 'badge-neutral';
      case 'COMPLETED': return 'badge-ghost';
      default: return 'badge-neutral';
    }
  }

  getConnectionBadgeClass(status: ConnectionStatus): string {
    switch (status) {
      case 'connected': return 'badge-success';
      case 'connecting': return 'badge-warning';
      case 'error': return 'badge-error';
      default: return 'badge-neutral';
    }
  }
}
