import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { TokenStorageService } from '../services/token-storage.service';

/**
 * This guard protects operations pages for admin and airline staff roles only.
 */
export const operationsGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const tokenStorageService = inject(TokenStorageService);
  const token = tokenStorageService.getToken();

  if (!token) {
    return router.createUrlTree(['/auth/login'], {
      queryParams: {
        returnUrl: state.url
      }
    });
  }

  const role = tokenStorageService.getRole();
  if (role === 'ADMIN' || role === 'AIRLINE_STAFF') {
    return true;
  }

  return router.createUrlTree(['/']);
};
