/**
 * See game-websocket.service.spec.ts for why this is a separate file: jsdom
 * (26.1.0, bundled with this repo's jest-environment-jsdom) makes
 * `window.location` a non-configurable own property, so no single test file
 * can mutate it mid-run to exercise two different origins. Instead, each
 * file pins its own real origin via the `@jest-environment-options` docblock
 * and asserts buildSocketUrl against jsdom's genuine, unmocked
 * window.location for that origin. This file covers the wss:// (https) case;
 * game-websocket.service.spec.ts covers the ws:// (http) case.
 *
 * @jest-environment jsdom
 * @jest-environment-options {"url": "https://poker.example.com/"}
 */
import { TestBed } from '@angular/core/testing';
import { GameWebSocketService } from './game-websocket.service';
import { UserService } from '../user/user-service';

describe('GameWebSocketService.buildSocketUrl (https origin)', () => {
  let service: GameWebSocketService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        GameWebSocketService,
        { provide: UserService, useValue: { getCurrentUser: () => null } },
      ],
    });
    service = TestBed.inject(GameWebSocketService);
  });

  it('uses wss:// for an https page', () => {
    expect(window.location.protocol).toBe('https:');
    expect(window.location.host).toBe('poker.example.com');

    expect(service.buildSocketUrl('game-1', 'tok')).toBe(
      'wss://poker.example.com/ws/games/game-1?token=tok'
    );
  });
});
