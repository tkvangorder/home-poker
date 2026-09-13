import { TestBed } from '@angular/core/testing';

import { SvgLoaderService } from './svg-loader.service';

describe('SvgLoaderService', () => {
  let service: SvgLoaderService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(SvgLoaderService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
