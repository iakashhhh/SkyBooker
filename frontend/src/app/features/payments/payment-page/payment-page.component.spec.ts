import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, of, throwError } from 'rxjs';

import { BookingResponse } from '../../../core/models/booking.models';
import { BookingApiService } from '../../../core/services/booking-api.service';
import { BookingJourneyService } from '../../../core/services/booking-journey.service';
import { PassengerApiService } from '../../../core/services/passenger-api.service';
import { PaymentApiService } from '../../../core/services/payment-api.service';
import { PaymentPageComponent } from './payment-page.component';

describe('PaymentPageComponent', () => {
  let fixture: ComponentFixture<PaymentPageComponent>;
  let component: PaymentPageComponent;
  let paymentApiServiceSpy: jasmine.SpyObj<PaymentApiService>;
  let routerSpy: jasmine.SpyObj<Router>;

  const booking: BookingResponse = {
    bookingId: 'BKG-501',
    userId: 9,
    flightId: 77,
    seatIds: [11],
    pnrCode: 'PNR501',
    tripType: 'ONE_WAY',
    status: 'PENDING',
    totalFare: 6400,
    baseFare: 5200,
    seatCharge: 300,
    baggageCharge: 200,
    mealCharge: 300,
    taxes: 400,
    luggageKg: 15,
    contactEmail: 'akash@test.com',
    contactPhone: '9999999999',
    bookedAt: '2026-01-20T10:00:00Z'
  };

  beforeEach(async () => {
    const bookingApiServiceSpy = jasmine.createSpyObj<BookingApiService>('BookingApiService', ['getBookingById']);
    const bookingJourneyServiceSpy = jasmine.createSpyObj<BookingJourneyService>('BookingJourneyService', [
      'getActiveBookingContext',
      'saveActiveBookingContext'
    ]);
    const passengerApiServiceSpy = jasmine.createSpyObj<PassengerApiService>('PassengerApiService', ['getPassengersByBooking']);
    paymentApiServiceSpy = jasmine.createSpyObj<PaymentApiService>('PaymentApiService', [
      'getPaymentByBooking',
      'initiatePayment',
      'verifyPayment',
      'processPayment',
      'getPaymentKey'
    ]);
    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);

    bookingJourneyServiceSpy.getActiveBookingContext.and.returnValue({ bookingId: booking.bookingId });
    bookingApiServiceSpy.getBookingById.and.returnValue(of(booking));
    passengerApiServiceSpy.getPassengersByBooking.and.returnValue(of([]));
    paymentApiServiceSpy.getPaymentByBooking.and.returnValue(of({
      paymentId: 'PAY-BOOT',
      bookingId: booking.bookingId,
      userId: booking.userId,
      amount: booking.totalFare,
      currency: 'INR',
      status: 'PENDING',
      paymentMode: 'UPI'
    }));

    await TestBed.configureTestingModule({
      imports: [PaymentPageComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({}),
              queryParamMap: convertToParamMap({})
            }
          }
        },
        { provide: Router, useValue: routerSpy },
        { provide: HttpClient, useValue: jasmine.createSpyObj<HttpClient>('HttpClient', ['get']) },
        { provide: BookingApiService, useValue: bookingApiServiceSpy },
        { provide: BookingJourneyService, useValue: bookingJourneyServiceSpy },
        { provide: PassengerApiService, useValue: passengerApiServiceSpy },
        { provide: PaymentApiService, useValue: paymentApiServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PaymentPageComponent);
    component = fixture.componentInstance;
  });

  it('shows error when payment is initiated without booking context', () => {
    component.booking = undefined;

    component.initiate();

    expect(component.errorMessage).toBe('Unable to load payment details');
    expect(paymentApiServiceSpy.initiatePayment).not.toHaveBeenCalled();
  });

  it('shows error when passenger details are incomplete', () => {
    component.booking = booking;
    component.passengers = [];

    component.initiate();

    expect(component.errorMessage).toBe('Please complete passenger details before payment.');
    expect(paymentApiServiceSpy.initiatePayment).not.toHaveBeenCalled();
  });

  it('initiates payment with selected mode when booking and passengers are valid', () => {
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
      }
    ];
    component.paymentForm.controls.paymentMode.setValue('CARD');

    paymentApiServiceSpy.initiatePayment.and.returnValue(of({
      paymentId: 'PAY-1',
      bookingId: booking.bookingId,
      userId: booking.userId,
      amount: booking.totalFare,
      currency: 'INR',
      status: 'PENDING',
      paymentMode: 'CARD'
    }));
    spyOn<any>(component, 'openRazorpayCheckout').and.returnValue(Promise.resolve());

    component.initiate();

    expect(paymentApiServiceSpy.initiatePayment).toHaveBeenCalledWith({
      bookingId: booking.bookingId,
      userId: booking.userId,
      amount: booking.totalFare,
      currency: 'INR',
      paymentMode: 'CARD'
    });
    expect(component.paymentId).toBe('PAY-1');
  });

  it('shows API error when initiate payment fails', () => {
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
      }
    ];
    paymentApiServiceSpy.initiatePayment.and.returnValue(throwError(() => new Error('gateway down')));

    component.initiate();

    expect(component.errorMessage).toBe('Unable to start payment. Please try again.');
    expect(component.isProcessing).toBeFalse();
  });

  it('shows error when payment mode is not selected', () => {
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
      }
    ];
    component.paymentForm.controls.paymentMode.setValue(null as any);

    component.initiate();

    expect(paymentApiServiceSpy.initiatePayment).not.toHaveBeenCalled();
    expect(component.errorMessage).toBe('Please select a payment method.');
  });

  it('shows load error when booking id cannot be resolved', () => {
    const journey = (component as any).bookingJourneyService;
    journey.getActiveBookingContext.and.returnValue(null);
    const route = (component as any).route;
    route.snapshot.paramMap = convertToParamMap({});
    route.snapshot.queryParamMap = convertToParamMap({});

    (component as any).loadCheckoutDetails();

    expect(component.errorMessage).toBe('Unable to load payment details');
    expect(component.isLoading).toBeFalse();
  });

  it('navigates to failure page when payment verification returns non-paid status', () => {
    component.booking = booking;
    component.paymentId = 'PAY-1';
    component['pnr'] = booking.pnrCode;

    paymentApiServiceSpy.verifyPayment.and.returnValue(of({
      paymentId: 'PAY-1',
      bookingId: booking.bookingId,
      userId: booking.userId,
      amount: booking.totalFare,
      currency: 'INR',
      status: 'FAILED',
      paymentMode: 'CARD',
      gatewayResponse: 'declined'
    } as any));

    component['verifyRazorpayPayment']({
      razorpay_order_id: 'order_1',
      razorpay_payment_id: 'pay_1',
      razorpay_signature: 'sig_1'
    });

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/payment-failed'], {
      queryParams: jasmine.objectContaining({
        bookingId: booking.bookingId,
        paymentId: 'PAY-1',
        status: 'FAILED'
      })
    });
  });

  it('marks payment as failed when verify API throws error', async () => {
    component.booking = booking;
    component.paymentId = 'PAY-9';
    component['pnr'] = booking.pnrCode;
    paymentApiServiceSpy.verifyPayment.and.returnValue(throwError(() => new Error('verify failed')));
    paymentApiServiceSpy.processPayment.and.returnValue(of({
      paymentId: 'PAY-9',
      bookingId: booking.bookingId,
      userId: booking.userId,
      amount: booking.totalFare,
      currency: 'INR',
      status: 'FAILED',
      paymentMode: 'UPI'
    } as any));

    await component['verifyRazorpayPayment']({
      razorpay_order_id: 'order_2',
      razorpay_payment_id: 'pay_2',
      razorpay_signature: 'sig_2'
    });

    await Promise.resolve();
    await Promise.resolve();

    expect(paymentApiServiceSpy.processPayment).toHaveBeenCalledWith({
      paymentId: 'PAY-9',
      success: false,
      gatewayResponse: 'Payment verification failed.'
    });
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/payment-failed'], {
      queryParams: jasmine.objectContaining({
        bookingId: booking.bookingId,
        paymentId: 'PAY-9',
        status: 'FAILED'
      })
    });
  });

  it('handles checkout failure even when payment id is missing', async () => {
    component.booking = booking;
    component.paymentId = '';
    component['pnr'] = booking.pnrCode;

    await component['markPaymentFailed']('Payment cancelled by user.');

    expect(paymentApiServiceSpy.processPayment).not.toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/payment-failed'], {
      queryParams: jasmine.objectContaining({
        bookingId: booking.bookingId,
        paymentId: '',
        status: 'FAILED',
        reason: 'Payment cancelled by user.'
      })
    });
  });

  it('shows payment details error when checkout is opened without booking', async () => {
    component.booking = undefined;

    await component['openRazorpayCheckout']({
      paymentId: 'PAY-3',
      bookingId: 'BKG-3',
      userId: 1,
      amount: 1000,
      currency: 'INR',
      status: 'PENDING',
      paymentMode: 'UPI'
    } as any);

    expect(component.errorMessage).toBe('Unable to load payment details');
    expect(component.isProcessing).toBeFalse();
  });

  it('computes payment readiness getters based on booking and passengers', () => {
    component.booking = booking;
    component.passengers = [];
    expect(component.requiredPassengerCount).toBe(1);
    expect(component.hasAllPassengerDetails).toBeFalse();
    expect(component.canInitiatePayment).toBeFalse();

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
      }
    ];

    expect(component.hasAllPassengerDetails).toBeTrue();
    expect(component.canInitiatePayment).toBeTrue();
  });

  it('returns null flight meta when flight id is invalid', async () => {
    const result = await firstValueFrom((component as any).fetchFlightMeta(0));
    expect(result).toBeNull();
  });

  it('maps flight meta with fallback airline when airline id is missing', async () => {
    const httpSpy = TestBed.inject(HttpClient) as jasmine.SpyObj<HttpClient>;
    httpSpy.get.and.returnValue(of({ originAirportCode: 'del', destinationAirportCode: 'bom' } as any));

    const result = await firstValueFrom((component as any).fetchFlightMeta(77));

    expect(result).toEqual({
      routeLabel: 'DEL -> BOM',
      airlineName: 'Airline unavailable'
    });
  });

  it('shows error when razorpay key fetch fails before checkout', async () => {
    component.booking = booking;
    spyOn<any>(component, 'ensureRazorpayKey').and.returnValue(Promise.reject(new Error('no key')));

    await (component as any).openRazorpayCheckout({
      paymentId: 'PAY-5',
      bookingId: booking.bookingId,
      userId: booking.userId,
      amount: booking.totalFare,
      currency: 'INR',
      status: 'PENDING',
      paymentMode: 'UPI'
    } as any);

    expect(component.errorMessage).toBe('Unable to start payment. Please try again.');
    expect(component.isProcessing).toBeFalse();
  });

  it('falls back to failed navigation when processPayment fails inside markPaymentFailed', async () => {
    component.booking = booking;
    component.paymentId = 'PAY-404';
    component['pnr'] = booking.pnrCode;
    paymentApiServiceSpy.processPayment.and.returnValue(throwError(() => new Error('process down')));

    await (component as any).markPaymentFailed('Gateway declined');

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/payment-failed'], {
      queryParams: jasmine.objectContaining({
        bookingId: booking.bookingId,
        paymentId: 'PAY-404',
        reason: 'Gateway declined'
      })
    });
  });

  it('loads checkout details again when retry is requested', () => {
    const loadSpy = spyOn<any>(component, 'loadCheckoutDetails');

    component.retryLoad();

    expect(loadSpy).toHaveBeenCalled();
  });

  it('navigates to payment success page when verification returns PAID', () => {
    component.booking = booking;
    component.paymentId = 'PAY-123';
    component['pnr'] = booking.pnrCode;
    paymentApiServiceSpy.verifyPayment.and.returnValue(of({
      paymentId: 'PAY-123',
      bookingId: booking.bookingId,
      userId: booking.userId,
      amount: booking.totalFare,
      currency: 'INR',
      status: 'PAID',
      paymentMode: 'UPI',
      gatewayResponse: 'ok'
    } as any));

    component['verifyRazorpayPayment']({
      razorpay_order_id: 'order_paid',
      razorpay_payment_id: 'payment_paid',
      razorpay_signature: 'sig_paid'
    });

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/payment-success'], {
      queryParams: jasmine.objectContaining({
        bookingId: booking.bookingId,
        pnr: booking.pnrCode,
        paymentId: 'PAY-123',
        status: 'PAID'
      })
    });
  });

  it('builds checkout preferences for all payment modes', () => {
    const upi = component['buildCheckoutPreference']('UPI');
    const card = component['buildCheckoutPreference']('CARD');
    const netBanking = component['buildCheckoutPreference']('NET_BANKING');
    const wallet = component['buildCheckoutPreference']('WALLET');

    expect(upi.method.upi).toBeTrue();
    expect(card.method.card).toBeTrue();
    expect(netBanking.method.netbanking).toBeTrue();
    expect(wallet.method.wallet).toBeTrue();
  });

  it('schedules failure handling on checkout dismiss and clears timer once completion starts', async () => {
    jasmine.clock().install();
    try {
      const markFailedSpy = spyOn<any>(component, 'markPaymentFailed').and.returnValue(Promise.resolve());

      component['scheduleDismissFailure']();
      jasmine.clock().tick(5600);
      await Promise.resolve();
      expect(markFailedSpy).toHaveBeenCalledWith('Payment cancelled by user.');

      component['paymentFailureHandled'] = false;
      component['checkoutCompletionInitiated'] = true;
      markFailedSpy.calls.reset();
      component['scheduleDismissFailure']();
      jasmine.clock().tick(5600);
      expect(markFailedSpy).not.toHaveBeenCalled();
    } finally {
      jasmine.clock().uninstall();
    }
  });

  it('ignores markPaymentFailed when payment failure already handled', async () => {
    component['paymentFailureHandled'] = true;
    paymentApiServiceSpy.processPayment.calls.reset();

    await component['markPaymentFailed']('Already processed');

    expect(paymentApiServiceSpy.processPayment).not.toHaveBeenCalled();
  });
});
