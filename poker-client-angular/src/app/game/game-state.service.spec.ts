import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';

import { GameStateService } from './game-state.service';
import { GameWebSocketService, ConnectionStatus } from './game-websocket.service';
import { ToasterService } from '../toaster/toaster.service';
import {
  AdminViewingReplayEvent,
  BlindPostedEvent,
  ClientGameMessage,
  GameEvent,
  HandStartedEvent,
  PlayerActedEvent,
  PlayerDisconnectedEvent,
  PlayerReconnectedEvent,
  SeatSummary,
  ShowdownResultEvent,
  UserMessageEvent,
} from './game-events';

class FakeWebSocketService {
  events$ = new Subject<GameEvent>();
  status$ = new Subject<ConnectionStatus>();
  sentCommands: unknown[] = [];

  connect() {
    return this.events$.asObservable();
  }
  disconnect() {}
  sendCommand(c: unknown) {
    this.sentCommands.push(c);
  }
  getConnectionStatus() {
    return this.status$.asObservable();
  }
}

const TABLE_ID = 'table-1';
const GAME_ID = 'game-1';

function seat(
  seatPosition: number,
  userId: string,
  chipCount: number,
  currentBetAmount = 0,
  status: SeatSummary['status'] = 'ACTIVE'
): SeatSummary {
  return { seatPosition, userId, status, chipCount, currentBetAmount };
}

function handStarted(seats: SeatSummary[]): HandStartedEvent {
  return {
    eventType: 'hand-started',
    timestamp: '2026-05-09T00:00:00Z',
    gameId: GAME_ID,
    tableId: TABLE_ID,
    handNumber: 1,
    dealerPosition: 1,
    smallBlindPosition: 2,
    bigBlindPosition: 3,
    smallBlindAmount: 5,
    bigBlindAmount: 10,
    currentBet: 10,
    minimumRaise: 10,
    seats,
  };
}

describe('GameStateService', () => {
  let service: GameStateService;
  let ws: FakeWebSocketService;

  beforeEach(() => {
    ws = new FakeWebSocketService();
    TestBed.configureTestingModule({
      providers: [
        GameStateService,
        { provide: GameWebSocketService, useValue: ws },
        { provide: ToasterService, useValue: { displayToast: jest.fn() } },
      ],
    });
    service = TestBed.inject(GameStateService);
    service.connectToGame(GAME_ID);
  });

  function getSeat(pos: number): SeatSummary | undefined {
    let result: SeatSummary | undefined;
    service.getTableState(TABLE_ID).subscribe((t) => {
      result = t?.seatSummaries.get(pos);
    });
    return result;
  }

  describe('player-acted', () => {
    it('updates the seat currentBetAmount based on the chip-count delta', () => {
      ws.events$.next(
        handStarted([seat(2, 'user-2', 95, 5), seat(3, 'user-3', 90, 10)])
      );

      const acted: PlayerActedEvent = {
        eventType: 'player-acted',
        timestamp: '2026-05-09T00:00:01Z',
        gameId: GAME_ID,
        tableId: TABLE_ID,
        seatPosition: 3,
        userId: 'user-3',
        action: { type: 'raise', amount: 30 },
        chipCount: 70,
        resultingStatus: 'ACTIVE',
        currentBet: 30,
        minimumRaise: 20,
        potTotal: 35,
      };
      ws.events$.next(acted);

      const s = getSeat(3)!;
      expect(s.chipCount).toBe(70);
      expect(s.currentBetAmount).toBe(30);
      expect(s.status).toBe('ACTIVE');
    });

    it('leaves currentBetAmount unchanged on fold (zero chips spent)', () => {
      ws.events$.next(
        handStarted([seat(2, 'user-2', 95, 5), seat(3, 'user-3', 90, 10)])
      );

      const folded: PlayerActedEvent = {
        eventType: 'player-acted',
        timestamp: '2026-05-09T00:00:01Z',
        gameId: GAME_ID,
        tableId: TABLE_ID,
        seatPosition: 2,
        userId: 'user-2',
        action: { type: 'fold' },
        chipCount: 95,
        resultingStatus: 'FOLDED',
        currentBet: 10,
        minimumRaise: 10,
        potTotal: 15,
      };
      ws.events$.next(folded);

      const s = getSeat(2)!;
      expect(s.chipCount).toBe(95);
      expect(s.currentBetAmount).toBe(5);
      expect(s.status).toBe('FOLDED');
    });

    it('accumulates currentBetAmount across multiple actions in the same round', () => {
      ws.events$.next(
        handStarted([seat(2, 'user-2', 95, 5), seat(3, 'user-3', 90, 10)])
      );

      ws.events$.next({
        eventType: 'player-acted',
        timestamp: '2026-05-09T00:00:01Z',
        gameId: GAME_ID,
        tableId: TABLE_ID,
        seatPosition: 3,
        userId: 'user-3',
        action: { type: 'raise', amount: 30 },
        chipCount: 70,
        resultingStatus: 'ACTIVE',
        currentBet: 30,
        minimumRaise: 20,
        potTotal: 35,
      } satisfies PlayerActedEvent);

      // SB calls: was 5 in, owes 25 more to match the 30-bet
      ws.events$.next({
        eventType: 'player-acted',
        timestamp: '2026-05-09T00:00:02Z',
        gameId: GAME_ID,
        tableId: TABLE_ID,
        seatPosition: 2,
        userId: 'user-2',
        action: { type: 'call', amount: 25 },
        chipCount: 70,
        resultingStatus: 'ACTIVE',
        currentBet: 30,
        minimumRaise: 20,
        potTotal: 60,
      } satisfies PlayerActedEvent);

      expect(getSeat(2)!.currentBetAmount).toBe(30);
      expect(getSeat(3)!.currentBetAmount).toBe(30);
    });
  });

  describe('actionSeq', () => {
    function getActionSeq(): number | undefined {
      let seq: number | undefined;
      service.getTableState(TABLE_ID).subscribe((t) => {
        seq = t?.actionSeq;
      });
      return seq;
    }

    it('initializes to 0 when a hand starts', () => {
      ws.events$.next(handStarted([seat(2, 'user-2', 100, 0), seat(3, 'user-3', 100, 0)]));
      expect(getActionSeq()).toBe(0);
    });

    it('increments on each player-acted event', () => {
      ws.events$.next(handStarted([seat(2, 'user-2', 95, 5), seat(3, 'user-3', 90, 10)]));

      ws.events$.next({
        eventType: 'player-acted',
        timestamp: '2026-05-09T00:00:01Z',
        gameId: GAME_ID,
        tableId: TABLE_ID,
        seatPosition: 3,
        userId: 'user-3',
        action: { type: 'raise', amount: 30 },
        chipCount: 70,
        resultingStatus: 'ACTIVE',
        currentBet: 30,
        minimumRaise: 20,
        potTotal: 35,
      } satisfies PlayerActedEvent);
      expect(getActionSeq()).toBe(1);

      ws.events$.next({
        eventType: 'player-acted',
        timestamp: '2026-05-09T00:00:02Z',
        gameId: GAME_ID,
        tableId: TABLE_ID,
        seatPosition: 2,
        userId: 'user-2',
        action: { type: 'call', amount: 25 },
        chipCount: 70,
        resultingStatus: 'ACTIVE',
        currentBet: 30,
        minimumRaise: 20,
        potTotal: 60,
      } satisfies PlayerActedEvent);
      expect(getActionSeq()).toBe(2);
    });

    it('resets to 0 when a new hand starts', () => {
      ws.events$.next(handStarted([seat(2, 'user-2', 95, 5), seat(3, 'user-3', 90, 10)]));

      ws.events$.next({
        eventType: 'player-acted',
        timestamp: '2026-05-09T00:00:01Z',
        gameId: GAME_ID,
        tableId: TABLE_ID,
        seatPosition: 3,
        userId: 'user-3',
        action: { type: 'fold' },
        chipCount: 90,
        resultingStatus: 'FOLDED',
        currentBet: 10,
        minimumRaise: 10,
        potTotal: 15,
      } satisfies PlayerActedEvent);
      expect(getActionSeq()).toBe(1);

      ws.events$.next(handStarted([seat(2, 'user-2', 95, 5), seat(3, 'user-3', 90, 10)]));
      expect(getActionSeq()).toBe(0);
    });

    it('bumps on player-timed-out and records the default action', () => {
      ws.events$.next(handStarted([seat(2, 'user-2', 95, 5), seat(3, 'user-3', 90, 10)]));

      ws.events$.next({
        eventType: 'player-timed-out',
        timestamp: '2026-05-09T00:00:03Z',
        gameId: GAME_ID,
        tableId: TABLE_ID,
        seatPosition: 3,
        userId: 'user-3',
        defaultAction: { type: 'fold' },
      });

      expect(getActionSeq()).toBe(1);

      let lastAction: { seatPosition: number; action: string } | null = null;
      service.getTableState(TABLE_ID).subscribe((t) => {
        lastAction = t?.lastAction ?? null;
      });
      expect(lastAction).toEqual({ seatPosition: 3, action: 'fold' });
    });
  });

  describe('blind-posted', () => {
    it('debits chips and credits currentBetAmount on the posting seat', () => {
      // Seed seat summaries via a minimal hand-started so the seat exists.
      ws.events$.next(handStarted([seat(2, 'user-2', 100, 0), seat(3, 'user-3', 100, 0)]));

      const sb: BlindPostedEvent = {
        eventType: 'blind-posted',
        timestamp: '2026-05-09T00:00:01Z',
        gameId: GAME_ID,
        tableId: TABLE_ID,
        seatPosition: 2,
        userId: 'user-2',
        blindType: 'SMALL',
        amountPosted: 5,
      };
      ws.events$.next(sb);

      const s = getSeat(2)!;
      expect(s.chipCount).toBe(95);
      expect(s.currentBetAmount).toBe(5);
    });
  });

  describe('presence and replay events', () => {
    it('appends an info message on player-disconnected', () => {
      const evt: PlayerDisconnectedEvent = {
        eventType: 'player-disconnected',
        timestamp: '2026-05-09T00:00:01Z',
        gameId: GAME_ID,
        userId: 'user-2',
      };
      ws.events$.next(evt);

      let messages: ReadonlyArray<{ message: string }> = [];
      service.getMessages().subscribe((m) => (messages = m));
      expect(messages.at(-1)?.message).toContain('disconnected');
    });

    it('appends an info message on player-reconnected', () => {
      const evt: PlayerReconnectedEvent = {
        eventType: 'player-reconnected',
        timestamp: '2026-05-09T00:00:01Z',
        gameId: GAME_ID,
        userId: 'user-2',
      };
      ws.events$.next(evt);

      let messages: ReadonlyArray<{ message: string }> = [];
      service.getMessages().subscribe((m) => (messages = m));
      expect(messages.at(-1)?.message).toContain('reconnected');
    });

    it('appends an info message naming the admin on admin-viewing-replay', () => {
      const evt: AdminViewingReplayEvent = {
        eventType: 'admin-viewing-replay',
        timestamp: '2026-05-09T00:00:01Z',
        gameId: GAME_ID,
        adminUserId: 'admin-1',
        adminAlias: 'TheBoss',
        tableId: TABLE_ID,
        handNumber: 42,
      };
      ws.events$.next(evt);

      let messages: ReadonlyArray<{ message: string }> = [];
      service.getMessages().subscribe((m) => (messages = m));
      expect(messages.at(-1)?.message).toContain('TheBoss');
      expect(messages.at(-1)?.message).toContain('42');
    });
  });

  describe('showdown-result messages', () => {
    it('tags emitted winner messages with kind="showdown"', () => {
      ws.events$.next(handStarted([seat(2, 'user-2', 95, 5), seat(3, 'user-3', 90, 10)]));

      ws.events$.next({
        eventType: 'showdown-result',
        timestamp: '2026-05-09T00:01:00Z',
        gameId: GAME_ID,
        tableId: TABLE_ID,
        potResults: [
          {
            potIndex: 0,
            potAmount: 50,
            winners: [
              { seatPosition: 3, userId: 'user-3', amount: 50, handDescription: 'Pair of Aces' },
            ],
          },
        ],
      } satisfies ShowdownResultEvent);

      let messages: Array<ClientGameMessage | UserMessageEvent> = [];
      service.getMessages().subscribe((m) => { messages = m; });
      const winnerMsg = messages.find(
        (m) => m.eventType === 'game-message' && (m as ClientGameMessage).kind === 'showdown'
      );
      expect(winnerMsg).toBeDefined();
      expect(winnerMsg!.message).toContain('Pair of Aces');
    });
  });

  describe('player-acted log messages', () => {
    function getMessages(): Array<ClientGameMessage | UserMessageEvent> {
      let result: Array<ClientGameMessage | UserMessageEvent> = [];
      service.getMessages().subscribe((m) => { result = m; });
      return result;
    }

    beforeEach(() => {
      ws.events$.next(handStarted([seat(2, 'user-2', 95, 5), seat(3, 'user-3', 90, 10)]));
    });

    it('logs "folds" with kind=action', () => {
      ws.events$.next({
        eventType: 'player-acted',
        timestamp: '2026-05-09T00:00:01Z',
        gameId: GAME_ID,
        tableId: TABLE_ID,
        seatPosition: 2,
        userId: 'user-2',
        action: { type: 'fold' },
        chipCount: 95,
        resultingStatus: 'FOLDED',
        currentBet: 10,
        minimumRaise: 10,
        potTotal: 15,
      } satisfies PlayerActedEvent);

      const last = getMessages().at(-1)!;
      expect(last.eventType).toBe('game-message');
      expect((last as ClientGameMessage).kind).toBe('action');
      expect(last.message).toMatch(/folds$/);
    });

    it('logs "calls $X.XX"', () => {
      ws.events$.next({
        eventType: 'player-acted',
        timestamp: '2026-05-09T00:00:01Z',
        gameId: GAME_ID,
        tableId: TABLE_ID,
        seatPosition: 2,
        userId: 'user-2',
        action: { type: 'call', amount: 5 },
        chipCount: 90,
        resultingStatus: 'ACTIVE',
        currentBet: 10,
        minimumRaise: 10,
        potTotal: 20,
      } satisfies PlayerActedEvent);

      expect(getMessages().at(-1)!.message).toMatch(/calls \$0\.05$/);
    });

    it('logs "raises to $X.XX" using the wire-level total bet amount', () => {
      ws.events$.next({
        eventType: 'player-acted',
        timestamp: '2026-05-09T00:00:01Z',
        gameId: GAME_ID,
        tableId: TABLE_ID,
        seatPosition: 3,
        userId: 'user-3',
        action: { type: 'raise', amount: 30 },
        chipCount: 70,
        resultingStatus: 'ACTIVE',
        currentBet: 30,
        minimumRaise: 20,
        potTotal: 35,
      } satisfies PlayerActedEvent);

      expect(getMessages().at(-1)!.message).toMatch(/raises to \$0\.30$/);
    });

    it('appends "(all in)" when resultingStatus is ALL_IN', () => {
      ws.events$.next({
        eventType: 'player-acted',
        timestamp: '2026-05-09T00:00:01Z',
        gameId: GAME_ID,
        tableId: TABLE_ID,
        seatPosition: 3,
        userId: 'user-3',
        action: { type: 'raise', amount: 90 },
        chipCount: 0,
        resultingStatus: 'ALL_IN',
        currentBet: 90,
        minimumRaise: 80,
        potTotal: 95,
      } satisfies PlayerActedEvent);

      expect(getMessages().at(-1)!.message).toMatch(/raises to \$0\.90 \(all in\)$/);
    });
  });
});
