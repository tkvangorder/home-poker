import { Component, Input } from '@angular/core';
import { ToastMessageType } from './toaster.service';

@Component({
    selector: 'app-toast-message',
    imports: [],
    templateUrl: './toast-message.component.html'
})
export class ToastMessageComponent {
  @Input() message: string = '';
  @Input() type: ToastMessageType = 'info';
}
