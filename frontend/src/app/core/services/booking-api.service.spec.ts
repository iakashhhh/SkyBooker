import { provideHttpClient } from '@angular/common/http';
import { HttpErrorResponse } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { BookingApiService } from './booking-api.service';

describe('BookingApiService', () => {
  let service: BookingApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [BookingApiService, provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(BookingApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('creates booking and hits expected URL', () => {
    const requestBody = {
      userId: 7,
      flightId: 12,
      tripType: 'ONE_WAY' as const,
      baseFare: 4500,
      seatIds: [101],
      luggageKg: 15,
      contactEmail: 'a@test.com',
      contactPhone: '9999999999'
    };

    let responseBody: unknown;
    service.createBooking(requestBody).subscribe((response) => {
      responseBody = response;
    });

    const req = httpMock.expectOne('http://localhost:8080/api/v1/bookings');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(requestBody);

    req.flush({ bookingId: 'BKG-1' });
    expect(responseBody).toEqual({ bookingId: 'BKG-1' });
  });

  it('propagates backend error for booking lookup', () => {
    let errorResponse: HttpErrorResponse | undefined;

    service.getBookingById('BKG-404').subscribe({
      next: () => fail('expected error'),
      error: (error) => {
        errorResponse = error;
      }
    });

    const req = httpMock.expectOne('http://localhost:8080/api/v1/bookings/BKG-404');
    expect(req.request.method).toBe('GET');
    req.flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });

    expect(errorResponse?.status).toBe(404);
  });

  it('sends cancel payload with bookingId', () => {
    service.cancelBooking('BKG-8').subscribe();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/bookings/cancel');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ bookingId: 'BKG-8' });
    req.flush({ status: 'CANCELLED' });
  });

  it('returns ticket URL for QR rendering', () => {
    expect(service.getTicketQrUrl('BKG-8')).toBe('http://localhost:8080/api/v1/bookings/BKG-8/ticket/qr');
  });
});
