import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { TokenStorageService } from '../services/token-storage.service';
import { adminGuard } from './admin.guard';

describe('adminGuard', () => {
  let tokenStorageSpy: jasmine.SpyObj<TokenStorageService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    tokenStorageSpy = jasmine.createSpyObj<TokenStorageService>('TokenStorageService', ['getToken', 'getRole']);
    routerSpy = jasmine.createSpyObj<Router>('Router', ['createUrlTree']);
    routerSpy.createUrlTree.and.callFake((commands: unknown[]) => ({ commands } as any));

    TestBed.configureTestingModule({
      providers: [
        { provide: TokenStorageService, useValue: tokenStorageSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });
  });

  it('allows admin user', () => {
    tokenStorageSpy.getToken.and.returnValue('jwt');
    tokenStorageSpy.getRole.and.returnValue('ADMIN');

    const result = TestBed.runInInjectionContext(() =>
      adminGuard({} as any, { url: '/admin/dashboard' } as any)
    );

    expect(result).toBeTrue();
  });

  it('redirects to login when unauthenticated', () => {
    tokenStorageSpy.getToken.and.returnValue(null);

    const result = TestBed.runInInjectionContext(() =>
      adminGuard({} as any, { url: '/admin/users' } as any)
    );

    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/auth/login'], {
      queryParams: { returnUrl: '/admin/users', role: 'ADMIN' }
    });
    expect(result).toEqual({ commands: ['/auth/login'] } as any);
  });

  it('redirects non-admin user to home', () => {
    tokenStorageSpy.getToken.and.returnValue('jwt');
    tokenStorageSpy.getRole.and.returnValue('PASSENGER');

    const result = TestBed.runInInjectionContext(() =>
      adminGuard({} as any, { url: '/admin/settings' } as any)
    );

    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/']);
    expect(result).toEqual({ commands: ['/'] } as any);
  });
});
