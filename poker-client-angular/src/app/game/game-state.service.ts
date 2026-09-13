import { inject, Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { distinctUntilChanged, filter, map, take } from 'rxjs/operators';
import { Card, CardSuit, CardValue } from '../poker/poker-models';
import { GameStatus, Pot, SeatStatus, TableStatus } from './game-models';
import { GameWebSocketService } from './game-websocket.service';
import { ToasterService } from '../toaster/toaster.service';
import { LangUtils } from '../lib/lang.utils';
import {
  GameEvent,
  PotResult,
  SeatCard,
  HandPhase,
  ClientGameMessage,
  UserMessageEvent,
  SeatSummary,
} from './game-events';
import { PlayerAction } from './game-commands';

export interface TableState {
  tableId: string;
  tableStatus: TableStatus | null;
  handNumber: number;
  dealerPosition: number | null;
  smallBlindPosition: number | null;
  bigBlindPosition: number | null;
  smallBlindAmount: number;
  bigBlindAmount: number;
  communityCards: Card[];
  pots: Pot[];
  potTotal: number;
  phase: HandPhase | null;
  potResults: PotResult[] | null;
  /** Seat cards keyed by 1-indexed seat position. */
  seatCards: Map<number, SeatCard[]>;
  /** Per-seat summary keyed by 1-indexed seat position. */
  seatSummaries: Map<number, SeatSummary>;
  lastAction: { seatPosition: number; action: string } | null;
  /** 1-indexed seat position of the player on the clock, or null. */
  actionPosition: number | null;
  actionDeadline: string | null;
  callAmount: number;
  currentBet: number;
  minimumRaise: number;
  actionSeq: number;
}

export interface PlayerState {
  userId: string;
  displayName: string;
  chipCount: number;
  tableId: string | null;
  /** 1-indexed seat position (1..numberOfSeats), or null if not seated. */
  seatPosition: number | null;
}

export interface GameState {
  gameId: string | null;
  status: GameStatus | null;
  players: Map<string, PlayerState>;
  tables: Map<string, TableState>;
  messages: Array<ClientGameMessage | UserMessageEvent>;
}

function createInitialState(): GameState {
  return {
    gameId: null,
    status: null,
    players: new Map(),
    tables: new Map(),
    messages: [],
  };
}

function createInitialTableState(tableId: string): TableState {
  return {
    tableId,
    tableStatus: null,
    handNumber: 0,
    dealerPosition: null,
    smallBlindPosition: null,
    bigBlindPosition: null,
    smallBlindAmount: 0,
    bigBlindAmount: 0,
    communityCards: [],
    pots: [],
    potTotal: 0,
    phase: null,
    potResults: null,
    seatCards: new Map(),
    seatSummaries: new Map(),
    lastAction: null,
    actionPosition: null,
    actionDeadline: null,
    callAmount: 0,
    currentBet: 0,
    minimumRaise: 0,
    actionSeq: 0,
  };
}

function summariesToMap(summaries: SeatSummary[]): Map<number, SeatSummary> {
  const map = new Map<number, SeatSummary>();
  for (const s of summaries) {
    map.set(s.seatPosition, s);
  }
  return map;
}

function sumPots(pots: Pot[]): number {
  return pots.reduce((sum, p) => sum + p.amount, 0);
}

@Injectable({
  providedIn: 'root',
})
export class GameStateService implements OnDestroy {
  private state$ = new BehaviorSubject<GameState>(createInitialState());
  private subscription: Subscription | null = null;

  private statusSubscription: Subscription | null = null;
  private toasterService = inject(ToasterService);
  private displayNames = new Map<string, string>();

  constructor(private webSocketService: GameWebSocketService) {}

  connectToGame(gameId: string): void {
    this.disconnect();

    const state = createInitialState();
    state.gameId = gameId;
    this.state$.next(state);

    // Subscribe to connection status BEFORE connecting so we don't miss the
    // 'connected' event (connectionStatus$ is a plain Subject).
    this.statusSubscription = this.webSocketService
      .getConnectionStatus()
      .pipe(
        filter((status) => status === 'connected'),
        take(1)
      )
      .subscribe(() => {
        this.webSocketService.sendCommand({
          commandId: 'join-game',
          gameId,
        });
        this.webSocketService.sendCommand({
          commandId: 'get-game-state',
          gameId,
        });
      });

    this.subscription = this.webSocketService
      .connect(gameId)
      .subscribe((event) => this.handleEvent(event));
  }

  disconnect(): void {
    this.subscription?.unsubscribe();
    this.subscription = null;
    this.statusSubscription?.unsubscribe();
    this.statusSubscription = null;
    this.webSocketService.disconnect();
    this.displayNames.clear();
    this.state$.next(createInitialState());
  }

  ngOnDestroy(): void {
    this.disconnect();
  }

  // --- Selectors ---

  getState(): Observable<GameState> {
    return this.state$.asObservable();
  }

  getGameStatus(): Observable<GameStatus | null> {
    return this.state$.pipe(
      map((s) => s.status),
      distinctUntilChanged()
    );
  }

  getPlayers(): Observable<Map<string, PlayerState>> {
    return this.state$.pipe(map((s) => s.players));
  }

  getTableState(tableId: string): Observable<TableState | undefined> {
    return this.state$.pipe(
      map((s) => s.tables.get(tableId)),
      distinctUntilChanged()
    );
  }

  getTables(): Observable<Map<string, TableState>> {
    return this.state$.pipe(map((s) => s.tables));
  }

  getMessages(): Observable<Array<ClientGameMessage | UserMessageEvent>> {
    return this.state$.pipe(map((s) => s.messages));
  }

  // --- Helpers ---

  private getDisplayName(userId: string): string {
    return this.displayNames.get(userId) ?? userId;
  }

  private createInfoMessage(gameId: string, message: string): ClientGameMessage {
    return {
      eventType: 'game-message',
      timestamp: new Date().toISOString(),
      gameId,
      message,
    };
  }

  private formatActionVerb(action: PlayerAction): string {
    switch (action.type) {
      case 'fold': return 'folds';
      case 'check': return 'checks';
      case 'call': return `calls ${LangUtils.formatCurrency(action.amount)}`;
      case 'bet': return `bets ${LangUtils.formatCurrency(action.amount)}`;
      case 'raise': return `raises to ${LangUtils.formatCurrency(action.amount)}`;
    }
  }

  private updateTable(
    state: GameState,
    tableId: string,
    updater: (table: TableState) => TableState
  ): void {
    const tables = new Map(state.tables);
    const existing = tables.get(tableId) ?? createInitialTableState(tableId);
    tables.set(tableId, updater(existing));
    state.tables = tables;
  }

  /**
   * Syncs player seat positions from a SeatSummary list. Seat positions are 1-indexed.
   */
  private syncPlayersFromSummaries(
    state: GameState,
    tableId: string,
    summaries: SeatSummary[]
  ): void {
    const players = new Map(state.players);
    for (const s of summaries) {
      if (!s.userId) continue;
      const existing = players.get(s.userId);
      if (existing) {
        players.set(s.userId, {
          ...existing,
          seatPosition: s.seatPosition,
          tableId,
          chipCount: s.chipCount,
        });
      } else {
        players.set(s.userId, {
          userId: s.userId,
          displayName: this.getDisplayName(s.userId),
          chipCount: s.chipCount,
          tableId,
          seatPosition: s.seatPosition,
        });
      }
    }
    state.players = players;
  }

  // --- Event Handling ---

  private handleEvent(event: GameEvent): void {
    const state = { ...this.state$.value };

    switch (event.eventType) {
      case 'game-status-changed':
        state.status = event.newStatus;
        state.messages = [...state.messages, this.createInfoMessage(event.gameId, `Game is now ${event.newStatus}`)];
        break;

      case 'player-joined': {
        const players = new Map(state.players);
        if (!players.has(event.userId)) {
          players.set(event.userId, {
            userId: event.userId,
            displayName: this.getDisplayName(event.userId),
            chipCount: 0,
            tableId: null,
            seatPosition: null,
          });
        }
        state.players = players;
        const name = this.getDisplayName(event.userId);
        state.messages = [...state.messages, this.createInfoMessage(event.gameId, `${name} joined the game`)];
        break;
      }

      case 'player-seated': {
        const players = new Map(state.players);
        const existing = players.get(event.userId);
        if (existing) {
          players.set(event.userId, { ...existing, tableId: event.tableId });
        }
        state.players = players;

        if (!state.tables.has(event.tableId)) {
          const tables = new Map(state.tables);
          tables.set(event.tableId, createInitialTableState(event.tableId));
          state.tables = tables;
        }
        break;
      }

      case 'game-message':
        state.messages = [...state.messages, event];
        break;

      case 'user-message':
        state.messages = [...state.messages, event];
        if (event.severity === 'ERROR' || event.severity === 'WARNING') {
          this.toasterService.displayToast({
            message: event.message,
            type: event.severity === 'ERROR' ? 'error' : 'warning',
          });
        }
        break;

      case 'player-buy-in': {
        const players = new Map(state.players);
        const existing = players.get(event.userId);
        players.set(event.userId, {
          userId: event.userId,
          displayName: existing?.displayName ?? this.getDisplayName(event.userId),
          chipCount: event.newChipCount,
          tableId: existing?.tableId ?? null,
          seatPosition: existing?.seatPosition ?? null,
        });
        state.players = players;
        const name = this.getDisplayName(event.userId);
        state.messages = [...state.messages, this.createInfoMessage(event.gameId, `${name} bought in for ${LangUtils.formatCurrency(event.amount)}`)];
        break;
      }

      case 'player-moved-tables': {
        const players = new Map(state.players);
        const existing = players.get(event.userId);
        players.set(event.userId, {
          userId: event.userId,
          displayName: existing?.displayName ?? this.getDisplayName(event.userId),
          chipCount: existing?.chipCount ?? 0,
          tableId: event.toTableId,
          seatPosition: existing?.seatPosition ?? null,
        });
        state.players = players;

        if (!state.tables.has(event.toTableId)) {
          const tables = new Map(state.tables);
          tables.set(event.toTableId, createInitialTableState(event.toTableId));
          state.tables = tables;
        }
        const name = this.getDisplayName(event.userId);
        state.messages = [...state.messages, this.createInfoMessage(event.gameId, `${name} was assigned to a table`)];
        break;
      }

      case 'player-disconnected': {
        const name = this.getDisplayName(event.userId);
        state.messages = [...state.messages, this.createInfoMessage(event.gameId, `${name} disconnected`)];
        break;
      }

      case 'player-reconnected': {
        const name = this.getDisplayName(event.userId);
        state.messages = [...state.messages, this.createInfoMessage(event.gameId, `${name} reconnected`)];
        break;
      }

      case 'admin-viewing-replay': {
        state.messages = [
          ...state.messages,
          this.createInfoMessage(event.gameId, `${event.adminAlias} is reviewing hand #${event.handNumber}`),
        ];
        break;
      }

      case 'table-status-changed': {
        this.updateTable(state, event.tableId, (t) => ({
          ...t,
          tableStatus: event.newStatus,
        }));
        break;
      }

      case 'hand-phase-changed': {
        this.updateTable(state, event.tableId, (t) => ({
          ...t,
          phase: event.newPhase,
        }));
        break;
      }

      case 'waiting-for-players': {
        this.updateTable(state, event.tableId, (t) => ({
          ...t,
          phase: 'WAITING_FOR_PLAYERS',
        }));
        break;
      }

      case 'blind-posted': {
        this.updateTable(state, event.tableId, (t) => {
          const seatSummaries = new Map(t.seatSummaries);
          const prior = seatSummaries.get(event.seatPosition);
          if (prior) {
            seatSummaries.set(event.seatPosition, {
              ...prior,
              chipCount: prior.chipCount - event.amountPosted,
              currentBetAmount: prior.currentBetAmount + event.amountPosted,
            });
          }
          return { ...t, seatSummaries };
        });
        const players = new Map(state.players);
        const existingPlayer = players.get(event.userId);
        if (existingPlayer) {
          players.set(event.userId, {
            ...existingPlayer,
            chipCount: existingPlayer.chipCount - event.amountPosted,
          });
          state.players = players;
        }
        break;
      }

      case 'hand-started': {
        this.updateTable(state, event.tableId, (t) => ({
          ...t,
          handNumber: event.handNumber,
          dealerPosition: event.dealerPosition,
          smallBlindPosition: event.smallBlindPosition,
          bigBlindPosition: event.bigBlindPosition,
          smallBlindAmount: event.smallBlindAmount,
          bigBlindAmount: event.bigBlindAmount,
          currentBet: event.currentBet,
          minimumRaise: event.minimumRaise,
          communityCards: [],
          pots: [],
          potTotal: 0,
          phase: 'PREDEAL',
          potResults: null,
          seatCards: new Map(),
          seatSummaries: summariesToMap(event.seats),
          lastAction: null,
          actionPosition: null,
          actionDeadline: null,
          callAmount: 0,
          actionSeq: 0,
        }));
        this.syncPlayersFromSummaries(state, event.tableId, event.seats);
        state.messages = [...state.messages, this.createInfoMessage(event.gameId, `Hand #${event.handNumber} started`)];
        break;
      }

      case 'hole-cards-dealt': {
        this.updateTable(state, event.tableId, (t) => {
          const seatCards = new Map(t.seatCards);
          // Set the current player's actual cards
          seatCards.set(event.seatPosition, event.cards);
          // Set face-down placeholders for all other dealt seats
          const faceDownCards: SeatCard[] = [
            { card: { value: CardValue.TWO, suit: CardSuit.SPADE }, showCard: false },
            { card: { value: CardValue.TWO, suit: CardSuit.SPADE }, showCard: false },
          ];
          for (const pos of event.seatsWithCards ?? []) {
            if (!seatCards.has(pos)) {
              seatCards.set(pos, faceDownCards);
            }
          }
          return { ...t, seatCards };
        });
        break;
      }

      case 'community-cards-dealt': {
        this.updateTable(state, event.tableId, (t) => ({
          ...t,
          communityCards: event.allCommunityCards,
          phase: event.phase,
        }));
        break;
      }

      case 'player-acted': {
        this.updateTable(state, event.tableId, (t) => {
          const seatSummaries = new Map(t.seatSummaries);
          const prior = seatSummaries.get(event.seatPosition);
          if (prior) {
            // PlayerActed doesn't carry currentBetAmount; derive it from the
            // chip delta. Fold/check spend 0 chips so bet is unchanged; call/
            // bet/raise/all-in increase the seat's bet by exactly the chips
            // that left the stack.
            const chipsSpent = prior.chipCount - event.chipCount;
            seatSummaries.set(event.seatPosition, {
              ...prior,
              status: event.resultingStatus,
              chipCount: event.chipCount,
              currentBetAmount: prior.currentBetAmount + chipsSpent,
            });
          }
          return {
            ...t,
            lastAction: {
              seatPosition: event.seatPosition,
              action: event.action.type,
            },
            currentBet: event.currentBet,
            minimumRaise: event.minimumRaise,
            potTotal: event.potTotal,
            seatSummaries,
            actionSeq: t.actionSeq + 1,
          };
        });

        // Append action history message
        const actorName = this.getDisplayName(event.userId);
        const verb = this.formatActionVerb(event.action);
        const allInSuffix = event.resultingStatus === 'ALL_IN' ? ' (all in)' : '';
        const actionMsg: ClientGameMessage = {
          eventType: 'game-message',
          timestamp: new Date().toISOString(),
          gameId: event.gameId,
          message: `${actorName} ${verb}${allInSuffix}`,
          kind: 'action',
        };
        state.messages = [...state.messages, actionMsg];

        // Update player chip count
        const players = new Map(state.players);
        const existing = players.get(event.userId);
        if (existing) {
          players.set(event.userId, { ...existing, chipCount: event.chipCount });
          state.players = players;
        }
        break;
      }

      case 'player-timed-out': {
        this.updateTable(state, event.tableId, (t) => ({
          ...t,
          lastAction: {
            seatPosition: event.seatPosition,
            action: event.defaultAction.type,
          },
          actionSeq: t.actionSeq + 1,
        }));
        break;
      }

      case 'action-on-player': {
        this.updateTable(state, event.tableId, (t) => ({
          ...t,
          actionPosition: event.seatPosition,
          actionDeadline: event.actionDeadline,
          currentBet: event.currentBet,
          minimumRaise: event.minimumRaise,
          callAmount: event.callAmount,
          potTotal: event.potTotal,
        }));
        break;
      }

      case 'betting-round-complete': {
        this.updateTable(state, event.tableId, (t) => ({
          ...t,
          pots: event.pots,
          potTotal: event.potTotal,
          seatSummaries: summariesToMap(event.seats),
          // completedPhase is the phase that just finished; HandPhaseChanged
          // drives the next phase. Clear per-round transient action state.
          actionPosition: null,
          actionDeadline: null,
          callAmount: 0,
          lastAction: null,
        }));
        this.syncPlayersFromSummaries(state, event.tableId, event.seats);
        break;
      }

      case 'showdown-result': {
        this.updateTable(state, event.tableId, (t) => ({
          ...t,
          potResults: event.potResults,
        }));
        for (const pot of event.potResults) {
          for (const winner of pot.winners) {
            const name = this.getDisplayName(winner.userId);
            const desc = winner.handDescription
              ? ` (${winner.handDescription})`
              : '';
            const msg: ClientGameMessage = {
              eventType: 'game-message',
              timestamp: new Date().toISOString(),
              gameId: event.gameId,
              message: `${name} won ${LangUtils.formatCurrency(winner.amount)}${desc}`,
              kind: 'showdown',
            };
            state.messages = [...state.messages, msg];
          }
        }
        break;
      }

      case 'hand-complete': {
        this.updateTable(state, event.tableId, (t) => ({
          ...t,
          phase: 'HAND_COMPLETE',
          actionPosition: null,
          actionDeadline: null,
          callAmount: 0,
          lastAction: null,
        }));
        break;
      }

      case 'game-snapshot': {
        state.status = event.status;

        const players = new Map<string, PlayerState>();
        for (const player of event.players) {
          const displayName = player.user.alias || player.user.name || player.user.id;
          this.displayNames.set(player.user.id, displayName);
          players.set(player.user.id, {
            userId: player.user.id,
            displayName,
            chipCount: player.chipCount,
            tableId: player.tableId,
            seatPosition: null,
          });
        }
        state.players = players;

        // Ensure table entries exist for all table IDs and request each table's state
        const tables = new Map(state.tables);
        for (const tableId of event.tableIds) {
          if (!tables.has(tableId)) {
            tables.set(tableId, createInitialTableState(tableId));
          }
          this.webSocketService.sendCommand({
            commandId: 'get-table-state',
            gameId: event.gameId,
            tableId,
          });
        }
        state.tables = tables;
        break;
      }

      case 'table-snapshot': {
        const t = event.table;
        // seats[i] in the snapshot corresponds to seat position (i + 1).
        const seatCards = new Map<number, SeatCard[]>();
        for (let i = 0; i < t.seats.length; i++) {
          const pos = i + 1;
          if (t.seats[i].cards) {
            seatCards.set(pos, t.seats[i].cards!);
          }
        }

        // Build SeatSummary map from the Table's seats.
        const seatSummaries = new Map<number, SeatSummary>();
        for (let i = 0; i < t.seats.length; i++) {
          const pos = i + 1;
          const seat = t.seats[i];
          if (seat.status === ('EMPTY' as SeatStatus) || !seat.player) {
            continue;
          }
          seatSummaries.set(pos, {
            seatPosition: pos,
            userId: seat.player.user.id,
            status: seat.isAllIn ? 'ALL_IN' : seat.status === 'FOLDED' ? 'FOLDED' : 'ACTIVE',
            chipCount: seat.player.chipCount,
            currentBetAmount: seat.currentBetAmount,
          });
        }

        this.updateTable(state, t.id, (existing) => ({
          ...existing,
          tableStatus: t.status,
          handNumber: t.handNumber,
          dealerPosition: t.dealerPosition,
          smallBlindPosition: t.smallBlindPosition,
          bigBlindPosition: t.bigBlindPosition,
          communityCards: t.communityCards,
          pots: t.pots,
          potTotal: sumPots(t.pots),
          phase: t.handPhase,
          potResults: null,
          seatCards,
          seatSummaries,
          lastAction: null,
          actionPosition: t.actionPosition,
          actionDeadline: t.actionDeadline,
          currentBet: t.currentBet,
          minimumRaise: t.minimumRaise,
          callAmount: 0,
        }));

        // Update player seat positions and display names from the table snapshot
        const players = new Map(state.players);
        for (let i = 0; i < t.seats.length; i++) {
          const pos = i + 1;
          const seat = t.seats[i];
          if (seat.player) {
            const userId = seat.player.user.id;
            const displayName = seat.player.user.alias || seat.player.user.name || userId;
            this.displayNames.set(userId, displayName);
            const existing = players.get(userId);
            if (existing) {
              players.set(userId, { ...existing, seatPosition: pos, tableId: t.id });
            }
          }
        }
        state.players = players;
        break;
      }
    }

    this.state$.next(state);
  }
}
