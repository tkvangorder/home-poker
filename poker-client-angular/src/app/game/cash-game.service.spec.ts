import { TestBed } from '@angular/core/testing';

import { CashGameService } from './cash-game.service';

describe('CashGameService', () => {
  let service: CashGameService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(CashGameService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
