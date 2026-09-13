/**
 * Game commands sent from the client to the server via WebSocket.
 * The `user` field is never sent — it is injected server-side from the authenticated session.
 */

// --- Player Actions ---

export interface FoldAction {
  type: 'fold';
}

export interface CheckAction {
  type: 'check';
}

export interface CallAction {
  type: 'call';
  amount: number;
}

export interface BetAction {
  type: 'bet';
  amount: number;
}

export interface RaiseAction {
  type: 'raise';
  amount: number;
}

export type PlayerAction = FoldAction | CheckAction | CallAction | BetAction | RaiseAction;

// --- Game-Level Commands ---

export interface JoinGameCommand {
  commandId: 'join-game';
  gameId: string;
}

export interface StartGameCommand {
  commandId: 'start-game';
  gameId: string;
}

export interface PauseGameCommand {
  commandId: 'pause-game';
  gameId: string;
}

export interface ResumeGameCommand {
  commandId: 'resume-game';
  gameId: string;
}

export interface EndGameCommand {
  commandId: 'end-game';
  gameId: string;
}

export interface BuyInCommand {
  commandId: 'buy-in';
  gameId: string;
  amount: number;
}

export interface LeaveGameCommand {
  commandId: 'leave-game';
  gameId: string;
}

export interface GetGameStateCommand {
  commandId: 'get-game-state';
  gameId: string;
}

// --- Table-Level Commands ---

export interface PlayerActionCommand {
  commandId: 'player-action-command';
  gameId: string;
  tableId: string;
  action: PlayerAction;
}

export interface PlayerIntentCommand {
  commandId: 'player-intent';
  gameId: string;
  tableId: string;
  action: PlayerAction;
}

export interface ShowCardsCommand {
  commandId: 'show-cards';
  gameId: string;
  tableId: string;
}

export interface PostBlindCommand {
  commandId: 'post-blind';
  gameId: string;
  tableId: string;
}

export interface GetTableStateCommand {
  commandId: 'get-table-state';
  gameId: string;
  tableId: string;
}

export type GameCommand =
  | JoinGameCommand
  | StartGameCommand
  | PauseGameCommand
  | ResumeGameCommand
  | EndGameCommand
  | BuyInCommand
  | LeaveGameCommand
  | PlayerActionCommand
  | PlayerIntentCommand
  | ShowCardsCommand
  | PostBlindCommand
  | GetGameStateCommand
  | GetTableStateCommand;
