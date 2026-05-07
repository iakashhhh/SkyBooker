import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { authGuard } from './auth.guard';

describe('authGuard', () => {
  beforeEach(() => {
    const routerSpy = jasmine.createSpyObj<Router>('Router', ['createUrlTree']);
    routerSpy.createUrlTree.and.returnValue({ redirected: true } as any);

    TestBed.configureTestingModule({
      providers: [{ provide: Router, useValue: routerSpy }]
    });

    localStorage.clear();
  });

  it('allows access when token exists', () => {
    localStorage.setItem('skybooker_token', 'jwt');

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as any, { url: '/bookings' } as any)
    );

    expect(result).toBeTrue();
  });

  it('redirects to login when token missing', () => {
    const router = TestBed.inject(Router) as jasmine.SpyObj<Router>;

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as any, { url: '/payment' } as any)
    );

    expect(router.createUrlTree).toHaveBeenCalledWith(['/auth/login'], {
      queryParams: { returnUrl: '/payment' }
    });
    expect(result).toEqual({ redirected: true } as any);
  });

  it('treats empty token as unauthenticated', () => {
    localStorage.setItem('skybooker_token', '');
    const router = TestBed.inject(Router) as jasmine.SpyObj<Router>;

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as any, { url: '/bookings/1' } as any)
    );

    expect(router.createUrlTree).toHaveBeenCalledWith(['/auth/login'], {
      queryParams: { returnUrl: '/bookings/1' }
    });
    expect(result).toEqual({ redirected: true } as any);
  });
});
