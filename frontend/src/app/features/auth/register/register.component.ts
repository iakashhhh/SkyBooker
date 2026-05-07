import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ActivatedRoute, Router } from '@angular/router';

import { AuthApiService } from '../../../core/services/auth-api.service';
import { RegisterRequest } from '../../../core/models/auth.models';
import { BookingJourneyService } from '../../../core/services/booking-journey.service';
import { AirlineAirportApiService, AirlineRecord } from '../../../core/services/airline-airport-api.service';
import { catchError, of } from 'rxjs';
import { environment } from '../../../core/config/environment';

/**
 * This component handles new user registration flow.
 * It sends role, passport, and nationality fields to backend.
 */
@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css'
})
export class RegisterComponent implements OnInit {
  isLoading = false;
  errorMessage = '';
  selectedRole: 'PASSENGER' | 'AIRLINE_STAFF' = 'PASSENGER';
  airlineOptions: AirlineRecord[] = [];
  readonly googleAuthConfigured = Boolean(environment.googleClientId);

  private readonly namePattern = /^[A-Za-z .'-]{2,80}$/;
  private readonly passwordPattern = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,20}$/;
  private readonly phonePattern = /^\+?[0-9]{10,15}$/;
  private readonly passportPattern = /^[A-Z0-9]{6,12}$/;
  private readonly nationalityPattern = /^[A-Za-z ]{2,60}$/;

  readonly form = this.formBuilder.group({
    fullName: ['', [Validators.required, Validators.pattern(this.namePattern)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.pattern(this.passwordPattern)]],
    confirmPassword: ['', Validators.required],
    phone: ['', [Validators.required, Validators.pattern(this.phonePattern)]],
    passportNumber: ['', [Validators.required, Validators.pattern(this.passportPattern)]],
    nationality: ['', [Validators.required, Validators.pattern(this.nationalityPattern)]],
    dateOfBirth: [''],
    role: ['PASSENGER' as RegisterRequest['role'], Validators.required],
    airlineId: ['']
  }, { validators: this.passwordsMatchValidator });

  constructor(
    private readonly formBuilder: FormBuilder,
    private readonly authApiService: AuthApiService,
    private readonly bookingJourneyService: BookingJourneyService,
    private readonly airlineAirportApiService: AirlineAirportApiService,
    private readonly activatedRoute: ActivatedRoute,
    private readonly router: Router
  ) {
    const requestedRole = this.activatedRoute.snapshot.queryParamMap.get('role');
    if (requestedRole === 'AIRLINE_STAFF' || requestedRole === 'PASSENGER') {
      this.selectedRole = requestedRole;
      this.form.controls.role.setValue(requestedRole);
    }
  }

  ngOnInit(): void {
    this.loadAirlines();
    this.initializeGoogleSignup();
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();
    const request: RegisterRequest = {
      fullName: String(raw.fullName ?? '').trim(),
      email: String(raw.email ?? '').trim(),
      password: String(raw.password ?? '').trim(),
      phone: String(raw.phone ?? '').trim(),
      passportNumber: String(raw.passportNumber ?? '').trim().toUpperCase(),
      nationality: String(raw.nationality ?? '').trim(),
      role: (raw.role ?? 'PASSENGER') as RegisterRequest['role'],
      dateOfBirth: raw.dateOfBirth ? String(raw.dateOfBirth) : undefined,
      airlineId: this.selectedRole === 'AIRLINE_STAFF' ? Number(raw.airlineId || 0) : undefined
    };

    if (request.role === 'AIRLINE_STAFF' && (!request.airlineId || request.airlineId < 1)) {
      this.errorMessage = 'Please select an airline for airline staff registration.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    this.authApiService.register(request).subscribe({
      next: (response) => {
        this.handlePostAuthSuccess(response.role, request);
      },
      error: () => {
        this.isLoading = false;
        this.errorMessage = 'Unable to create account. Please verify details and try again.';
      }
    });
  }

  selectRole(role: RegisterRequest['role']): void {
    if (role === 'ADMIN') {
      return;
    }
    this.selectedRole = role;
    this.form.controls.role.setValue(role);
    if (role !== 'AIRLINE_STAFF') {
      this.form.controls.airlineId.setValue('');
    }
  }

  get pageTitle(): string {
    switch (this.selectedRole) {
      case 'AIRLINE_STAFF':
        return 'Create your airline staff workspace';
      default:
        return 'Create your SkyBooker travel account';
    }
  }

  get pageDescription(): string {
    if (this.isBookingReturn) {
      return 'Register now and jump straight back into checkout without starting your booking again.';
    }

    switch (this.selectedRole) {
      case 'AIRLINE_STAFF':
        return 'Use this account to manage airline and airport inventory from the operations hub.';
      default:
        return 'Save bookings, manage payments, and travel with a smoother modern checkout flow.';
    }
  }

  get isBookingReturn(): boolean {
    return this.activatedRoute.snapshot.queryParamMap.get('context') === 'booking'
      || this.bookingJourneyService.hasPendingBookingDraft();
  }

  get loginQueryParams(): Record<string, string> {
    return {
      ...this.activatedRoute.snapshot.queryParams,
      role: this.selectedRole
    };
  }

  get emailInvalid(): boolean {
    const control = this.form.controls.email;
    return control.invalid && (control.dirty || control.touched);
  }

  get confirmPasswordInvalid(): boolean {
    const control = this.form.controls.confirmPassword;
    const mismatch = this.form.hasError('passwordMismatch');
    return (control.dirty || control.touched) && (control.invalid || mismatch);
  }

  get confirmPasswordMessage(): string {
    if (this.form.controls.confirmPassword.hasError('required')) {
      return 'Please confirm your password.';
    }
    return 'Passwords do not match.';
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

  private handlePostAuthSuccess(role: string, request: RegisterRequest): void {
    const returnUrl = String(this.activatedRoute.snapshot.queryParamMap.get('returnUrl') ?? '').trim();
    const context = String(this.activatedRoute.snapshot.queryParamMap.get('context') ?? '').trim();

    this.bookingJourneyService.resumePendingBooking({
      contactEmail: request.email,
      contactPhone: request.phone
    }).subscribe({
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

  private loadAirlines(): void {
    this.airlineAirportApiService.getAirlines().pipe(catchError(() => of([] as AirlineRecord[]))).subscribe((airlines) => {
      this.airlineOptions = airlines
        .filter((airline) => (airline.active ?? airline.isActive ?? true) && airline.airlineId)
        .sort((a, b) => a.name.localeCompare(b.name));
    });
  }

  private initializeGoogleSignup(): void {
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
        document.getElementById('google-register-btn'),
        { theme: 'outline', size: 'large', width: 320 }
      );
    }).catch(() => {
      this.errorMessage = 'Google signup is currently unavailable.';
    });
  }

  showGoogleSetupMessage(): void {
    this.errorMessage = 'Google signup is not configured yet. Please set GOOGLE_CLIENT_ID and rebuild frontend + auth-service.';
  }

  private handleGoogleCredential(idToken: string): void {
    this.isLoading = true;
    this.errorMessage = '';

    const airlineId = this.selectedRole === 'AIRLINE_STAFF'
      ? Number(this.form.controls.airlineId.value || 0)
      : undefined;

    if (this.selectedRole === 'AIRLINE_STAFF' && (!airlineId || airlineId < 1)) {
      this.isLoading = false;
      this.errorMessage = 'Please select an airline before continuing with Google.';
      return;
    }

    const fallbackRequest = this.buildRequestFromForm();

    this.authApiService.googleLogin({ idToken, role: this.selectedRole, airlineId }).subscribe({
      next: (response) => this.handlePostAuthSuccess(response.role, fallbackRequest),
      error: (error) => {
        this.isLoading = false;
        this.errorMessage = error?.error?.message ?? 'Google signup failed.';
      }
    });
  }

  private buildRequestFromForm(): RegisterRequest {
    const raw = this.form.getRawValue();
    return {
      fullName: String(raw.fullName ?? '').trim(),
      email: String(raw.email ?? '').trim(),
      password: String(raw.password ?? '').trim(),
      phone: String(raw.phone ?? '').trim(),
      passportNumber: String(raw.passportNumber ?? '').trim().toUpperCase(),
      nationality: String(raw.nationality ?? '').trim(),
      role: (raw.role ?? 'PASSENGER') as RegisterRequest['role'],
      dateOfBirth: raw.dateOfBirth ? String(raw.dateOfBirth) : undefined,
      airlineId: this.selectedRole === 'AIRLINE_STAFF' ? Number(raw.airlineId || 0) : undefined
    };
  }

  private passwordsMatchValidator(group: AbstractControl): ValidationErrors | null {
    const password = String(group.get('password')?.value ?? '');
    const confirmPassword = String(group.get('confirmPassword')?.value ?? '');
    if (!confirmPassword) {
      return null;
    }
    return password === confirmPassword ? null : { passwordMismatch: true };
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
