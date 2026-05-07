import { provideHttpClient } from '@angular/common/http';
import { HttpErrorResponse } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PaymentApiService } from './payment-api.service';

describe('PaymentApiService', () => {
  let service: PaymentApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [PaymentApiService, provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(PaymentApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('initiates payment via expected endpoint', () => {
    const payload = {
      bookingId: 'BKG-1',
      userId: 11,
      amount: 5999,
      currency: 'INR',
      paymentMode: 'UPI' as const
    };

    let responseBody: unknown;
    service.initiatePayment(payload).subscribe((response) => {
      responseBody = response;
    });

    const req = httpMock.expectOne('http://localhost:8080/payments/initiate');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({ paymentId: 'PAY-1', status: 'PENDING' });

    expect(responseBody).toEqual({ paymentId: 'PAY-1', status: 'PENDING' });
  });

  it('propagates verification failure from API', () => {
    let errorResponse: HttpErrorResponse | undefined;

    service.verifyPayment({ bookingId: 'BKG-1', razorpaySignature: 'bad-signature' }).subscribe({
      next: () => fail('expected verify error'),
      error: (error) => {
        errorResponse = error;
      }
    });

    const req = httpMock.expectOne('http://localhost:8080/payments/verify');
    expect(req.request.method).toBe('POST');
    req.flush({ message: 'signature failed' }, { status: 400, statusText: 'Bad Request' });

    expect(errorResponse?.status).toBe(400);
  });

  it('fetches payment key and booking payment details', () => {
    service.getPaymentKey().subscribe((response) => {
      expect(response.key).toBe('rzp_key');
    });
    const keyReq = httpMock.expectOne('http://localhost:8080/payments/key');
    expect(keyReq.request.method).toBe('GET');
    keyReq.flush({ key: 'rzp_key' });

    service.getPaymentByBooking('BKG-9').subscribe((response) => {
      expect(response.bookingId).toBe('BKG-9');
    });
    const bookingReq = httpMock.expectOne('http://localhost:8080/payments/booking/BKG-9');
    expect(bookingReq.request.method).toBe('GET');
    bookingReq.flush({ bookingId: 'BKG-9' });
  });
});
