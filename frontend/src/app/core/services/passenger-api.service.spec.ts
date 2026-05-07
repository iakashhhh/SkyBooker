import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';

import { PassengerApiService } from './passenger-api.service';

describe('PassengerApiService', () => {
  let service: PassengerApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [PassengerApiService, provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(PassengerApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('adds and updates passenger via expected endpoints', () => {
    const payload = {
      bookingId: 'BKG-1',
      title: 'Mr',
      firstName: 'Akash',
      lastName: 'Sharma',
      dateOfBirth: '1990-01-01',
      gender: 'Male',
      passengerType: 'ADULT',
      passportNumber: 'P1234567',
      nationality: 'Indian',
      passportExpiry: '2030-01-01'
    };

    service.addPassenger(payload as any).subscribe();
    const addReq = httpMock.expectOne('http://localhost:8080/api/v1/passengers/add');
    expect(addReq.request.method).toBe('POST');
    addReq.flush({ passengerId: 1 });

    service.updatePassenger(1, payload as any).subscribe();
    const updateReq = httpMock.expectOne('http://localhost:8080/api/v1/passengers/1');
    expect(updateReq.request.method).toBe('PUT');
    updateReq.flush({ passengerId: 1 });
  });

  it('gets passengers by booking and assigns seat', () => {
    service.getPassengersByBooking('BKG-2').subscribe((response) => {
      expect(response.length).toBe(1);
    });
    const listReq = httpMock.expectOne('http://localhost:8080/api/v1/passengers/booking/BKG-2');
    expect(listReq.request.method).toBe('GET');
    listReq.flush([{ passengerId: 2 }]);

    service.assignSeat(2, { seatId: 10, bookingId: 'BKG-2' } as any).subscribe();
    const assignReq = httpMock.expectOne('http://localhost:8080/api/v1/passengers/2/assign-seat');
    expect(assignReq.request.method).toBe('PUT');
    assignReq.flush({ passengerId: 2, seatNumber: '12A' });
  });

  it('handles empty passenger list response', () => {
    service.getPassengersByBooking('BKG-EMPTY').subscribe((response) => {
      expect(response).toEqual([]);
    });

    const req = httpMock.expectOne('http://localhost:8080/api/v1/passengers/booking/BKG-EMPTY');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('propagates assign-seat API error', () => {
    let actualError: HttpErrorResponse | undefined;

    service.assignSeat(999, { seatId: 300, bookingId: 'BKG-404' } as any).subscribe({
      error: (error) => {
        actualError = error;
      }
    });

    const req = httpMock.expectOne('http://localhost:8080/api/v1/passengers/999/assign-seat');
    req.flush({ message: 'Passenger not found' }, { status: 404, statusText: 'Not Found' });

    expect(actualError?.status).toBe(404);
  });
});
