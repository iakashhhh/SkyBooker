import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AuthApiService } from './auth-api.service';
import { BookingApiService } from './booking-api.service';
import { BookingJourneyService } from './booking-journey.service';
import { TokenStorageService } from './token-storage.service';

describe('BookingJourneyService', () => {
  let service: BookingJourneyService;
  let authApiSpy: jasmine.SpyObj<AuthApiService>;
  let bookingApiSpy: jasmine.SpyObj<BookingApiService>;
  let tokenStorageSpy: jasmine.SpyObj<TokenStorageService>;

  beforeEach(() => {
    authApiSpy = jasmine.createSpyObj<AuthApiService>('AuthApiService', ['getProfile']);
    bookingApiSpy = jasmine.createSpyObj<BookingApiService>('BookingApiService', ['createBooking']);
    tokenStorageSpy = jasmine.createSpyObj<TokenStorageService>('TokenStorageService', ['getUserId']);

    TestBed.configureTestingModule({
      providers: [
        BookingJourneyService,
        { provide: AuthApiService, useValue: authApiSpy },
        { provide: BookingApiService, useValue: bookingApiSpy },
        { provide: TokenStorageService, useValue: tokenStorageSpy }
      ]
    });

    service = TestBed.inject(BookingJourneyService);
    sessionStorage.clear();
  });

  it('returns null when no pending draft exists', () => {
    service.resumePendingBooking().subscribe((result) => {
      expect(result).toBeNull();
    });

    expect(authApiSpy.getProfile).not.toHaveBeenCalled();
    expect(bookingApiSpy.createBooking).not.toHaveBeenCalled();
  });

  it('creates booking with profile user and clears draft after success', () => {
    service.savePendingBookingDraft({
      flightId: 88,
      tripType: 'ONE_WAY',
      baseFare: 4300,
      seatIds: [1, 2],
      luggageKg: 15,
      mealPreference: 'VEG',
      contactEmail: 'draft@test.com',
      contactPhone: '7000000000'
    });

    authApiSpy.getProfile.and.returnValue(of({
      userId: 42,
      fullName: 'Akash',
      email: 'profile@test.com',
      phone: '8000000000',
      passportNumber: 'AA123456',
      nationality: 'IN',
      role: 'PASSENGER',
      provider: 'LOCAL',
      active: true
    }));
    bookingApiSpy.createBooking.and.returnValue(of({ bookingId: 'BKG-42' } as any));

    service.resumePendingBooking({ contactEmail: 'fallback@test.com' }).subscribe((response) => {
      expect(response?.bookingId).toBe('BKG-42');
    });

    expect(bookingApiSpy.createBooking).toHaveBeenCalledWith(jasmine.objectContaining({
      userId: 42,
      flightId: 88,
      contactEmail: 'fallback@test.com',
      contactPhone: '7000000000'
    }));
    expect(service.hasPendingBookingDraft()).toBeFalse();
  });

  it('falls back to token storage user id when profile call fails', () => {
    service.savePendingBookingDraft({
      flightId: 91,
      tripType: 'ROUND_TRIP',
      baseFare: 5000,
      seatIds: [9],
      luggageKg: 10
    });

    authApiSpy.getProfile.and.returnValue(throwError(() => new Error('profile failed')));
    tokenStorageSpy.getUserId.and.returnValue(55);
    bookingApiSpy.createBooking.and.returnValue(of({ bookingId: 'BKG-55' } as any));

    service.resumePendingBooking().subscribe((response) => {
      expect(response?.bookingId).toBe('BKG-55');
    });

    expect(bookingApiSpy.createBooking).toHaveBeenCalledWith(jasmine.objectContaining({
      userId: 55,
      contactEmail: '',
      contactPhone: ''
    }));
  });

  it('returns null when neither profile nor token has user id', () => {
    service.savePendingBookingDraft({
      flightId: 99,
      tripType: 'ONE_WAY',
      baseFare: 2000,
      seatIds: [5],
      luggageKg: 0
    });

    authApiSpy.getProfile.and.returnValue(of(null as any));
    tokenStorageSpy.getUserId.and.returnValue(null);

    service.resumePendingBooking().subscribe((response) => {
      expect(response).toBeNull();
    });

    expect(bookingApiSpy.createBooking).not.toHaveBeenCalled();
  });

  it('returns null and clears malformed active booking context JSON', () => {
    sessionStorage.setItem('skybooker_active_booking_context', '{invalid-json');

    expect(service.getActiveBookingContext()).toBeNull();
    expect(sessionStorage.getItem('skybooker_active_booking_context')).toBeNull();
  });
});
