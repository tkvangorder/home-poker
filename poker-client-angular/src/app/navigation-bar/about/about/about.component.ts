import { Component } from '@angular/core';
import {
  Modal,
  ModalOptions,
  ModalComponent,
} from '../../../modal/modal.component';

@Component({
    selector: 'app-about',
    templateUrl: './about.component.html',
    imports: [ModalComponent]
})
export class AboutComponent implements Modal {
  modalOptions: ModalOptions = {
    id: 'about',
    title: 'About',
    buttons: [{ label: 'OK', type: 'cancel' }],
  };
}
