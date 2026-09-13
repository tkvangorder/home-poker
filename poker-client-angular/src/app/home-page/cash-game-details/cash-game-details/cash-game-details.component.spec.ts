import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CashGameDetailsComponent } from './cash-game-details.component';

describe('CashGameDetailsComponent', () => {
  let component: CashGameDetailsComponent;
  let fixture: ComponentFixture<CashGameDetailsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CashGameDetailsComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(CashGameDetailsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
