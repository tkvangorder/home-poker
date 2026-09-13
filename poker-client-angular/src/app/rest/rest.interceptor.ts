import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { UserService } from '../user/user-service';
import { inject } from '@angular/core';
import { EMPTY, catchError, empty, throwError } from 'rxjs';
import { SystemError, ValidationError } from '../error-handling/error-models';
import { Router } from '@angular/router';
import { ModalService } from '../modal/modal.service';

export const restInterceptor: HttpInterceptorFn = (req, next) => {
  const userService = inject(UserService);
  const modalService = inject(ModalService);

  const { token } = userService.getCurrentUser() ?? {};

  // If the user is logged in, pass the token in the Authorization header
  if (token) {
    req = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
      },
    });
  }
  return next(req).pipe(
    catchError((err: any) => {
      if (err instanceof HttpErrorResponse) {
        const message = err.error?.message ? err.error.message : err.message;
        // Handle HTTP errors
        if (err.status === 403) {
          modalService.closeAll();
          userService.logout();

          return throwError(
            () =>
              new SystemError(
                String(err.status),
                'Your session has expired. Please log in again.',
                err
              )
          );
        } else if (err.status >= 400 && err.status < 500) {
          // Specific handling for unauthorized errors
          return throwError(
            () => new ValidationError(String(err.status), message)
          );
          // You might trigger a re-authentication flow or redirect the user here
        } else {
          return throwError(
            () => new SystemError(String(err.status), message, err)
          );
        }
      } else {
        // Re-throw the error to propagate it further
        return throwError(() => err);
      }
    })
  );
};
