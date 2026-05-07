/**
 * These interfaces represent request and response payloads for auth APIs.
 * Keeping these models centralized improves API contract consistency.
 */
export interface RegisterRequest {
  fullName: string;
  email: string;
  password: string;
  phone: string;
  passportNumber: string;
  nationality: string;
  dateOfBirth?: string;
  profilePhotoUrl?: string;
  airlineId?: number;
  role: 'PASSENGER' | 'ADMIN' | 'AIRLINE_STAFF';
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  email: string;
  role: string;
  userId: number;
}

export interface ProfileResponse {
  userId: number;
  fullName: string;
  email: string;
  phone: string;
  passportNumber: string;
  nationality: string;
  dateOfBirth?: string;
  profilePhotoUrl?: string;
  role: string;
  provider: string;
  active: boolean;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  email: string;
  otpCode: string;
  newPassword: string;
}

export interface GoogleLoginRequest {
  idToken: string;
  role: 'PASSENGER' | 'ADMIN' | 'AIRLINE_STAFF';
  airlineId?: number;
}
