import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

/**
 * This guard protects pages that require login.
 * It redirects anonymous users to the login page.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const token = localStorage.getItem('skybooker_token');

  if (token) {
    return true;
  }

  return router.createUrlTree(['/auth/login'], {
    queryParams: {
      returnUrl: state.url
    }
  });
};
