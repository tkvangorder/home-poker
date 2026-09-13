import { ErrorHandler, inject } from '@angular/core';
import { ToasterService } from '../toaster/toaster.service';
import { ValidationError } from './error-models';

export class GlobalErrorHandler implements ErrorHandler {
  private toastService = inject(ToasterService);

  handleError(error: any): void {
    if (error instanceof ValidationError) {
      //Do not show or log validation errors
      return;
    }
    this.toastService.displayToast({
      message: error,
      type: 'error',
    });

    console.log(error);
  }
}
