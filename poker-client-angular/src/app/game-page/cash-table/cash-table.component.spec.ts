import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CashTableComponent } from './cash-table.component';

describe('CashTableComponent', () => {
  let component: CashTableComponent;
  let fixture: ComponentFixture<CashTableComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CashTableComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(CashTableComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
