import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AirlineAirportApiService } from '../../../core/services/airline-airport-api.service';
import { BookingApiService } from '../../../core/services/booking-api.service';
import { FlightApiService } from '../../../core/services/flight-api.service';
import { PassengerApiService } from '../../../core/services/passenger-api.service';
import { PnrStatusPageComponent } from './pnr-status-page.component';

describe('PnrStatusPageComponent', () => {
  let fixture: ComponentFixture<PnrStatusPageComponent>;
  let component: PnrStatusPageComponent;
  let bookingApiSpy: jasmine.SpyObj<BookingApiService>;
  let flightApiSpy: jasmine.SpyObj<FlightApiService>;
  let passengerApiSpy: jasmine.SpyObj<PassengerApiService>;

  beforeEach(async () => {
    bookingApiSpy = jasmine.createSpyObj<BookingApiService>('BookingApiService', ['getBookingByPnr']);
    flightApiSpy = jasmine.createSpyObj<FlightApiService>('FlightApiService', ['getFlightById']);
    passengerApiSpy = jasmine.createSpyObj<PassengerApiService>('PassengerApiService', ['getPassengersByBooking']);
    const airlineApiSpy = jasmine.createSpyObj<AirlineAirportApiService>('AirlineAirportApiService', ['getAirlines']);
    airlineApiSpy.getAirlines.and.returnValue(of([{ airlineId: 7, name: 'Sky Air' }] as any));

    await TestBed.configureTestingModule({
      imports: [PnrStatusPageComponent],
      providers: [
        { provide: BookingApiService, useValue: bookingApiSpy },
        { provide: FlightApiService, useValue: flightApiSpy },
        { provide: PassengerApiService, useValue: passengerApiSpy },
        { provide: AirlineAirportApiService, useValue: airlineApiSpy }
      ]
    })
      .overrideComponent(PnrStatusPageComponent, { set: { template: '' } })
      .compileComponents();

    fixture = TestBed.createComponent(PnrStatusPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('blocks submission when form is invalid', () => {
    component.pnrForm.controls.pnr.setValue('');

    component.checkStatus();

    expect(bookingApiSpy.getBookingByPnr).not.toHaveBeenCalled();
    expect(component.pnrForm.controls.pnr.touched).toBeTrue();
  });

  it('loads booking, flight, and passengers for valid PNR', () => {
    component.pnrForm.controls.pnr.setValue('pnr12');
    bookingApiSpy.getBookingByPnr.and.returnValue(of({ bookingId: 'BKG-1', pnrCode: 'PNR12', flightId: 99, seatIds: [11], status: 'CONFIRMED' } as any));
    flightApiSpy.getFlightById.and.returnValue(of({ airlineId: 7, originAirportCode: 'DEL', destinationAirportCode: 'BLR' } as any));
    passengerApiSpy.getPassengersByBooking.and.returnValue(of([{ title: 'Mr', firstName: 'A', lastName: 'B', seatNumber: '12A' }] as any));

    component.checkStatus();

    expect(bookingApiSpy.getBookingByPnr).toHaveBeenCalledWith('PNR12');
    expect(component.booking?.bookingId).toBe('BKG-1');
    expect(component.passengerName).toContain('Mr A B');
    expect(component.routeText).toContain('DEL');
    expect(component.airlineName).toBe('Sky Air');
  });

  it('shows not-found message for 404 response', () => {
    component.pnrForm.controls.pnr.setValue('PNR404');
    bookingApiSpy.getBookingByPnr.and.returnValue(throwError(() => ({ status: 404 })));

    component.checkStatus();

    expect(component.errorMessage).toBe('No booking found for this PNR');
    expect(component.isLoading).toBeFalse();
  });

  it('shows generic message for unexpected booking error', () => {
    component.pnrForm.controls.pnr.setValue('PNR500');
    bookingApiSpy.getBookingByPnr.and.returnValue(throwError(() => ({ status: 500 })));

    component.checkStatus();

    expect(component.errorMessage).toContain('Unable to fetch booking details');
  });
});
