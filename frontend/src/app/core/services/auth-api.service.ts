import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

import { environment } from '../config/environment';
import {
  AuthResponse,
  ForgotPasswordRequest,
  GoogleLoginRequest,
  LoginRequest,
  ProfileResponse,
  RegisterRequest,
  ResetPasswordRequest
} from '../models/auth.models';
import { TokenStorageService } from './token-storage.service';

/**
 * This service calls backend auth APIs through API Gateway.
 * It stores token after successful login/register responses.
 */
@Injectable({
  providedIn: 'root'
})
export class AuthApiService {
  private readonly baseUrl = `${environment.apiBaseUrl}/auth`;

  constructor(
    private readonly httpClient: HttpClient,
    private readonly tokenStorageService: TokenStorageService
  ) {}

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.httpClient.post<AuthResponse>(`${this.baseUrl}/register`, request).pipe(
      tap((response) => this.storeAuth(response))
    );
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.httpClient.post<AuthResponse>(`${this.baseUrl}/login`, request).pipe(
      tap((response) => this.storeAuth(response))
    );
  }

  googleLogin(request: GoogleLoginRequest): Observable<AuthResponse> {
    return this.httpClient.post<AuthResponse>(`${this.baseUrl}/google`, request).pipe(
      tap((response) => this.storeAuth(response))
    );
  }

  getProfile(): Observable<ProfileResponse> {
    return this.httpClient.get<ProfileResponse>(`${this.baseUrl}/profile`).pipe(
      tap((profile) => {
        if (profile.userId) {
          this.tokenStorageService.saveUserId(profile.userId);
        }
      })
    );
  }

  logout(): void {
    this.tokenStorageService.clear();
  }

  forgotPassword(request: ForgotPasswordRequest): Observable<{ message: string }> {
    return this.httpClient.post<{ message: string }>(`${this.baseUrl}/password/forgot`, request);
  }

  resetPassword(request: ResetPasswordRequest): Observable<{ message: string }> {
    return this.httpClient.post<{ message: string }>(`${this.baseUrl}/password/reset`, request);
  }

  updateProfile(profile: Partial<ProfileResponse>): Observable<ProfileResponse> {
    return this.httpClient.put<ProfileResponse>(`${this.baseUrl}/profile`, profile);
  }

  changePassword(currentPassword: string, newPassword: string): Observable<{ message: string }> {
    return this.httpClient.put<{ message: string }>(`${this.baseUrl}/password`, {
      currentPassword,
      newPassword
    });
  }

  private storeAuth(response: AuthResponse): void {
    this.tokenStorageService.saveToken(response.token);
    this.tokenStorageService.saveRole(response.role);
    if (response.userId) {
      this.tokenStorageService.saveUserId(response.userId);
    }
  }
}
