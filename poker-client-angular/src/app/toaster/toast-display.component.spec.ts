import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ToastDisplayComponent } from './toast-display.component';

describe('ToastDisplayComponent', () => {
  let component: ToastDisplayComponent;
  let fixture: ComponentFixture<ToastDisplayComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ToastDisplayComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ToastDisplayComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
