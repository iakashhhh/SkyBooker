import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';

import { BookingApiService } from '../../../core/services/booking-api.service';
import { FlightApiService } from '../../../core/services/flight-api.service';
import { PassengerApiService } from '../../../core/services/passenger-api.service';
import { PublicTicketPageComponent } from './public-ticket-page.component';

describe('PublicTicketPageComponent', () => {
  let fixture: ComponentFixture<PublicTicketPageComponent>;
  let component: PublicTicketPageComponent;
  let bookingApiSpy: jasmine.SpyObj<BookingApiService>;
  let passengerApiSpy: jasmine.SpyObj<PassengerApiService>;
  let flightApiSpy: jasmine.SpyObj<FlightApiService>;

  beforeEach(async () => {
    bookingApiSpy = jasmine.createSpyObj<BookingApiService>('BookingApiService', ['getBookingById']);
    passengerApiSpy = jasmine.createSpyObj<PassengerApiService>('PassengerApiService', ['getPassengersByBooking']);
    flightApiSpy = jasmine.createSpyObj<FlightApiService>('FlightApiService', ['getFlightById']);

    await TestBed.configureTestingModule({
      imports: [PublicTicketPageComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ id: 'BKG-7' })
            }
          }
        },
        { provide: BookingApiService, useValue: bookingApiSpy },
        { provide: PassengerApiService, useValue: passengerApiSpy },
        { provide: FlightApiService, useValue: flightApiSpy }
      ]
    })
      .overrideComponent(PublicTicketPageComponent, { set: { template: '' } })
      .compileComponents();

    fixture = TestBed.createComponent(PublicTicketPageComponent);
    component = fixture.componentInstance;
  });

  it('loads ticket details on valid ticket id', () => {
    bookingApiSpy.getBookingById.and.returnValue(of({ bookingId: 'BKG-7', flightId: 20 } as any));
    passengerApiSpy.getPassengersByBooking.and.returnValue(of([{ passengerId: 1 }] as any));
    flightApiSpy.getFlightById.and.returnValue(of({ flightNumber: 'AI-20' } as any));

    fixture.detectChanges();

    expect(component.booking?.bookingId).toBe('BKG-7');
    expect(component.passengers.length).toBe(1);
    expect(component.flight?.flightNumber).toBe('AI-20');
  });

  it('shows invalid link error when id missing', () => {
    const route = TestBed.inject(ActivatedRoute) as ActivatedRoute & {
      snapshot: { paramMap: ReturnType<typeof convertToParamMap> };
    };
    route.snapshot.paramMap = convertToParamMap({});

    fixture = TestBed.createComponent(PublicTicketPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.errorMessage).toBe('Invalid ticket link.');
    expect(bookingApiSpy.getBookingById).not.toHaveBeenCalled();
  });

  it('shows not found error when booking lookup fails', () => {
    bookingApiSpy.getBookingById.and.returnValue(throwError(() => new Error('404')));

    fixture.detectChanges();

    expect(component.errorMessage).toBe('Ticket not found or unavailable.');
    expect(component.isLoading).toBeFalse();
  });

  it('exposes public ticket URL when booking exists', () => {
    component.booking = { bookingId: 'BKG-URL' } as any;

    expect(component.ticketUrl).toContain('/ticket/BKG-URL');
  });
});
