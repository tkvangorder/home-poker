import { Card } from '../poker/poker-models';
import { User } from '../user/user-models';
import { PlayerAction } from './game-commands';

export type GameStatus = 'SCHEDULED' | 'SEATING' | 'ACTIVE' | 'BALANCING' | 'PAUSED' | 'COMPLETED';

export enum GameFormat {
  'CASH',
  'TOURNAMENT',
}
export enum GameType {
  'TEXAS_HOLDEM',
}

export type PlayerStatus =
  | 'ACTIVE'
  | 'OUT'
  | 'AWAY'
  | 'BUYING_IN'
  | 'REGISTERED'
  | 'OBSERVER';

export type TableStatus = 'PLAYING' | 'PAUSE_AFTER_HAND' | 'PAUSED';

export type SeatStatus = 'ACTIVE' | 'FOLDED' | 'JOINED_WAITING' | 'EMPTY';

export type HandPhase =
  | 'WAITING_FOR_PLAYERS'
  | 'PREDEAL'
  | 'DEAL'
  | 'PRE_FLOP_BETTING'
  | 'FLOP'
  | 'FLOP_BETTING'
  | 'TURN'
  | 'TURN_BETTING'
  | 'RIVER'
  | 'RIVER_BETTING'
  | 'SHOWDOWN'
  | 'HAND_COMPLETE';

export interface GameCriteria {
  name?: string;
  statuses?: GameStatus[];
  startTime?: string;
  endTime?: string;
}

export interface Player {
  user: User;
  status: PlayerStatus;
  chipCount: number;
  buyInTotal: number;
  reBuys: number;
  addOns: number;
  tableId: string | null;
}

export interface SeatCard {
  card: Card;
  showCard: boolean;
}

export interface Pot {
  amount: number;
  seatPositions: number[];
}

export interface Seat {
  status: SeatStatus;
  player: Player | null;
  cards: SeatCard[] | null;
  action: PlayerAction | null;
  currentBetAmount: number;
  isAllIn: boolean;
  mustPostBlind: boolean;
  missedBigBlind: boolean;
  pendingIntent: PlayerAction | null;
}

export interface Table {
  id: string;
  seats: Seat[];
  status: TableStatus;
  handPhase: HandPhase;
  dealerPosition: number | null;
  actionPosition: number | null;
  smallBlindPosition: number | null;
  bigBlindPosition: number | null;
  lastRaiserPosition: number | null;
  currentBet: number;
  minimumRaise: number;
  handNumber: number;
  phaseStartedAt: string | null;
  actionDeadline: string | null;
  communityCards: Card[];
  pots: Pot[];
}

export interface Game {
  id: string;
  name: string;
  status: GameStatus;
  format: GameFormat;
  type: GameType;
  startTime: string;
  endTime: string | undefined;
  owner: User;
  players: Map<string, Player>;
  tables: Table[];
}

export interface CashGameConfiguration {
  name: string | undefined;
  gameType: GameType | undefined;
  startTime: Date;
  maxBuyIn: number;
  smallBlind: number;
  bigBlind: number;
}

export interface CashGameDetails {
  id: string;
  name: string;
  type: GameType;
  status: GameStatus;
  startTime: string;
  maxBuyIn: number;
  owner: User;
  smallBlind: number;
  bigBlind: number;
  players: Player[];
}

export class CashGame implements Game {
  public readonly format = GameFormat.CASH;

  constructor(
    public id: string,
    public name: string,
    public status: GameStatus,
    public type: GameType,
    public owner: User,
    public startTime: string,
    public endTime: string | undefined,
    public players: Map<string, Player>,
    public tables: Table[],
    public smallBlind: number,
    public bigBlind: number,
    public maxBuyIn: number
  ) {}
}
