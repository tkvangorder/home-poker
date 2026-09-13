import {
  ChangeDetectorRef,
  Component,
  DestroyRef,
  OnInit,
  ViewChild,
  ViewContainerRef,
  inject,
} from '@angular/core';
import { ToasterService } from './toaster.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ToastMessageComponent } from './toast-message.component';

@Component({
  selector: 'app-toast-display',
  imports: [],
  templateUrl: './toast-display.component.html',
})
export class ToastDisplayComponent implements OnInit {
  @ViewChild('toaster', { read: ViewContainerRef, static: true })
  toastContainer!: ViewContainerRef;

  changeDetectorRef = inject(ChangeDetectorRef);

  constructor(
    private toastService: ToasterService,
    private destroyRef: DestroyRef,
  ) {}
  ngOnInit(): void {
    this.toastService.toastSubscription
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (toastMessage) => {
          const toastComponent = this.toastContainer.createComponent(
            ToastMessageComponent,
          );
          toastComponent.instance.message = toastMessage.message;
          toastComponent.instance.type = toastMessage.type;
          this.changeDetectorRef.detectChanges();
          setTimeout(() => {
            toastComponent.destroy();
          }, 5000);
        },
      });
  }
}
