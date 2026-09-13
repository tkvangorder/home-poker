import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { TitlePageComponent } from './title-page.component';

describe('TitlePageComponent', () => {
  let component: TitlePageComponent;
  let fixture: ComponentFixture<TitlePageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TitlePageComponent],
      providers: [provideRouter([])]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(TitlePageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
