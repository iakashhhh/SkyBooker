import { TestBed } from '@angular/core/testing';
import { Router, convertToParamMap } from '@angular/router';

import { TokenStorageService } from '../services/token-storage.service';
import { guestGuard } from './guest.guard';

describe('guestGuard', () => {
  let tokenStorageSpy: jasmine.SpyObj<TokenStorageService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    tokenStorageSpy = jasmine.createSpyObj<TokenStorageService>('TokenStorageService', ['getToken', 'getRole']);
    routerSpy = jasmine.createSpyObj<Router>('Router', ['parseUrl']);
    routerSpy.parseUrl.and.callFake((url: string) => ({ redirectedTo: url } as any));

    TestBed.configureTestingModule({
      providers: [
        { provide: TokenStorageService, useValue: tokenStorageSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });
  });

  it('allows guest access when not authenticated', () => {
    tokenStorageSpy.getToken.and.returnValue(null);

    const result = TestBed.runInInjectionContext(() =>
      guestGuard({ queryParamMap: convertToParamMap({}) } as any, {} as any)
    );

    expect(result).toBeTrue();
  });

  it('redirects to returnUrl when logged in and returnUrl is present', () => {
    tokenStorageSpy.getToken.and.returnValue('jwt');

    const result = TestBed.runInInjectionContext(() =>
      guestGuard({ queryParamMap: convertToParamMap({ returnUrl: '/payment' }) } as any, {} as any)
    );

    expect(routerSpy.parseUrl).toHaveBeenCalledWith('/payment');
    expect(result).toEqual({ redirectedTo: '/payment' } as any);
  });

  it('routes authenticated admin to admin dashboard', () => {
    tokenStorageSpy.getToken.and.returnValue('jwt');
    tokenStorageSpy.getRole.and.returnValue('ADMIN');

    const result = TestBed.runInInjectionContext(() =>
      guestGuard({ queryParamMap: convertToParamMap({}) } as any, {} as any)
    );

    expect(result).toEqual({ redirectedTo: '/admin/dashboard' } as any);
  });
});
