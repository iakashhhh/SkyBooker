import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';

import { AirlineAirportApiService } from './airline-airport-api.service';

describe('AirlineAirportApiService', () => {
  let service: AirlineAirportApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AirlineAirportApiService, provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(AirlineAirportApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('handles airline CRUD routes', () => {
    service.getAirlines().subscribe((response) => {
      expect(response.length).toBe(1);
    });
    const getReq = httpMock.expectOne('http://localhost:8080/airlines');
    expect(getReq.request.method).toBe('GET');
    getReq.flush([{ airlineId: 1, name: 'Sky', iataCode: 'SK', country: 'IN' }]);

    service.createAirline({ name: 'Sky', iataCode: 'SK', country: 'IN' } as any).subscribe();
    const createReq = httpMock.expectOne('http://localhost:8080/airlines');
    expect(createReq.request.method).toBe('POST');
    createReq.flush({ airlineId: 1 });

    service.updateAirline(1, { name: 'SkyX', iataCode: 'SX', country: 'IN' } as any).subscribe();
    const updateReq = httpMock.expectOne('http://localhost:8080/airlines/1');
    expect(updateReq.request.method).toBe('PUT');
    updateReq.flush({ airlineId: 1 });

    service.deleteAirline(1).subscribe();
    const deleteReq = httpMock.expectOne('http://localhost:8080/airlines/1');
    expect(deleteReq.request.method).toBe('DELETE');
    deleteReq.flush({});
  });

  it('searches airports with query params', () => {
    service.searchAirports('del').subscribe((response) => {
      expect(response.length).toBe(1);
    });

    const req = httpMock.expectOne((request) =>
      request.url === 'http://localhost:8080/airports/search' && request.params.get('query') === 'del'
    );
    expect(req.request.method).toBe('GET');
    req.flush([{ airportId: 1, iataCode: 'DEL' }]);
  });

  it('handles airport CRUD routes', () => {
    service.getAirports().subscribe((response) => {
      expect(response.length).toBe(1);
    });
    const getReq = httpMock.expectOne('http://localhost:8080/airports');
    expect(getReq.request.method).toBe('GET');
    getReq.flush([{ airportId: 5, iataCode: 'BLR' }]);

    service.createAirport({ name: 'BLR', iataCode: 'BLR', city: 'Bengaluru', country: 'IN' } as any).subscribe();
    const createReq = httpMock.expectOne('http://localhost:8080/airports');
    expect(createReq.request.method).toBe('POST');
    createReq.flush({ airportId: 5 });

    service.updateAirport(5, { name: 'BLR2', iataCode: 'BLR', city: 'Bengaluru', country: 'IN' } as any).subscribe();
    const updateReq = httpMock.expectOne('http://localhost:8080/airports/5');
    expect(updateReq.request.method).toBe('PUT');
    updateReq.flush({ airportId: 5 });

    service.deleteAirport(5).subscribe();
    const deleteReq = httpMock.expectOne('http://localhost:8080/airports/5');
    expect(deleteReq.request.method).toBe('DELETE');
    deleteReq.flush({});
  });

  it('surfaces API errors from getAirlines', () => {
    let actualError: HttpErrorResponse | undefined;

    service.getAirlines().subscribe({
      error: (error) => {
        actualError = error;
      }
    });

    const req = httpMock.expectOne('http://localhost:8080/airlines');
    req.flush({ message: 'Service unavailable' }, { status: 503, statusText: 'Service Unavailable' });

    expect(actualError?.status).toBe(503);
  });
});
