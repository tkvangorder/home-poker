import {
  ApplicationRef,
  ComponentRef,
  EnvironmentInjector,
  Injectable,
  Type,
  createComponent,
} from '@angular/core';
import { Modal, ModalComponent } from './modal.component';

@Injectable({
  providedIn: 'root',
})
export class ModalService {
  private dynamicModals = new Map<string, ComponentRef<unknown>>();

  constructor(
    private appRef: ApplicationRef,
    private injector: EnvironmentInjector
  ) {}

  openDialog(modal: Type<unknown>) {
    const modalComponent = createComponent(modal, {
      environmentInjector: this.injector,
    });

    this.dynamicModals.set(this.getModalId(modalComponent), modalComponent);
    document.body.appendChild(modalComponent.location.nativeElement);
    this.appRef.attachView(modalComponent.hostView);
  }

  close(modal: Modal) {
    const id = modal.modalOptions.id;
    const ref = this.dynamicModals.get(id);

    if (!ref) {
      throw new Error(`modal '${id}' not found`);
    }
    this.dynamicModals.delete(id);
    document.body.removeChild(ref.location.nativeElement);
  }

  closeDialog(id: string) {
    const modal = this.dynamicModals.get(id);

    if (!modal) {
      throw new Error(`modal '${id}' not found`);
    }
    this.dynamicModals.delete(id);
    document.body.removeChild(modal.location.nativeElement);
  }

  closeAll() {
    this.dynamicModals.forEach((modal) => {
      document.body.removeChild(modal.location.nativeElement);
    });
    this.dynamicModals.clear();
  }

  private getModalId(modal: ComponentRef<unknown>) {
    return (modal.instance as Modal).modalOptions.id;
  }
}
