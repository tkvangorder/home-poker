import { ApplicationConfig, ErrorHandler } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { GlobalErrorHandler } from './error-handling/global-error-handler';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { restInterceptor } from './rest/rest.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(withInterceptors([restInterceptor])),
    provideRouter(routes),
    {
      provide: ErrorHandler,
      useClass: GlobalErrorHandler,
      deps: [], // Add any dependencies here needed for the error handler.
    },
  ],
};
