import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CashGameListComponent } from './cash-game-list.component';

describe('CashGameListComponent', () => {
  let component: CashGameListComponent;
  let fixture: ComponentFixture<CashGameListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CashGameListComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(CashGameListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
