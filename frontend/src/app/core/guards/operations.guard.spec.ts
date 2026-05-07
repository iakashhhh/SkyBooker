import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { operationsGuard } from './operations.guard';
import { TokenStorageService } from '../services/token-storage.service';

describe('operationsGuard', () => {
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

  it('allows ADMIN and AIRLINE_STAFF roles', () => {
    tokenStorageSpy.getToken.and.returnValue('jwt');
    tokenStorageSpy.getRole.and.returnValue('ADMIN');

    const adminResult = TestBed.runInInjectionContext(() =>
      operationsGuard({} as any, { url: '/ops' } as any)
    );
    expect(adminResult).toBeTrue();

    tokenStorageSpy.getRole.and.returnValue('AIRLINE_STAFF');
    const staffResult = TestBed.runInInjectionContext(() =>
      operationsGuard({} as any, { url: '/ops' } as any)
    );
    expect(staffResult).toBeTrue();
  });

  it('redirects passenger role to home', () => {
    tokenStorageSpy.getToken.and.returnValue('jwt');
    tokenStorageSpy.getRole.and.returnValue('PASSENGER');

    const result = TestBed.runInInjectionContext(() =>
      operationsGuard({} as any, { url: '/ops' } as any)
    );

    expect(result).toEqual({ commands: ['/'] } as any);
  });

  it('redirects unauthenticated user to login', () => {
    tokenStorageSpy.getToken.and.returnValue(null);

    const result = TestBed.runInInjectionContext(() =>
      operationsGuard({} as any, { url: '/ops/checkin' } as any)
    );

    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/auth/login'], {
      queryParams: { returnUrl: '/ops/checkin' }
    });
    expect(result).toEqual({ commands: ['/auth/login'] } as any);
  });
});
