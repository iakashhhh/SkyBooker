import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AirlineAirportApiService } from '../../../core/services/airline-airport-api.service';
import { AuthApiService } from '../../../core/services/auth-api.service';
import { BookingJourneyService } from '../../../core/services/booking-journey.service';
import { RegisterComponent } from './register.component';

describe('RegisterComponent', () => {
  let fixture: ComponentFixture<RegisterComponent>;
  let component: RegisterComponent;
  let authApiSpy: jasmine.SpyObj<AuthApiService>;
  let bookingJourneySpy: jasmine.SpyObj<BookingJourneyService>;
  let airlineApiSpy: jasmine.SpyObj<AirlineAirportApiService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    authApiSpy = jasmine.createSpyObj<AuthApiService>('AuthApiService', ['register', 'googleLogin']);
    bookingJourneySpy = jasmine.createSpyObj<BookingJourneyService>('BookingJourneyService', [
      'hasPendingBookingDraft',
      'resumePendingBooking',
      'saveActiveBookingContext',
      'getActiveBookingContext'
    ]);
    airlineApiSpy = jasmine.createSpyObj<AirlineAirportApiService>('AirlineAirportApiService', ['getAirlines']);
    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigateByUrl', 'navigate']);

    bookingJourneySpy.hasPendingBookingDraft.and.returnValue(false);
    bookingJourneySpy.resumePendingBooking.and.returnValue(of(null));
    airlineApiSpy.getAirlines.and.returnValue(of([
      { airlineId: 2, name: 'Zulu Air', iataCode: 'ZA', country: 'IN', active: true },
      { airlineId: 1, name: 'Alpha Air', iataCode: 'AA', country: 'IN', active: true },
      { airlineId: 3, name: 'Inactive', iataCode: 'IN', country: 'IN', active: false }
    ] as any));

    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
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
        { provide: AirlineAirportApiService, useValue: airlineApiSpy },
        { provide: Router, useValue: routerSpy }
      ]
    })
      .overrideComponent(RegisterComponent, {
        set: { template: '' }
      })
      .compileComponents();

    fixture = TestBed.createComponent(RegisterComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  function fillValidPassengerForm(target: RegisterComponent): void {
    target.form.setValue({
      fullName: 'Akash Sharma',
      email: 'akash@test.com',
      password: 'Password@123',
      confirmPassword: 'Password@123',
      phone: '+919876543210',
      passportNumber: 'P1234567',
      nationality: 'Indian',
      dateOfBirth: '1990-01-01',
      role: 'PASSENGER',
      airlineId: ''
    });
  }

  it('loads only active airlines sorted by name', () => {
    expect(component.airlineOptions.map((item) => item.name)).toEqual(['Alpha Air', 'Zulu Air']);
  });

  it('blocks submission when airline staff has no airline selected', () => {
    component.selectRole('AIRLINE_STAFF');
    fillValidPassengerForm(component);
    component.form.controls.role.setValue('AIRLINE_STAFF');
    component.form.controls.airlineId.setValue('');

    component.submit();

    expect(authApiSpy.register).not.toHaveBeenCalled();
    expect(component.errorMessage).toContain('Please select an airline');
  });

  it('submits valid form and navigates after successful registration', () => {
    authApiSpy.register.and.returnValue(of({
      token: 'jwt',
      email: 'akash@test.com',
      role: 'PASSENGER',
      userId: 7
    }));

    fillValidPassengerForm(component);
    component.submit();

    expect(authApiSpy.register).toHaveBeenCalled();
    expect(bookingJourneySpy.resumePendingBooking).toHaveBeenCalled();
    expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/');
  });

  it('routes airline staff to airline dashboard when no return url exists', () => {
    authApiSpy.register.and.returnValue(of({
      token: 'jwt',
      email: 'staff@test.com',
      role: 'AIRLINE_STAFF',
      userId: 8
    }));

    fillValidPassengerForm(component);
    component.selectRole('AIRLINE_STAFF');
    component.form.controls.role.setValue('AIRLINE_STAFF');
    component.form.controls.airlineId.setValue('1');

    component.submit();

    expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/airline/dashboard');
  });

  it('shows error when register API fails', () => {
    authApiSpy.register.and.returnValue(throwError(() => new Error('failed')));

    fillValidPassengerForm(component);
    component.submit();

    expect(component.errorMessage).toContain('Unable to create account');
    expect(component.isLoading).toBeFalse();
  });

  it('marks controls touched and blocks submit when form is invalid', () => {
    component.form.patchValue({
      email: 'not-an-email'
    });

    component.submit();

    expect(authApiSpy.register).not.toHaveBeenCalled();
    expect(component.form.controls.email.touched).toBeTrue();
  });

  it('clears airline selection when role changes away from airline staff', () => {
    component.selectRole('AIRLINE_STAFF');
    component.form.controls.airlineId.setValue('12');

    component.selectRole('PASSENGER');

    expect(component.form.controls.role.value).toBe('PASSENGER');
    expect(component.form.controls.airlineId.value).toBe('');
  });

  it('resumes booking context and navigates to passenger details when pending booking exists', () => {
    authApiSpy.register.and.returnValue(of({
      token: 'jwt',
      email: 'akash@test.com',
      role: 'PASSENGER',
      userId: 7
    }));
    bookingJourneySpy.resumePendingBooking.and.returnValue(of({
      bookingId: 'BKG-900',
      pnrCode: 'PNR900',
      flightId: 77,
      seatIds: [11],
      userId: 7,
      totalFare: 5500
    } as any));

    fillValidPassengerForm(component);
    component.submit();

    expect(bookingJourneySpy.saveActiveBookingContext).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/passenger-details'], {
      queryParams: jasmine.objectContaining({
        bookingId: 'BKG-900',
        pnr: 'PNR900',
        resumed: true
      })
    });
  });

  it('falls back to flights results when booking context returnUrl is passenger-details but no active booking exists', () => {
    const route = TestBed.inject(ActivatedRoute) as any;
    route.snapshot.queryParamMap = convertToParamMap({
      context: 'booking',
      returnUrl: '/passenger-details'
    });
    route.snapshot.queryParams = { context: 'booking', returnUrl: '/passenger-details' };

    authApiSpy.register.and.returnValue(of({
      token: 'jwt',
      email: 'akash@test.com',
      role: 'PASSENGER',
      userId: 7
    }));
    bookingJourneySpy.resumePendingBooking.and.returnValue(of(null));
    bookingJourneySpy.getActiveBookingContext.and.returnValue(null as any);

    fillValidPassengerForm(component);
    component.submit();

    expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/flights/results');
  });

  it('shows mismatch message when confirm password does not match', () => {
    component.form.controls.password.setValue('Password@123');
    component.form.controls.confirmPassword.setValue('Password@456');
    component.form.controls.confirmPassword.markAsTouched();

    expect(component.form.hasError('passwordMismatch')).toBeTrue();
    expect(component.confirmPasswordInvalid).toBeTrue();
    expect(component.confirmPasswordMessage).toBe('Passwords do not match.');
  });

  it('shows google setup message when feature is unavailable', () => {
    component.showGoogleSetupMessage();

    expect(component.errorMessage).toContain('Google signup is not configured yet');
  });

  it('ignores admin role selection to protect registration role list', () => {
    component.selectRole('PASSENGER');
    component.form.controls.airlineId.setValue('33');

    component.selectRole('ADMIN');

    expect(component.selectedRole).toBe('PASSENGER');
    expect(component.form.controls.role.value).toBe('PASSENGER');
    expect(component.form.controls.airlineId.value).toBe('33');
  });
});
