import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { TokenStorageService } from '../services/token-storage.service';

/**
 * This guard prevents logged-in users from opening login/register pages again.
 */
export const guestGuard: CanActivateFn = (route) => {
  const router = inject(Router);
  const tokenStorageService = inject(TokenStorageService);

  if (tokenStorageService.getToken()) {
    const returnUrl = route.queryParamMap.get('returnUrl');
    const role = tokenStorageService.getRole();
    if (returnUrl) {
      return router.parseUrl(returnUrl);
    }
    if (role === 'ADMIN') {
      return router.parseUrl('/admin/dashboard');
    }
    if (role === 'AIRLINE_STAFF') {
      return router.parseUrl('/airline/dashboard');
    }
    return router.parseUrl('/');
  }

  return true;
};
