import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';

import { FlightApiService } from './flight-api.service';

describe('FlightApiService', () => {
  let service: FlightApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [FlightApiService, provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(FlightApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('searches one-way flights and serializes query params', () => {
    service.searchOneWay({
      origin: 'DEL',
      destination: 'BLR',
      journeyDate: '2026-05-10',
      sortBy: '',
      airlineId: undefined
    }).subscribe();

    const req = httpMock.expectOne((request) => request.url === 'http://localhost:8080/flights/search');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('origin')).toBe('DEL');
    expect(req.request.params.get('destination')).toBe('BLR');
    expect(req.request.params.get('journeyDate')).toBe('2026-05-10');
    expect(req.request.params.has('sortBy')).toBeFalse();
    expect(req.request.params.has('airlineId')).toBeFalse();
    req.flush([]);
  });

  it('searches round-trip and loads by id', () => {
    service.searchRoundTrip({
      origin: 'DEL',
      destination: 'BLR',
      onwardDate: '2026-05-10',
      returnDate: '2026-05-14'
    }).subscribe((response) => {
      expect(response.outboundFlights.length).toBe(1);
    });

    const roundReq = httpMock.expectOne('http://localhost:8080/flights/search/round-trip?origin=DEL&destination=BLR&onwardDate=2026-05-10&returnDate=2026-05-14');
    expect(roundReq.request.method).toBe('GET');
    roundReq.flush({ outboundFlights: [{}], returnFlights: [] });

    service.getFlightById(77).subscribe((response) => {
      expect(response.flightId).toBe(77);
    });
    const idReq = httpMock.expectOne('http://localhost:8080/flights/77');
    expect(idReq.request.method).toBe('GET');
    idReq.flush({ flightId: 77 });
  });

  it('preserves numeric filters and empty result payloads', () => {
    service.searchOneWay({
      origin: 'DEL',
      destination: 'BOM',
      journeyDate: '2026-06-20',
      sortBy: null as any,
      airlineId: 42
    }).subscribe((response) => {
      expect(response).toEqual([]);
    });

    const req = httpMock.expectOne((request) =>
      request.url === 'http://localhost:8080/flights/search'
      && request.params.get('airlineId') === '42'
      && request.params.has('sortBy') === false
    );
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('propagates backend error for missing flight id', () => {
    let actualError: HttpErrorResponse | undefined;

    service.getFlightById(9999).subscribe({
      error: (error) => {
        actualError = error;
      }
    });

    const req = httpMock.expectOne('http://localhost:8080/flights/9999');
    req.flush({ message: 'Flight not found' }, { status: 404, statusText: 'Not Found' });

    expect(actualError?.status).toBe(404);
    expect(actualError?.error?.message).toBe('Flight not found');
  });
});
