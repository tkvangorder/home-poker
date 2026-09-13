import { AuthenticatedUserReponse } from './rest-client-models';
import { RegisterUserRequest } from '../user/user-models';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import {
  CashGameConfiguration,
  CashGameDetails,
  GameCriteria
} from '../game/game-models';

@Injectable({
  providedIn: 'root',
})
export class PokerRestClient {
  baseUrl: string = '/api';
  constructor(private httpClient: HttpClient) {}

  login(loginId: string, password: string) {
    return this.httpClient.post<AuthenticatedUserReponse>(
      `${this.baseUrl}/auth/login`,
      {
        loginId: loginId,
        password: password,
      }
    );
  }

  registerUser(registerUserRequest: RegisterUserRequest) {
    return this.httpClient.post<AuthenticatedUserReponse>(
      `${this.baseUrl}/auth/register`,
      registerUserRequest
    );
  }

  searchGames(criteria: GameCriteria) {
    return this.httpClient.post<CashGameDetails[]>(
      `${this.baseUrl}/cash-games/search`,
      criteria
    );
  }

  getGame(gameId: string) {
    return this.httpClient.get<CashGameDetails>(
      `${this.baseUrl}/cash-games/${gameId}`,
      { params: new HttpParams().set('gameId', gameId) }
    );
  }

  createGame(cashGameConfiguration: CashGameConfiguration) {
    return this.httpClient.post<CashGameDetails>(
      `${this.baseUrl}/cash-games`,
      cashGameConfiguration
    );
  }

  updateGame(gameId: string, cashGameConfiguration: CashGameConfiguration) {
    return this.httpClient.post<CashGameDetails>(
      `${this.baseUrl}/cash-games/${gameId}/update`,
      cashGameConfiguration,
      { params: new HttpParams().set('gameId', gameId) }
    );
  }

  deleteGame(gameId: string) {
    return this.httpClient.delete<void>(
      `${this.baseUrl}/cash-games/${gameId}`,
      { params: new HttpParams().set('gameId', gameId) }
    );
  }

}
