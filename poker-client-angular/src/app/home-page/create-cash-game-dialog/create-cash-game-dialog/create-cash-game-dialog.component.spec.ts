import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CreateCashGameDialogComponent } from './create-cash-game-dialog.component';

describe('CreateCashGameDialogComponent', () => {
  let component: CreateCashGameDialogComponent;
  let fixture: ComponentFixture<CreateCashGameDialogComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CreateCashGameDialogComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(CreateCashGameDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
