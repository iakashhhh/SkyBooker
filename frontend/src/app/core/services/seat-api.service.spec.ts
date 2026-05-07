import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';

import { SeatApiService } from './seat-api.service';

describe('SeatApiService', () => {
  let service: SeatApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [SeatApiService, provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(SeatApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads seat map with and without total seats', () => {
    service.getSeatMap(55).subscribe();
    const defaultReq = httpMock.expectOne('http://localhost:8080/seats/55');
    expect(defaultReq.request.method).toBe('GET');
    defaultReq.flush({ seats: [] });

    service.getSeatMap(55, 180).subscribe();
    const withTotalReq = httpMock.expectOne((request) =>
      request.url === 'http://localhost:8080/seats/55' && request.params.get('totalSeats') === '180'
    );
    expect(withTotalReq.request.method).toBe('GET');
    withTotalReq.flush({ seats: [] });
  });

  it('posts hold/release/confirm seat actions', () => {
    const payload = { flightId: 55, seatIds: [1, 2] };

    service.holdSeats(payload).subscribe();
    const holdReq = httpMock.expectOne('http://localhost:8080/seats/hold');
    expect(holdReq.request.method).toBe('POST');
    holdReq.flush({ success: true });

    service.releaseSeats(payload).subscribe();
    const releaseReq = httpMock.expectOne('http://localhost:8080/seats/release');
    expect(releaseReq.request.method).toBe('POST');
    releaseReq.flush({ success: true });

    service.confirmSeats(payload).subscribe();
    const confirmReq = httpMock.expectOne('http://localhost:8080/seats/confirm');
    expect(confirmReq.request.method).toBe('POST');
    confirmReq.flush({ success: true });
  });

  it('treats zero total seats as non-parameterized request', () => {
    service.getSeatMap(88, 0).subscribe();

    const req = httpMock.expectOne('http://localhost:8080/seats/88');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.has('totalSeats')).toBeFalse();
    req.flush({ seats: [] });
  });

  it('propagates hold seat failure response', () => {
    let actualError: HttpErrorResponse | undefined;

    service.holdSeats({ flightId: 55, seatIds: [99] }).subscribe({
      error: (error) => {
        actualError = error;
      }
    });

    const req = httpMock.expectOne('http://localhost:8080/seats/hold');
    req.flush({ message: 'Seat unavailable' }, { status: 409, statusText: 'Conflict' });

    expect(actualError?.status).toBe(409);
  });
});
