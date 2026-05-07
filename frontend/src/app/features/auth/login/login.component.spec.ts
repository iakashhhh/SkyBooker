import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthApiService } from '../../../core/services/auth-api.service';
import { BookingJourneyService } from '../../../core/services/booking-journey.service';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let authApiSpy: jasmine.SpyObj<AuthApiService>;
  let bookingJourneySpy: jasmine.SpyObj<BookingJourneyService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    authApiSpy = jasmine.createSpyObj<AuthApiService>('AuthApiService', ['login', 'forgotPassword', 'resetPassword', 'googleLogin']);
    bookingJourneySpy = jasmine.createSpyObj<BookingJourneyService>('BookingJourneyService', [
      'hasPendingBookingDraft',
      'resumePendingBooking',
      'saveActiveBookingContext',
      'getActiveBookingContext'
    ]);
    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigateByUrl', 'navigate']);

    bookingJourneySpy.hasPendingBookingDraft.and.returnValue(false);
    bookingJourneySpy.resumePendingBooking.and.returnValue(of(null));

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            url: of([]),
            params: of({}),
            queryParams: of({}),
            fragment: of(null),
            data: of({}),
            snapshot: {
              queryParamMap: convertToParamMap({}),
              queryParams: {}
            }
          }
        },
        { provide: AuthApiService, useValue: authApiSpy },
        { provide: BookingJourneyService, useValue: bookingJourneySpy },
        { provide: Router, useValue: routerSpy }
      ]
    })
      .overrideComponent(LoginComponent, {
        set: { template: '' }
      })
      .compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('does not call login when form is invalid', () => {
    component.submit();

    expect(authApiSpy.login).not.toHaveBeenCalled();
    expect(component.form.controls.email.touched).toBeTrue();
  });

  it('submits valid login and navigates after success', () => {
    authApiSpy.login.and.returnValue(of({
      token: 'jwt',
      email: 'akash@test.com',
      role: 'PASSENGER',
      userId: 5
    }));

    component.form.setValue({ email: 'akash@test.com', password: 'Password@123' });
    component.submit();

    expect(authApiSpy.login).toHaveBeenCalledWith({ email: 'akash@test.com', password: 'Password@123' });
    expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/');
    expect(component.isLoading).toBeFalse();
  });

  it('routes admin users to operations area using returnUrl when provided', () => {
    const route = TestBed.inject(ActivatedRoute) as any;
    route.snapshot.queryParamMap = convertToParamMap({ returnUrl: '/operations/flights' });
    route.snapshot.queryParams = { returnUrl: '/operations/flights' };
    authApiSpy.login.and.returnValue(of({
      token: 'jwt',
      email: 'ops@test.com',
      role: 'ADMIN',
      userId: 1
    }));

    component.form.setValue({ email: 'ops@test.com', password: 'Password@123' });
    component.submit();

    expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/operations/flights');
  });

  it('resumes pending booking and sends passenger to details page', () => {
    authApiSpy.login.and.returnValue(of({
      token: 'jwt',
      email: 'akash@test.com',
      role: 'PASSENGER',
      userId: 5
    }));
    bookingJourneySpy.resumePendingBooking.and.returnValue(of({
      bookingId: 'BKG-300',
      pnrCode: 'PNR300',
      flightId: 90,
      seatIds: [11, 12],
      userId: 5,
      totalFare: 8800
    } as any));

    component.form.setValue({ email: 'akash@test.com', password: 'Password@123' });
    component.submit();

    expect(bookingJourneySpy.saveActiveBookingContext).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/passenger-details'], {
      queryParams: jasmine.objectContaining({
        bookingId: 'BKG-300',
        pnr: 'PNR300',
        resumed: true
      })
    });
  });

  it('shows error message when login fails', () => {
    authApiSpy.login.and.returnValue(throwError(() => new Error('invalid')));
    component.form.setValue({ email: 'akash@test.com', password: 'Password@123' });

    component.submit();

    expect(component.errorMessage).toContain('Unable to sign in');
    expect(component.isLoading).toBeFalse();
  });

  it('requests forgot password OTP and updates UI state', () => {
    authApiSpy.forgotPassword.and.returnValue(of({ message: 'OTP sent' }));
    component.forgotMode = true;
    component.forgotForm.controls.email.setValue('akash@test.com');

    component.requestPasswordOtp();

    expect(authApiSpy.forgotPassword).toHaveBeenCalledWith({ email: 'akash@test.com' });
    expect(component.forgotMessage).toBe('OTP sent');
    expect(component.errorMessage).toBe('');
  });

  it('does not request OTP when forgot email is invalid', () => {
    component.forgotMode = true;
    component.forgotForm.controls.email.setValue('bad-email');

    component.requestPasswordOtp();

    expect(authApiSpy.forgotPassword).not.toHaveBeenCalled();
    expect(component.forgotForm.controls.email.touched).toBeTrue();
  });

  it('shows error when reset password is requested with missing fields', () => {
    component.forgotMode = true;
    component.forgotForm.patchValue({
      email: 'akash@test.com',
      otpCode: '',
      newPassword: ''
    });

    component.resetPassword();

    expect(authApiSpy.resetPassword).not.toHaveBeenCalled();
    expect(component.errorMessage).toBe('Email, OTP, and new password are required.');
  });

  it('resets password successfully and shows confirmation message', () => {
    authApiSpy.resetPassword.and.returnValue(of({ message: 'Password updated' }));
    component.forgotMode = true;
    component.forgotForm.patchValue({
      email: 'akash@test.com',
      otpCode: '123456',
      newPassword: 'Password@123'
    });

    component.resetPassword();

    expect(authApiSpy.resetPassword).toHaveBeenCalledWith({
      email: 'akash@test.com',
      otpCode: '123456',
      newPassword: 'Password@123'
    });
    expect(component.forgotMessage).toBe('Password updated');
    expect(component.errorMessage).toBe('');
  });

  it('shows error when forgot password API fails', () => {
    authApiSpy.forgotPassword.and.returnValue(throwError(() => new Error('down')));
    component.forgotMode = true;
    component.forgotForm.controls.email.setValue('akash@test.com');

    component.requestPasswordOtp();

    expect(component.errorMessage).toContain('Unable to send reset OTP');
    expect(component.isLoading).toBeFalse();
  });

  it('toggles forgot mode and clears status messages', () => {
    component.forgotMessage = 'old';
    component.errorMessage = 'error';

    component.toggleForgotMode();

    expect(component.forgotMode).toBeTrue();
    expect(component.forgotMessage).toBe('');
    expect(component.errorMessage).toBe('');
  });

  it('updates role specific title and description', () => {
    component.selectRole('AIRLINE_STAFF');

    expect(component.pageTitle).toContain('Staff access');
    expect(component.pageDescription).toContain('airline and airport inventory');
  });

  it('navigates to setup message path when google is not configured', () => {
    component.showGoogleSetupMessage();

    expect(component.errorMessage).toContain('Google login is not configured yet');
  });
});
