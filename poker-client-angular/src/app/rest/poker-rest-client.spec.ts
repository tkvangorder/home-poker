import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { PokerRestClient } from './poker-rest-client';

describe('PokerRestClient', () => {
  let client: PokerRestClient;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    client = TestBed.inject(PokerRestClient);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts login to the same-origin /auth/login path with no /api prefix', () => {
    client.login('alice', 'secret').subscribe();

    const req = http.expectOne('/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ loginId: 'alice', password: 'secret' });
    req.flush({});
  });

  it('posts game searches to /cash-games/search', () => {
    client.searchGames({} as any).subscribe();

    const req = http.expectOne('/cash-games/search');
    expect(req.request.method).toBe('POST');
    req.flush([]);
  });
});
