import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { UserService } from './user/user-service';

export const authenticationGuard: CanActivateFn = (next, state) => {
  const userService = inject(UserService);
  const router = inject(Router);

  if (!userService.isLoggedIn()) {
    router.navigate(['']);
    return false;
  }
  return true;
};

export const loggedInGuard: CanActivateFn = (next, state) => {
  const userService = inject(UserService);
  const router = inject(Router);

  if (userService.isLoggedIn()) {
    router.navigate(['/home']);
    return false;
  }
  return true;
};
