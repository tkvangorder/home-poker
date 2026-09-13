import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ModalComponent, ModalOptions } from './modal.component';

describe('ModalComponent', () => {
  let component: ModalComponent;
  let fixture: ComponentFixture<ModalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ModalComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ModalComponent);
    component = fixture.componentInstance;
    component.options = new ModalOptions('modal-1', 'Confirm Action', []);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
