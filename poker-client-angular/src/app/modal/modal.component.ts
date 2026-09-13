import { Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { ModalService } from './modal.service';

export interface Modal {
    modalOptions: ModalOptions;
}

export class ModalButton {
    label: string = 'Button';
    type!: 'submit' | 'cancel' | 'other';
}

export class ModalOptions {
    constructor(public id: string, public title: string, public buttons: ModalButton[]) {        
    }
}

@Component({
    selector: 'app-modal',
    standalone: true,
    templateUrl: './modal.component.html',
    styleUrl: './modal.component.css',
})
export class ModalComponent {

    @Input()
    public options!: ModalOptions;

    constructor(private modalService: ModalService) {
    }

    public closeDialog() {
        this.modalService.closeDialog(this.options.id);
    }
}
