import { ChangeDetectorRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { AuthApiService } from '../../../core/services/auth-api.service';
import { BookingApiService } from '../../../core/services/booking-api.service';
import { BookingJourneyService } from '../../../core/services/booking-journey.service';
import { FlightApiService } from '../../../core/services/flight-api.service';
import { SeatApiService } from '../../../core/services/seat-api.service';
import { TimerService } from '../../../core/services/timer.service';
import { TokenStorageService } from '../../../core/services/token-storage.service';
import { SeatSelectionComponent } from './seat-selection.component';

describe('SeatSelectionComponent', () => {
  let fixture: ComponentFixture<SeatSelectionComponent>;
  let component: SeatSelectionComponent;
  let bookingApiSpy: jasmine.SpyObj<BookingApiService>;
  let authApiSpy: jasmine.SpyObj<AuthApiService>;
  let seatApiSpy: jasmine.SpyObj<SeatApiService>;
  let bookingJourneySpy: jasmine.SpyObj<BookingJourneyService>;
  let flightApiSpy: jasmine.SpyObj<FlightApiService>;
  let tokenStorageSpy: jasmine.SpyObj<TokenStorageService>;
  let routerSpy: jasmine.SpyObj<Router>;

  const countdown$ = new BehaviorSubject<number>(0);
  const timerSpy = jasmine.createSpyObj<TimerService>('TimerService', ['start', 'reset'], {
    countdownSeconds$: countdown$.asObservable()
  });

  const routeMock: any = {
    snapshot: {
      queryParams: {
        flightId: '77',
        flightNumber: 'SB-77',
        origin: 'DEL',
        destination: 'BLR',
        baseFare: '5000',
        totalSeats: '180',
        returnUrl: '/flights/results?tripType=ONE_WAY',
        seatIds: '1,2'
      }
    }
  };

  beforeEach(async () => {
    routeMock.snapshot.queryParams = {
      flightId: '77',
      flightNumber: 'SB-77',
      origin: 'DEL',
      destination: 'BLR',
      baseFare: '5000',
      totalSeats: '180',
      returnUrl: '/flights/results?tripType=ONE_WAY',
      seatIds: '1,2'
    };

    bookingApiSpy = jasmine.createSpyObj<BookingApiService>('BookingApiService', ['createBooking']);
    authApiSpy = jasmine.createSpyObj<AuthApiService>('AuthApiService', ['getProfile']);
    seatApiSpy = jasmine.createSpyObj<SeatApiService>('SeatApiService', ['getSeatMap', 'holdSeats', 'releaseSeats']);
    bookingJourneySpy = jasmine.createSpyObj<BookingJourneyService>('BookingJourneyService', [
      'savePendingBookingDraft',
      'saveActiveBookingContext'
    ]);
    flightApiSpy = jasmine.createSpyObj<FlightApiService>('FlightApiService', ['getFlightById']);
    tokenStorageSpy = jasmine.createSpyObj<TokenStorageService>('TokenStorageService', ['getUserId', 'getToken']);
    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate', 'navigateByUrl', 'parseUrl']);

    authApiSpy.getProfile.and.returnValue(of({ userId: 9, email: 'user@test.com', phone: '9999999999' } as any));
    seatApiSpy.getSeatMap.and.returnValue(of({
      flightId: 77,
      seats: [
        { seatId: 1, seatNumber: '1A', status: 'HELD', row: 1, column: 'A', seatClass: 'FIRST', priceMultiplier: 2.5 }
      ],
      availableSeatsByClass: { FIRST: 1, BUSINESS: 0, ECONOMY: 0 }
    } as any));
    seatApiSpy.releaseSeats.and.returnValue(of({} as any));
    flightApiSpy.getFlightById.and.returnValue(of({ displayedPrice: 5000 } as any));
    tokenStorageSpy.getUserId.and.returnValue(5);
    tokenStorageSpy.getToken.and.returnValue(null);
    bookingApiSpy.createBooking.and.returnValue(of({
      bookingId: 'BKG-100',
      pnrCode: 'PNR100',
      flightId: 77,
      seatIds: [1],
      userId: 5,
      totalFare: 5000
    } as any));
    routerSpy.parseUrl.and.returnValue({ queryParams: {} } as any);

    await TestBed.configureTestingModule({
      imports: [SeatSelectionComponent],
      providers: [
        { provide: ActivatedRoute, useValue: routeMock },
        { provide: Router, useValue: routerSpy },
        { provide: BookingApiService, useValue: bookingApiSpy },
        { provide: AuthApiService, useValue: authApiSpy },
        { provide: SeatApiService, useValue: seatApiSpy },
        { provide: BookingJourneyService, useValue: bookingJourneySpy },
        { provide: FlightApiService, useValue: flightApiSpy },
        { provide: TimerService, useValue: timerSpy },
        { provide: TokenStorageService, useValue: tokenStorageSpy },
        { provide: ChangeDetectorRef, useValue: { markForCheck: () => undefined } }
      ]
    })
      .overrideComponent(SeatSelectionComponent, { set: { template: '' } })
      .compileComponents();

    fixture = TestBed.createComponent(SeatSelectionComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    component.ngOnDestroy();
  });

  it('shows error when flight id is missing in route params', () => {
    routeMock.snapshot.queryParams = { ...routeMock.snapshot.queryParams, flightId: '' };

    component.ngOnInit();

    expect(component.errorMessage).toContain('Flight ID missing');
    expect(seatApiSpy.getSeatMap).not.toHaveBeenCalled();
  });

  it('loads profile and seat map on init', () => {
    component.ngOnInit();

    expect(authApiSpy.getProfile).toHaveBeenCalled();
    expect(seatApiSpy.getSeatMap).toHaveBeenCalledWith(77, 180);
    expect(component.contactEmail).toBe('user@test.com');
  });

  it('holds seat successfully and starts timer when hold expiry is provided', () => {
    component.flightId = 77;
    component.seatMap = {
      flightId: 77,
      seats: [{ seatId: 11, seatNumber: '11A', status: 'AVAILABLE', row: 11, column: 'A', seatClass: 'ECONOMY', priceMultiplier: 1 }],
      availableSeatsByClass: { FIRST: 0, BUSINESS: 0, ECONOMY: 1 }
    } as any;
    seatApiSpy.holdSeats.and.returnValue(of({ holdExpiresAt: new Date(Date.now() + 60_000).toISOString() } as any));

    component.onSeatToggle(component.seatMap!.seats[0] as any);

    expect(seatApiSpy.holdSeats).toHaveBeenCalledWith({ flightId: 77, seatIds: [11] });
    expect(component.selectedSeatIds).toContain(11);
    expect(timerSpy.start).toHaveBeenCalled();
    expect(component.toastMessage).toContain('held for 15 minutes');
  });

  it('reverts seat selection when hold API fails', () => {
    component.flightId = 77;
    component.seatMap = {
      flightId: 77,
      seats: [{ seatId: 12, seatNumber: '12A', status: 'AVAILABLE', row: 12, column: 'A', seatClass: 'ECONOMY', priceMultiplier: 1 }],
      availableSeatsByClass: { FIRST: 0, BUSINESS: 0, ECONOMY: 1 }
    } as any;
    seatApiSpy.holdSeats.and.returnValue(throwError(() => new Error('taken')));

    component.onSeatToggle(component.seatMap!.seats[0] as any);

    expect(component.selectedSeatIds).not.toContain(12);
    expect(component.toastMessage).toContain('just taken');
  });

  it('stores pending booking and redirects to login when token is missing', () => {
    tokenStorageSpy.getToken.and.returnValue(null);
    component.flightId = 77;
    component.baseFare = 5000;
    component.selectedSeatIds = [1, 2];
    component.contactEmail = 'user@test.com';
    component.contactPhone = '9000000000';

    component.confirmSelection();

    expect(bookingJourneySpy.savePendingBookingDraft).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/auth/login'], {
      queryParams: jasmine.objectContaining({ context: 'booking' })
    });
  });

  it('creates booking and navigates to passenger details for logged-in user', () => {
    tokenStorageSpy.getToken.and.returnValue('jwt-token');
    component.flightId = 77;
    component.userId = 5;
    component.baseFare = 5000;
    component.selectedSeatIds = [1];
    component.contactEmail = 'user@test.com';
    component.contactPhone = '9000000000';

    component.confirmSelection();

    expect(bookingApiSpy.createBooking).toHaveBeenCalled();
    expect(bookingJourneySpy.saveActiveBookingContext).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/passenger-details'], {
      queryParams: jasmine.objectContaining({ bookingId: 'BKG-100', pnr: 'PNR100', fromSeatSelection: true })
    });
  });

  it('shows booking creation error when create booking fails', () => {
    tokenStorageSpy.getToken.and.returnValue('jwt-token');
    bookingApiSpy.createBooking.and.returnValue(throwError(() => new Error('failed')));
    component.flightId = 77;
    component.baseFare = 5000;
    component.selectedSeatIds = [1];

    component.confirmSelection();

    expect(component.toastMessage).toContain('Booking creation failed');
  });

  it('releases all seats and refreshes map', () => {
    component.flightId = 77;
    component.selectedSeatIds = [1, 2];
    seatApiSpy.releaseSeats.and.returnValue(of({} as any));

    component.releaseAllSeats(true);

    expect(seatApiSpy.releaseSeats).toHaveBeenCalledWith({ flightId: 77, seatIds: [1, 2] });
    expect(component.selectedSeatIds).toEqual([]);
    expect(timerSpy.reset).toHaveBeenCalled();
    expect(component.toastMessage).toContain('Hold expired');
  });

  it('navigates back with refresh marker for results URL', () => {
    component.returnUrl = '/flights/results?tripType=ONE_WAY';
    const urlTree = { queryParams: { tripType: 'ONE_WAY' } } as any;
    routerSpy.parseUrl.and.returnValue(urlTree);

    component.goBack();

    expect(routerSpy.navigateByUrl).toHaveBeenCalledWith(jasmine.objectContaining({
      queryParams: jasmine.objectContaining({ tripType: 'ONE_WAY' })
    }));
    expect(urlTree.queryParams.refreshAt).toBeDefined();
  });

  it('shows fare error when selected seats exist but base fare is invalid', () => {
    tokenStorageSpy.getToken.and.returnValue('jwt-token');
    component.selectedSeatIds = [1];
    component.baseFare = 0;

    component.confirmSelection();

    expect(component.toastMessage).toContain('fare details are unavailable');
    expect(bookingApiSpy.createBooking).not.toHaveBeenCalled();
  });

  it('releases already selected seat through release API path', () => {
    component.flightId = 77;
    component.selectedSeatIds = [14];
    component.seatMap = {
      flightId: 77,
      seats: [{ seatId: 14, seatNumber: '14A', status: 'HELD', row: 14, column: 'A', seatClass: 'ECONOMY', priceMultiplier: 1 }],
      availableSeatsByClass: { FIRST: 0, BUSINESS: 0, ECONOMY: 1 }
    } as any;
    seatApiSpy.releaseSeats.and.returnValue(of({} as any));

    component.onSeatToggle(component.seatMap!.seats[0] as any);

    expect(seatApiSpy.releaseSeats).toHaveBeenCalledWith({ flightId: 77, seatIds: [14] });
    expect(component.selectedSeatIds).toEqual([]);
    expect(component.toastMessage).toContain('released');
  });

  it('keeps selected seat and shows error when release API fails', () => {
    component.flightId = 77;
    component.selectedSeatIds = [15];
    component.seatMap = {
      flightId: 77,
      seats: [{ seatId: 15, seatNumber: '15A', status: 'HELD', row: 15, column: 'A', seatClass: 'ECONOMY', priceMultiplier: 1 }],
      availableSeatsByClass: { FIRST: 0, BUSINESS: 0, ECONOMY: 1 }
    } as any;
    seatApiSpy.releaseSeats.and.returnValue(throwError(() => new Error('cannot release')));

    component.onSeatToggle(component.seatMap!.seats[0] as any);

    expect(component.selectedSeatIds).toEqual([15]);
    expect(component.toastMessage).toContain('Unable to release seat');
  });

  it('falls back to generated seat map when backend seat map request fails', () => {
    seatApiSpy.getSeatMap.and.returnValue(throwError(() => new Error('backend down')));
    component.flightId = 77;
    component['totalSeats'] = 90;

    (component as any).loadSeatMap();

    expect(component.errorMessage).toContain('Unable to load seat map');
    expect(component.seatMap?.seats.length).toBeGreaterThan(0);
    expect(component.isLoading).toBeFalse();
  });

  it('navigates back to absolute route and to default route for invalid return path', () => {
    component.returnUrl = '/bookings';
    component.goBack();
    expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/bookings');

    routerSpy.navigateByUrl.calls.reset();
    component.returnUrl = 'bad-url';
    component.goBack();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/flights/results']);
  });

  it('recovers base fare from backend and shows fallback toast on failure', () => {
    component.flightId = 77;
    component.baseFare = 0;
    flightApiSpy.getFlightById.and.returnValue(of({ displayedPrice: 6400 } as any));

    (component as any).resolveBaseFareFromBackend();
    expect(component.baseFare).toBe(6400);

    component.baseFare = 0;
    flightApiSpy.getFlightById.and.returnValue(throwError(() => new Error('flight down')));
    (component as any).resolveBaseFareFromBackend();
    expect(component.toastMessage).toContain('Unable to fetch latest fare details');
  });
});
