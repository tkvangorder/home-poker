/**
 * NOTE ON TEST TECHNIQUE: The brief's original stubLocation() helper used
 * `Object.defineProperty(window, 'location', { configurable: true, value: ... })`
 * to fake window.location per test. Under the jsdom bundled with this repo's
 * pinned jest-environment-jsdom (jsdom 26.1.0), `window.location`'s own
 * property descriptor is `{ get, set, enumerable: true, configurable: false }`
 * (verified directly), so Object.defineProperty, delete, jest.spyOn(...,'get'),
 * and jest.replaceProperty all throw "Property `location` is not declared
 * configurable" / "Cannot redefine property: location" when targeting it or
 * its own `protocol`/`host` accessors -- there is no in-test way to mutate it.
 * jsdom's own docs confirm this is deliberate: only the code that *creates*
 * the JSDOM instance can change its URL, via `dom.reconfigure({ url })"; code
 * running inside the window cannot (see jsdom README, "Reconfiguring the
 * jsdom"). Jest exposes that capability per test *file* via the
 * `@jest-environment-options` docblock below, so this file pins a real
 * `http://localhost:4200/` origin and asserts buildSocketUrl's ws:// branch
 * against jsdom's genuine, unmocked window.location. The wss:// branch is
 * covered the same way in the sibling file
 * game-websocket.service.wss.spec.ts, pinned to an https origin.
 *
 * @jest-environment jsdom
 * @jest-environment-options {"url": "http://localhost:4200/"}
 */
import { TestBed } from '@angular/core/testing';
import { GameWebSocketService } from './game-websocket.service';
import { UserService } from '../user/user-service';

describe('GameWebSocketService.buildSocketUrl (http origin)', () => {
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

  it('uses ws:// and the page host for an http page', () => {
    expect(window.location.protocol).toBe('http:');
    expect(window.location.host).toBe('localhost:4200');

    expect(service.buildSocketUrl('game-1', 'tok')).toBe(
      'ws://localhost:4200/ws/games/game-1?token=tok'
    );
  });
});
