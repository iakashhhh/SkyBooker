import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { TokenStorageService } from '../services/token-storage.service';

export const adminGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const tokenStorageService = inject(TokenStorageService);
  const token = tokenStorageService.getToken();

  if (!token) {
    return router.createUrlTree(['/auth/login'], {
      queryParams: { returnUrl: state.url, role: 'ADMIN' }
    });
  }

  return tokenStorageService.getRole() === 'ADMIN'
    ? true
    : router.createUrlTree(['/']);
};

