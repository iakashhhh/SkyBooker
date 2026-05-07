import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';

import { AuthResponse, ProfileResponse } from '../models/auth.models';
import { AuthApiService } from './auth-api.service';
import { TokenStorageService } from './token-storage.service';

describe('AuthApiService', () => {
  let service: AuthApiService;
  let httpMock: HttpTestingController;
  let tokenStorageSpy: jasmine.SpyObj<TokenStorageService>;

  beforeEach(() => {
    tokenStorageSpy = jasmine.createSpyObj<TokenStorageService>('TokenStorageService', [
      'saveToken',
      'saveRole',
      'saveUserId',
      'clear'
    ]);

    TestBed.configureTestingModule({
      providers: [
        AuthApiService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TokenStorageService, useValue: tokenStorageSpy }
      ]
    });

    service = TestBed.inject(AuthApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('stores auth data after login', () => {
    const authResponse: AuthResponse = {
      token: 'jwt-token',
      email: 'akash@test.com',
      role: 'PASSENGER',
      userId: 42
    };

    service.login({ email: 'akash@test.com', password: 'Password@123' }).subscribe((response) => {
      expect(response).toEqual(authResponse);
    });

    const request = httpMock.expectOne('http://localhost:8080/auth/login');
    expect(request.request.method).toBe('POST');
    request.flush(authResponse);

    expect(tokenStorageSpy.saveToken).toHaveBeenCalledWith('jwt-token');
    expect(tokenStorageSpy.saveRole).toHaveBeenCalledWith('PASSENGER');
    expect(tokenStorageSpy.saveUserId).toHaveBeenCalledWith(42);
  });

  it('stores user id when profile contains it', () => {
    const profile: ProfileResponse = {
      userId: 77,
      fullName: 'Akash Sharma',
      email: 'akash@test.com',
      phone: '9999999999',
      passportNumber: 'P1234567',
      nationality: 'Indian',
      role: 'PASSENGER',
      provider: 'LOCAL',
      active: true
    };

    service.getProfile().subscribe((response) => {
      expect(response.userId).toBe(77);
    });

    const request = httpMock.expectOne('http://localhost:8080/auth/profile');
    expect(request.request.method).toBe('GET');
    request.flush(profile);

    expect(tokenStorageSpy.saveUserId).toHaveBeenCalledWith(77);
  });

  it('clears local auth data on logout', () => {
    service.logout();
    expect(tokenStorageSpy.clear).toHaveBeenCalled();
  });

  it('register stores auth data and posts to register endpoint', () => {
    const authResponse: AuthResponse = {
      token: 'register-token',
      email: 'new@test.com',
      role: 'PASSENGER',
      userId: 15
    };

    service.register({
      fullName: 'Akash Sharma',
      email: 'new@test.com',
      password: 'Password@123',
      phone: '9999999999',
      passportNumber: 'P1234567',
      nationality: 'Indian',
      role: 'PASSENGER'
    }).subscribe((response) => {
      expect(response.token).toBe('register-token');
    });

    const request = httpMock.expectOne('http://localhost:8080/auth/register');
    expect(request.request.method).toBe('POST');
    request.flush(authResponse);

    expect(tokenStorageSpy.saveToken).toHaveBeenCalledWith('register-token');
    expect(tokenStorageSpy.saveRole).toHaveBeenCalledWith('PASSENGER');
  });

  it('forwards forgot and reset password requests to backend', () => {
    service.forgotPassword({ email: 'akash@test.com' }).subscribe((response) => {
      expect(response.message).toBe('sent');
    });
    const forgotRequest = httpMock.expectOne('http://localhost:8080/auth/password/forgot');
    expect(forgotRequest.request.method).toBe('POST');
    forgotRequest.flush({ message: 'sent' });

    service.resetPassword({ email: 'akash@test.com', otpCode: '123456', newPassword: 'Password@123' }).subscribe((response) => {
      expect(response.message).toBe('reset');
    });
    const resetRequest = httpMock.expectOne('http://localhost:8080/auth/password/reset');
    expect(resetRequest.request.method).toBe('POST');
    resetRequest.flush({ message: 'reset' });
  });
});
