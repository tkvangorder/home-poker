import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type ToastMessageType = 'info' | 'warning' | 'error' | 'success';

export interface ToastMessage {
  message: string;
  type: ToastMessageType;
}

@Injectable({
  providedIn: 'root',
})
export class ToasterService {
  public toastMessage = new BehaviorSubject<ToastMessage>({} as ToastMessage);
  public toastSubscription = this.toastMessage.asObservable();

  constructor() {}

  public displayToast(toastMessage: ToastMessage): void {
    this.toastMessage.next(toastMessage);
  }
}
