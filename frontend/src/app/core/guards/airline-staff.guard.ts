import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { TokenStorageService } from '../services/token-storage.service';

export const airlineStaffGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const tokenStorageService = inject(TokenStorageService);
  const token = tokenStorageService.getToken();

  if (!token) {
    return router.createUrlTree(['/auth/login'], {
      queryParams: { returnUrl: state.url, role: 'AIRLINE_STAFF' }
    });
  }

  return tokenStorageService.getRole() === 'AIRLINE_STAFF'
    ? true
    : router.createUrlTree(['/']);
};

