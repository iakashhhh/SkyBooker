import { ChangeDetectorRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { BookingApiService } from '../../../core/services/booking-api.service';
import { FlightApiService } from '../../../core/services/flight-api.service';
import { PassengerApiService } from '../../../core/services/passenger-api.service';
import { TokenStorageService } from '../../../core/services/token-storage.service';
import { MyBookingsPageComponent } from './my-bookings-page.component';

describe('MyBookingsPageComponent', () => {
  let fixture: ComponentFixture<MyBookingsPageComponent>;
  let component: MyBookingsPageComponent;
  let bookingApiSpy: jasmine.SpyObj<BookingApiService>;
  let flightApiSpy: jasmine.SpyObj<FlightApiService>;
  let passengerApiSpy: jasmine.SpyObj<PassengerApiService>;
  let tokenStorageSpy: jasmine.SpyObj<TokenStorageService>;

  beforeEach(async () => {
    bookingApiSpy = jasmine.createSpyObj<BookingApiService>('BookingApiService', ['getBookingsByUser', 'cancelBooking']);
    flightApiSpy = jasmine.createSpyObj<FlightApiService>('FlightApiService', ['getFlightById']);
    passengerApiSpy = jasmine.createSpyObj<PassengerApiService>('PassengerApiService', ['getPassengersByBooking']);
    tokenStorageSpy = jasmine.createSpyObj<TokenStorageService>('TokenStorageService', ['getUserId']);

    await TestBed.configureTestingModule({
      imports: [MyBookingsPageComponent],
      providers: [
        { provide: BookingApiService, useValue: bookingApiSpy },
        { provide: FlightApiService, useValue: flightApiSpy },
        { provide: PassengerApiService, useValue: passengerApiSpy },
        { provide: TokenStorageService, useValue: tokenStorageSpy },
        { provide: ChangeDetectorRef, useValue: { markForCheck: () => {} } }
      ]
    })
      .overrideComponent(MyBookingsPageComponent, { set: { template: '' } })
      .compileComponents();

    localStorage.clear();
    fixture = TestBed.createComponent(MyBookingsPageComponent);
    component = fixture.componentInstance;
  });

  it('shows login-required error when user id is missing', () => {
    tokenStorageSpy.getUserId.and.returnValue(null as any);

    fixture.detectChanges();

    expect(component.errorMessage).toContain('Login required');
    expect(bookingApiSpy.getBookingsByUser).not.toHaveBeenCalled();
  });

  it('loads bookings and background passenger/flight details', () => {
    tokenStorageSpy.getUserId.and.returnValue(7);
    bookingApiSpy.getBookingsByUser.and.returnValue(of([
      { bookingId: 'BKG-2', flightId: 20, bookedAt: '2026-05-01T10:00:00Z', status: 'PENDING', pnrCode: 'PNR2', seatIds: [2], totalFare: 3000 },
      { bookingId: 'BKG-1', flightId: 10, bookedAt: '2026-05-02T10:00:00Z', status: 'CONFIRMED', pnrCode: 'PNR1', seatIds: [1], totalFare: 4000 }
    ] as any));
    passengerApiSpy.getPassengersByBooking.and.returnValue(of([{ passengerId: 1 }] as any));
    flightApiSpy.getFlightById.and.returnValue(of({ flightNumber: 'AI-101' } as any));

    fixture.detectChanges();

    expect(bookingApiSpy.getBookingsByUser).toHaveBeenCalledWith(7);
    expect(component.bookingTickets.length).toBe(2);
    expect(component.bookingTickets[0].booking.bookingId).toBe('BKG-1');
    expect(component.statusFilters).toContain('CONFIRMED');
    expect(passengerApiSpy.getPassengersByBooking).toHaveBeenCalled();
    expect(flightApiSpy.getFlightById).toHaveBeenCalled();
  });

  it('shows booking API failure message in error flow', () => {
    tokenStorageSpy.getUserId.and.returnValue(7);
    bookingApiSpy.getBookingsByUser.and.returnValue(throwError(() => new Error('down')));

    fixture.detectChanges();

    expect(component.errorMessage).toBe('Unable to fetch your bookings right now.');
    expect(component.isLoading).toBeFalse();
  });

  it('supports user actions: open/close modal and filter reset', () => {
    component.openTicketModal({ bookingId: 'BKG-1' } as any, [{ passengerId: 1 } as any], { flightId: 10 } as any);
    expect(component.isTicketModalOpen).toBeTrue();

    component.closeTicketModal();
    expect(component.isTicketModalOpen).toBeFalse();

    component.hiddenBookingIds.add('BKG-1');
    component.selectedStatus = 'CONFIRMED';
    component.selectedMonth = '2026-05';

    component.resetFilters();

    expect(component.selectedStatus).toBe('ALL');
    expect(component.selectedMonth).toBe('ALL');
    expect(component.hiddenBookingIds.size).toBe(0);
  });

  it('filters out hidden and stale pending bookings from visible list', () => {
    const staleDate = new Date(Date.now() - 60 * 60 * 1000).toISOString();
    component.bookingTickets = [
      {
        booking: { bookingId: 'BKG-HIDDEN', bookedAt: staleDate, status: 'CONFIRMED' } as any,
        passengers: [{ passengerId: 1 } as any],
        flight: null
      },
      {
        booking: { bookingId: 'BKG-STALE', bookedAt: staleDate, status: 'PENDING' } as any,
        passengers: [],
        flight: null
      },
      {
        booking: { bookingId: 'BKG-OK', bookedAt: staleDate, status: 'CONFIRMED' } as any,
        passengers: [{ passengerId: 2 } as any],
        flight: null
      }
    ];
    component.hiddenBookingIds.add('BKG-HIDDEN');

    expect(component.visibleBookingTickets.map((ticket) => ticket.booking.bookingId)).toEqual(['BKG-OK']);
  });

  it('applies status and month filters to visible bookings', () => {
    component.bookingTickets = [
      {
        booking: { bookingId: 'BKG-1', bookedAt: '2026-05-01T10:00:00Z', status: 'CONFIRMED' } as any,
        passengers: [{ passengerId: 1 } as any],
        flight: null
      },
      {
        booking: { bookingId: 'BKG-2', bookedAt: '2026-04-01T10:00:00Z', status: 'PENDING' } as any,
        passengers: [{ passengerId: 2 } as any],
        flight: null
      }
    ];
    component.selectedStatus = 'CONFIRMED';
    component.selectedMonth = '2026-05';

    expect(component.visibleBookingTickets.length).toBe(1);
    expect(component.visibleBookingTickets[0].booking.bookingId).toBe('BKG-1');
  });

  it('persists hidden booking id when removed from view', () => {
    (component as any).userId = 99;

    component.removeBookingFromView('BKG-11');

    expect(component.hiddenBookingIds.has('BKG-11')).toBeTrue();
    expect(localStorage.getItem('skybooker.hiddenBookings.99')).toContain('BKG-11');
  });

  it('returns plain ticket content fallback URL for local/private host', () => {
    const booking = {
      bookingId: 'BKG-21',
      pnrCode: 'PNR21',
      bookedAt: '2026-05-01T10:00:00Z',
      status: 'CONFIRMED',
      seatIds: [11],
      totalFare: 4500
    } as any;
    component.selectedBooking = booking;
    component.selectedPassenger = { title: 'Mr', firstName: 'Akash', lastName: 'Sharma', seatNumber: '11A' } as any;
    component.selectedFlight = { originAirportCode: 'DEL', destinationAirportCode: 'BLR', departureTime: '2026-05-10T10:00:00Z' } as any;

    const ticketText = component.getPublicTicketUrl('BKG-21');

    expect(ticketText).toContain('SkyBooker Boarding Pass');
    expect(ticketText).toContain('PNR: PNR21');
    expect(ticketText).toContain('Route: DEL -> BLR');
  });

  it('shows error when download is requested without printable ticket root', async () => {
    component.selectedBooking = { bookingId: 'BKG-50' } as any;
    component.ticketPrintableRef = undefined;

    await component.downloadTicket('BKG-50');

    expect(component.errorMessage).toContain('Ticket layout is not ready');
    expect(component.isDownloadingPdf).toBeFalse();
  });

  it('cancels and hides stale pending booking during cleanup', () => {
    (component as any).userId = 7;
    const staleDate = new Date(Date.now() - 60 * 60 * 1000).toISOString();
    component.bookingTickets = [
      {
        booking: { bookingId: 'BKG-CLEAN', bookedAt: staleDate, status: 'PENDING' } as any,
        passengers: [],
        flight: null
      }
    ];
    bookingApiSpy.cancelBooking.and.returnValue(of({ bookingId: 'BKG-CLEAN', status: 'CANCELLED' } as any));

    (component as any).cleanupGarbagePendingBooking('BKG-CLEAN');

    expect(bookingApiSpy.cancelBooking).toHaveBeenCalledWith('BKG-CLEAN');
    expect(component.hiddenBookingIds.has('BKG-CLEAN')).toBeTrue();
  });

  it('handles background detail API failures safely', () => {
    component.bookingTickets = [
      {
        booking: { bookingId: 'BKG-X', flightId: 9, bookedAt: '2026-05-02T10:00:00Z', status: 'CONFIRMED' } as any,
        passengers: [],
        flight: null
      }
    ];
    passengerApiSpy.getPassengersByBooking.and.returnValue(throwError(() => new Error('down')));
    flightApiSpy.getFlightById.and.returnValue(throwError(() => new Error('down')));

    (component as any).loadTicketDetailsInBackground();

    expect(passengerApiSpy.getPassengersByBooking).toHaveBeenCalledWith('BKG-X');
    expect(flightApiSpy.getFlightById).toHaveBeenCalledWith(9);
  });
});
