import { HttpErrorResponse } from '@angular/common/http';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AdminApiService } from './admin-api.service';

describe('AdminApiService', () => {
  let service: AdminApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AdminApiService, provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(AdminApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads managed flights with date query param', () => {
    const response = [{ flightId: 101, flightNumber: 'AI-101' }] as any;

    service.getManagedFlights('2026-05-02').subscribe((flights) => {
      expect(flights).toEqual(response);
    });

    const req = httpMock.expectOne((request) =>
      request.method === 'GET'
      && request.url === 'http://localhost:8080/api/v1/admin/flights'
      && request.params.get('date') === '2026-05-02'
    );
    req.flush(response);
  });

  it('creates a managed flight with expected payload', () => {
    const payload = {
      flightNumber: 'AI-220',
      airlineId: 9,
      originAirportCode: 'DEL',
      destinationAirportCode: 'BLR',
      departureTime: '2026-06-01T09:00:00',
      arrivalTime: '2026-06-01T11:30:00',
      durationMinutes: 150,
      numberOfStops: 0,
      status: 'ON_TIME',
      aircraftType: 'A320',
      totalSeats: 180,
      availableSeats: 180,
      basePrice: 5600
    } as const;

    service.createManagedFlight(payload).subscribe((flight) => {
      expect(flight.flightNumber).toBe('AI-220');
    });

    const req = httpMock.expectOne('http://localhost:8080/api/v1/admin/flights');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({ flightId: 201, ...payload });
  });

  it('cancels managed booking using bookingId path parameter', () => {
    service.cancelManagedBooking({ bookingId: 'BKG-99', reason: 'Passenger request' }).subscribe((result) => {
      expect(result).toEqual({ status: 'CANCELLED' });
    });

    const req = httpMock.expectOne('http://localhost:8080/api/v1/admin/bookings/BKG-99/cancel');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ bookingId: 'BKG-99', reason: 'Passenger request' });
    req.flush({ status: 'CANCELLED' });
  });

  it('returns backend errors for users endpoint', () => {
    let capturedError: HttpErrorResponse | undefined;

    service.getUsers().subscribe({
      next: () => fail('expected error'),
      error: (error) => {
        capturedError = error;
      }
    });

    const req = httpMock.expectOne('http://localhost:8080/api/v1/admin/users');
    expect(req.request.method).toBe('GET');
    req.flush({ message: 'forbidden' }, { status: 403, statusText: 'Forbidden' });

    expect(capturedError?.status).toBe(403);
  });
});
