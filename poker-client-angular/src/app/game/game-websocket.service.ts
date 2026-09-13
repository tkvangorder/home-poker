import { Injectable } from '@angular/core';
import { webSocket, WebSocketSubject } from 'rxjs/webSocket';
import { Observable, Subject, timer, EMPTY } from 'rxjs';
import { catchError, retry, tap } from 'rxjs/operators';
import { UserService } from '../user/user-service';
import { GameEvent } from './game-events';
import { GameCommand } from './game-commands';

export type ConnectionStatus = 'disconnected' | 'connecting' | 'connected' | 'error';

@Injectable({
  providedIn: 'root',
})
export class GameWebSocketService {
  // Use `any` internally since WebSocketSubject is bidirectional but we
  // send GameCommand and receive GameEvent as different shapes.
  private socket$: WebSocketSubject<any> | null = null;
  private connectionStatus$ = new Subject<ConnectionStatus>();

  constructor(private userService: UserService) {}

  getConnectionStatus(): Observable<ConnectionStatus> {
    return this.connectionStatus$.asObservable();
  }

  /**
   * Builds the game WebSocket URL relative to the page origin. In production
   * the Spring server serves both the client and the WebSocket endpoint; under
   * `ng serve` the dev proxy forwards `/ws` to the server.
   */
  buildSocketUrl(gameId: string, token: string): string {
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
    return `${scheme}://${window.location.host}/ws/games/${gameId}?token=${token}`;
  }

  connect(gameId: string): Observable<GameEvent> {
    const user = this.userService.getCurrentUser();
    if (!user?.token) {
      this.connectionStatus$.next('error');
      return EMPTY;
    }

    this.disconnect();
    this.connectionStatus$.next('connecting');

    const url = this.buildSocketUrl(gameId, user.token);

    this.socket$ = webSocket({
      url,
      openObserver: {
        next: () => this.connectionStatus$.next('connected'),
      },
      closeObserver: {
        next: () => this.connectionStatus$.next('disconnected'),
      },
    });

    return (this.socket$ as Observable<GameEvent>).pipe(
      tap({
        error: () => this.connectionStatus$.next('error'),
      }),
      retry({
        count: 5,
        delay: (_error, retryCount) => {
          this.connectionStatus$.next('connecting');
          const delayMs = Math.min(1000 * Math.pow(2, retryCount), 30000);
          return timer(delayMs);
        },
      }),
      catchError(() => {
        this.connectionStatus$.next('error');
        return EMPTY;
      })
    );
  }

  sendCommand(command: GameCommand): void {
    if (this.socket$) {
      this.socket$.next(command);
    }
  }

  disconnect(): void {
    if (this.socket$) {
      this.socket$.complete();
      this.socket$ = null;
      this.connectionStatus$.next('disconnected');
    }
  }
}
