import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { airlineStaffGuard } from './airline-staff.guard';
import { TokenStorageService } from '../services/token-storage.service';

describe('airlineStaffGuard', () => {
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

  it('allows airline staff role', () => {
    tokenStorageSpy.getToken.and.returnValue('jwt');
    tokenStorageSpy.getRole.and.returnValue('AIRLINE_STAFF');

    const result = TestBed.runInInjectionContext(() =>
      airlineStaffGuard({} as any, { url: '/airline/dashboard' } as any)
    );

    expect(result).toBeTrue();
  });

  it('blocks non-airline users', () => {
    tokenStorageSpy.getToken.and.returnValue('jwt');
    tokenStorageSpy.getRole.and.returnValue('PASSENGER');

    const result = TestBed.runInInjectionContext(() =>
      airlineStaffGuard({} as any, { url: '/airline/dashboard' } as any)
    );

    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/']);
    expect(result).toEqual({ commands: ['/'] } as any);
  });

  it('redirects to login when token is missing', () => {
    tokenStorageSpy.getToken.and.returnValue(null);

    const result = TestBed.runInInjectionContext(() =>
      airlineStaffGuard({} as any, { url: '/airline/flights' } as any)
    );

    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/auth/login'], {
      queryParams: { returnUrl: '/airline/flights', role: 'AIRLINE_STAFF' }
    });
    expect(result).toEqual({ commands: ['/auth/login'] } as any);
  });
});
