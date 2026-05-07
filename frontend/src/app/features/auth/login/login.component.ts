import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ActivatedRoute, Router } from '@angular/router';

import { AuthApiService } from '../../../core/services/auth-api.service';
import { BookingJourneyService } from '../../../core/services/booking-journey.service';
import { environment } from '../../../core/config/environment';

/**
 * This component handles user login form and authentication call.
 * After successful login, it resumes booking flow or routes by role.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent implements OnInit {
  isLoading = false;
  errorMessage = '';
  selectedRole: 'PASSENGER' | 'AIRLINE_STAFF' = 'PASSENGER';
  forgotMode = false;
  forgotMessage = '';
  readonly googleAuthConfigured = Boolean(environment.googleClientId);

  readonly form = this.formBuilder.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });

  readonly forgotForm = this.formBuilder.group({
    email: ['', [Validators.required, Validators.email]],
    otpCode: [''],
    newPassword: ['']
  });

  constructor(
    private readonly formBuilder: FormBuilder,
    private readonly authApiService: AuthApiService,
    private readonly bookingJourneyService: BookingJourneyService,
    private readonly activatedRoute: ActivatedRoute,
    private readonly router: Router
  ) {
    const requestedRole = this.activatedRoute.snapshot.queryParamMap.get('role');
    if (requestedRole === 'AIRLINE_STAFF' || requestedRole === 'PASSENGER') {
      this.selectedRole = requestedRole;
    }
  }

  ngOnInit(): void {
    this.redirectIfAlreadyLoggedIn();
    this.initializeGoogleLogin();
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    this.authApiService.login(this.form.getRawValue() as { email: string; password: string }).subscribe({
      next: (response) => {
        this.handlePostAuthSuccess(response.role);
      },
      error: () => {
        this.isLoading = false;
        this.errorMessage = 'Unable to sign in. Please check your credentials and try again.';
      }
    });
  }

  requestPasswordOtp(): void {
    if (this.forgotForm.controls.email.invalid) {
      this.forgotForm.controls.email.markAsTouched();
      return;
    }

    this.isLoading = true;
    this.forgotMessage = '';
    this.errorMessage = '';

    this.authApiService.forgotPassword({ email: String(this.forgotForm.controls.email.value ?? '').trim() }).subscribe({
      next: (response) => {
        this.isLoading = false;
        this.forgotMessage = response.message ?? 'Reset OTP sent if your email is registered.';
      },
      error: () => {
        this.isLoading = false;
        this.errorMessage = 'Unable to send reset OTP right now.';
      }
    });
  }

  resetPassword(): void {
    const email = String(this.forgotForm.controls.email.value ?? '').trim();
    const otpCode = String(this.forgotForm.controls.otpCode.value ?? '').trim();
    const newPassword = String(this.forgotForm.controls.newPassword.value ?? '').trim();

    if (!email || !otpCode || !newPassword) {
      this.forgotForm.markAllAsTouched();
      this.errorMessage = 'Email, OTP, and new password are required.';
      return;
    }

    this.isLoading = true;
    this.forgotMessage = '';
    this.errorMessage = '';

    this.authApiService.resetPassword({ email, otpCode, newPassword }).subscribe({
      next: (response) => {
        this.isLoading = false;
        this.forgotMessage = response.message ?? 'Password reset successful. You can now sign in.';
      },
      error: () => {
        this.isLoading = false;
        this.errorMessage = 'Unable to reset password.';
      }
    });
  }

  selectRole(role: 'PASSENGER' | 'AIRLINE_STAFF'): void {
    this.selectedRole = role;
  }

  toggleForgotMode(): void {
    this.forgotMode = !this.forgotMode;
    this.forgotMessage = '';
    this.errorMessage = '';
  }

  get pageTitle(): string {
    switch (this.selectedRole) {
      case 'AIRLINE_STAFF':
        return 'Staff access for airline operations';
      default:
        return 'Pick up your journey where you left it';
    }
  }

  get pageDescription(): string {
    if (this.isBookingReturn) {
      return 'Continue your booking without losing your selected seats or checkout progress.';
    }

    switch (this.selectedRole) {
      case 'AIRLINE_STAFF':
        return 'Manage airline and airport inventory with your staff workspace.';
      default:
        return 'Sign in to manage trips, payments, alerts, and saved bookings.';
    }
  }

  get isBookingReturn(): boolean {
    return this.activatedRoute.snapshot.queryParamMap.get('context') === 'booking'
      || this.bookingJourneyService.hasPendingBookingDraft();
  }

  get registerQueryParams(): Record<string, string> {
    return {
      ...this.activatedRoute.snapshot.queryParams,
      role: this.selectedRole
    };
  }

  get loginEmailInvalid(): boolean {
    const control = this.form.controls.email;
    return control.invalid && (control.dirty || control.touched);
  }

  get loginPasswordInvalid(): boolean {
    const control = this.form.controls.password;
    return control.invalid && (control.dirty || control.touched);
  }

  get shouldShowPasswordRules(): boolean {
    const control = this.form.controls.password;
    return control.dirty || control.touched;
  }

  get passwordRuleStates(): Array<{ label: string; valid: boolean }> {
    const value = String(this.form.controls.password.value ?? '');
    return [
      { label: 'At least 8 characters', valid: value.length >= 8 },
      { label: 'One uppercase letter', valid: /[A-Z]/.test(value) },
      { label: 'One lowercase letter', valid: /[a-z]/.test(value) },
      { label: 'One number', valid: /\d/.test(value) },
      { label: 'One special character', valid: /[@$!%*?&]/.test(value) }
    ];
  }

  private handlePostAuthSuccess(role: string): void {
    const returnUrl = String(this.activatedRoute.snapshot.queryParamMap.get('returnUrl') ?? '').trim();
    const context = String(this.activatedRoute.snapshot.queryParamMap.get('context') ?? '').trim();

    if (role === 'ADMIN' || role === 'AIRLINE_STAFF') {
      this.isLoading = false;
      this.router.navigateByUrl(returnUrl || this.resolveDefaultRoute(role));
      return;
    }

    this.bookingJourneyService.resumePendingBooking().subscribe({
      next: (booking) => {
        this.isLoading = false;

        if (booking) {
          this.bookingJourneyService.saveActiveBookingContext({
            bookingId: booking.bookingId,
            pnr: booking.pnrCode,
            flightId: booking.flightId,
            seatIds: booking.seatIds,
            userId: booking.userId,
            amount: booking.totalFare
          });
          this.router.navigate(['/passenger-details'], {
            queryParams: {
              bookingId: booking.bookingId,
              pnr: booking.pnrCode,
              userId: booking.userId,
              amount: booking.totalFare,
              resumed: true
            }
          });
          return;
        }

        if (context === 'booking' && returnUrl.startsWith('/passenger-details')) {
          const active = this.bookingJourneyService.getActiveBookingContext();
          if (active?.bookingId) {
            this.router.navigate(['/passenger-details'], {
              queryParams: {
                bookingId: active.bookingId,
                pnr: active.pnr,
                userId: active.userId,
                amount: active.amount
              }
            });
            return;
          }
          this.router.navigateByUrl('/flights/results');
          return;
        }

        this.router.navigateByUrl(returnUrl || this.resolveDefaultRoute(role));
      },
      error: () => {
        this.isLoading = false;
        this.router.navigateByUrl(returnUrl || this.resolveDefaultRoute(role));
      }
    });
  }

  private resolveDefaultRoute(role: string): string {
    if (role === 'ADMIN') {
      return '/admin/dashboard';
    }
    if (role === 'AIRLINE_STAFF') {
      return '/airline/dashboard';
    }
    return '/';
  }

  private redirectIfAlreadyLoggedIn(): void {
    const token = localStorage.getItem('skybooker_token');
    if (!token) {
      return;
    }

    const returnUrl = String(this.activatedRoute.snapshot.queryParamMap.get('returnUrl') ?? '').trim();
    const role = localStorage.getItem('skybooker_role') ?? '';
    this.router.navigateByUrl(returnUrl || this.resolveDefaultRoute(role));
  }

  private initializeGoogleLogin(): void {
    if (!environment.googleClientId) {
      return;
    }

    this.loadGoogleScript().then(() => {
      const google = (window as any).google;
      if (!google?.accounts?.id) {
        return;
      }

      google.accounts.id.initialize({
        client_id: environment.googleClientId,
        callback: (credentialResponse: { credential?: string }) => {
          if (!credentialResponse?.credential) {
            return;
          }
          this.handleGoogleCredential(credentialResponse.credential);
        }
      });

      google.accounts.id.renderButton(
        document.getElementById('google-login-btn'),
        { theme: 'outline', size: 'large', width: 320 }
      );
    }).catch(() => {
      this.errorMessage = 'Google login is currently unavailable.';
    });
  }

  showGoogleSetupMessage(): void {
    this.errorMessage = 'Google login is not configured yet. Please set GOOGLE_CLIENT_ID and rebuild frontend + auth-service.';
  }

  private handleGoogleCredential(idToken: string): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.authApiService.googleLogin({ idToken, role: this.selectedRole }).subscribe({
      next: (response) => this.handlePostAuthSuccess(response.role),
      error: (error) => {
        this.isLoading = false;
        this.errorMessage = error?.error?.message ?? 'Google login failed.';
      }
    });
  }

  private loadGoogleScript(): Promise<void> {
    if ((window as any).google?.accounts?.id) {
      return Promise.resolve();
    }

    return new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = 'https://accounts.google.com/gsi/client';
      script.async = true;
      script.defer = true;
      script.onload = () => resolve();
      script.onerror = () => reject();
      document.head.appendChild(script);
    });
  }
}
