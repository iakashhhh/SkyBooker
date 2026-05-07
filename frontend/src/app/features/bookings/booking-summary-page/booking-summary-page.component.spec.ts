import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { BookingResponse } from '../../../core/models/booking.models';
import { BookingApiService } from '../../../core/services/booking-api.service';
import { BookingJourneyService } from '../../../core/services/booking-journey.service';
import { PassengerApiService } from '../../../core/services/passenger-api.service';
import { PaymentApiService } from '../../../core/services/payment-api.service';
import { SeatApiService } from '../../../core/services/seat-api.service';
import { BookingSummaryPageComponent } from './booking-summary-page.component';

describe('BookingSummaryPageComponent', () => {
  let fixture: ComponentFixture<BookingSummaryPageComponent>;
  let component: BookingSummaryPageComponent;
  let bookingApiServiceSpy: jasmine.SpyObj<BookingApiService>;
  let bookingJourneyServiceSpy: jasmine.SpyObj<BookingJourneyService>;
  let passengerApiServiceSpy: jasmine.SpyObj<PassengerApiService>;
  let paymentApiServiceSpy: jasmine.SpyObj<PaymentApiService>;
  let seatApiServiceSpy: jasmine.SpyObj<SeatApiService>;
  let routerSpy: jasmine.SpyObj<Router>;

  const booking: BookingResponse = {
    bookingId: 'BKG-101',
    userId: 12,
    flightId: 31,
    seatIds: [101, 102],
    pnrCode: 'PNR101',
    tripType: 'ONE_WAY',
    status: 'PENDING',
    totalFare: 5500,
    baseFare: 4500,
    seatCharge: 400,
    baggageCharge: 100,
    mealCharge: 200,
    taxes: 300,
    luggageKg: 15,
    contactEmail: 'akash@test.com',
    contactPhone: '9999999999',
    bookedAt: '2026-01-20T10:00:00Z'
  };

  beforeEach(async () => {
    bookingApiServiceSpy = jasmine.createSpyObj<BookingApiService>('BookingApiService', [
      'getBookingById',
      'getBookingByPnr'
    ]);
    bookingJourneyServiceSpy = jasmine.createSpyObj<BookingJourneyService>('BookingJourneyService', [
      'getActiveBookingContext',
      'saveActiveBookingContext'
    ]);
    passengerApiServiceSpy = jasmine.createSpyObj<PassengerApiService>('PassengerApiService', ['getPassengersByBooking']);
    paymentApiServiceSpy = jasmine.createSpyObj<PaymentApiService>('PaymentApiService', ['getPaymentByBooking']);
    seatApiServiceSpy = jasmine.createSpyObj<SeatApiService>('SeatApiService', ['getSeatMap']);
    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);

    bookingJourneyServiceSpy.getActiveBookingContext.and.returnValue(null);
    passengerApiServiceSpy.getPassengersByBooking.and.returnValue(of([]));
    paymentApiServiceSpy.getPaymentByBooking.and.returnValue(of({
      paymentId: 'PAY-11',
      bookingId: booking.bookingId,
      userId: booking.userId,
      amount: booking.totalFare,
      currency: 'INR',
      status: 'PENDING',
      paymentMode: 'UPI'
    }));
    seatApiServiceSpy.getSeatMap.and.returnValue(of({
      flightId: 31,
      seats: [
        {
          seatId: 101,
          flightId: 31,
          seatNumber: '12A',
          seatClass: 'ECONOMY',
          row: 12,
          column: 'A',
          isWindow: true,
          isAisle: false,
          hasExtraLegroom: false,
          status: 'CONFIRMED',
          priceMultiplier: 1
        }
      ],
      availableSeatsByClass: {
        ECONOMY: 0,
        BUSINESS: 0,
        FIRST: 0
      }
    }));

    await TestBed.configureTestingModule({
      imports: [BookingSummaryPageComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap({})
            }
          }
        },
        { provide: BookingApiService, useValue: bookingApiServiceSpy },
        { provide: BookingJourneyService, useValue: bookingJourneyServiceSpy },
        { provide: PassengerApiService, useValue: passengerApiServiceSpy },
        { provide: PaymentApiService, useValue: paymentApiServiceSpy },
        { provide: SeatApiService, useValue: seatApiServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    }).compileComponents();
  });

  it('shows error when no booking reference is available', () => {
    fixture = TestBed.createComponent(BookingSummaryPageComponent);
    component = fixture.componentInstance;

    fixture.detectChanges();

    expect(component.errorMessage).toContain('No booking reference found');
    expect(bookingApiServiceSpy.getBookingById).not.toHaveBeenCalled();
    expect(bookingApiServiceSpy.getBookingByPnr).not.toHaveBeenCalled();
  });

  it('loads booking details and maps seat labels when booking id is present', () => {
    const activatedRoute = TestBed.inject(ActivatedRoute) as ActivatedRoute & {
      snapshot: { queryParamMap: ReturnType<typeof convertToParamMap> };
    };
    activatedRoute.snapshot.queryParamMap = convertToParamMap({ bookingId: booking.bookingId });

    bookingApiServiceSpy.getBookingById.and.returnValue(of(booking));
    passengerApiServiceSpy.getPassengersByBooking.and.returnValue(of([
      {
        passengerId: 1,
        bookingId: booking.bookingId,
        title: 'Mr',
        firstName: 'Akash',
        lastName: 'Sharma',
        dateOfBirth: '1990-01-01',
        gender: 'Male',
        passengerType: 'ADULT',
        passportNumber: 'P1234567',
        nationality: 'Indian',
        passportExpiry: '2030-01-01',
        ticketNumber: 'TKT-1'
      }
    ]));

    fixture = TestBed.createComponent(BookingSummaryPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(bookingApiServiceSpy.getBookingById).toHaveBeenCalledWith('BKG-101');
    expect(component.booking?.pnrCode).toBe('PNR101');
    expect(component.seatLabels).toEqual(['12A', 'Seat 102']);
    expect(bookingJourneyServiceSpy.saveActiveBookingContext).toHaveBeenCalled();
  });

  it('routes to passenger step when payment requested before passenger completion', () => {
    fixture = TestBed.createComponent(BookingSummaryPageComponent);
    component = fixture.componentInstance;
    component.booking = booking;
    component.passengers = [];

    component.continueToPayment();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/passenger-details'], {
      queryParams: {
        bookingId: booking.bookingId,
        pnr: booking.pnrCode,
        userId: booking.userId,
        amount: booking.totalFare
      }
    });
  });

  it('routes to payment step when passenger details are complete', () => {
    fixture = TestBed.createComponent(BookingSummaryPageComponent);
    component = fixture.componentInstance;
    component.booking = booking;
    component.passengers = [
      {
        passengerId: 1,
        bookingId: booking.bookingId,
        title: 'Mr',
        firstName: 'Akash',
        lastName: 'Sharma',
        dateOfBirth: '1990-01-01',
        gender: 'Male',
        passengerType: 'ADULT',
        passportNumber: 'P1234567',
        nationality: 'Indian',
        passportExpiry: '2030-01-01',
        ticketNumber: 'TKT-1'
      },
      {
        passengerId: 2,
        bookingId: booking.bookingId,
        title: 'Ms',
        firstName: 'Riya',
        lastName: 'Sharma',
        dateOfBirth: '1994-01-01',
        gender: 'Female',
        passengerType: 'ADULT',
        passportNumber: 'P1234568',
        nationality: 'Indian',
        passportExpiry: '2030-01-01',
        ticketNumber: 'TKT-2'
      }
    ];

    component.continueToPayment();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/payment'], {
      queryParams: {
        bookingId: booking.bookingId,
        pnr: booking.pnrCode,
        userId: booking.userId,
        amount: booking.totalFare
      }
    });
  });

  it('shows summary load error when booking API fails', () => {
    const activatedRoute = TestBed.inject(ActivatedRoute) as ActivatedRoute & {
      snapshot: { queryParamMap: ReturnType<typeof convertToParamMap> };
    };
    activatedRoute.snapshot.queryParamMap = convertToParamMap({ bookingId: booking.bookingId });
    bookingApiServiceSpy.getBookingById.and.returnValue(throwError(() => new Error('failed')));

    fixture = TestBed.createComponent(BookingSummaryPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.errorMessage).toBe('Unable to load booking summary for this booking.');
  });

  it('loads booking using pnr when booking id is not provided', () => {
    const activatedRoute = TestBed.inject(ActivatedRoute) as ActivatedRoute & {
      snapshot: { queryParamMap: ReturnType<typeof convertToParamMap> };
    };
    activatedRoute.snapshot.queryParamMap = convertToParamMap({ pnr: booking.pnrCode });

    bookingApiServiceSpy.getBookingByPnr.and.returnValue(of(booking));

    fixture = TestBed.createComponent(BookingSummaryPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(bookingApiServiceSpy.getBookingByPnr).toHaveBeenCalledWith('PNR101');
    expect(component.booking?.bookingId).toBe('BKG-101');
  });

  it('continueJourney routes to payment when passengers complete and payment pending', () => {
    fixture = TestBed.createComponent(BookingSummaryPageComponent);
    component = fixture.componentInstance;
    component.booking = booking;
    component.passengers = [
      {
        passengerId: 1,
        bookingId: booking.bookingId,
        title: 'Mr',
        firstName: 'Akash',
        lastName: 'Sharma',
        dateOfBirth: '1990-01-01',
        gender: 'Male',
        passengerType: 'ADULT',
        passportNumber: 'P1234567',
        nationality: 'Indian',
        passportExpiry: '2030-01-01',
        ticketNumber: 'TKT-1'
      },
      {
        passengerId: 2,
        bookingId: booking.bookingId,
        title: 'Ms',
        firstName: 'Riya',
        lastName: 'Sharma',
        dateOfBirth: '1994-01-01',
        gender: 'Female',
        passengerType: 'ADULT',
        passportNumber: 'P1234568',
        nationality: 'Indian',
        passportExpiry: '2030-01-01',
        ticketNumber: 'TKT-2'
      }
    ];
    component.payment = {
      paymentId: 'PAY-1',
      bookingId: booking.bookingId,
      userId: booking.userId,
      amount: booking.totalFare,
      currency: 'INR',
      status: 'PENDING',
      paymentMode: 'UPI'
    };

    component.continueJourney();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/payment'], {
      queryParams: jasmine.objectContaining({
        bookingId: booking.bookingId,
        pnr: booking.pnrCode
      })
    });
  });

  it('continueJourney does nothing when payment is already completed', () => {
    fixture = TestBed.createComponent(BookingSummaryPageComponent);
    component = fixture.componentInstance;
    component.booking = { ...booking, status: 'CONFIRMED' };
    component.passengers = [
      {
        passengerId: 1,
        bookingId: booking.bookingId,
        title: 'Mr',
        firstName: 'Akash',
        lastName: 'Sharma',
        dateOfBirth: '1990-01-01',
        gender: 'Male',
        passengerType: 'ADULT',
        passportNumber: 'P1234567',
        nationality: 'Indian',
        passportExpiry: '2030-01-01',
        ticketNumber: 'TKT-1'
      },
      {
        passengerId: 2,
        bookingId: booking.bookingId,
        title: 'Ms',
        firstName: 'Riya',
        lastName: 'Sharma',
        dateOfBirth: '1994-01-01',
        gender: 'Female',
        passengerType: 'ADULT',
        passportNumber: 'P1234568',
        nationality: 'Indian',
        passportExpiry: '2030-01-01',
        ticketNumber: 'TKT-2'
      }
    ];
    routerSpy.navigate.calls.reset();

    component.continueJourney();

    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });
});
