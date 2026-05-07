import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TicketCardComponent } from './ticket-card.component';

describe('TicketCardComponent', () => {
  let fixture: ComponentFixture<TicketCardComponent>;
  let component: TicketCardComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TicketCardComponent]
    })
      .overrideComponent(TicketCardComponent, { set: { template: '' } })
      .compileComponents();

    fixture = TestBed.createComponent(TicketCardComponent);
    component = fixture.componentInstance;
    component.booking = {
      bookingId: 'BKG-1',
      pnrCode: 'PNR-1',
      status: 'PENDING',
      flightId: 88,
      totalFare: 3500,
      seatIds: [11, 12],
      bookedAt: '2026-05-01T10:00:00Z'
    } as any;
    component.passengers = [];
    component.flight = null;
    fixture.detectChanges();
  });

  it('falls back to booking seat ids and generated flight number when flight/passenger missing', () => {
    expect(component.flightNumber).toBe('SKY088');
    expect(component.seatLabel).toContain('Seat 11');
    expect(component.passengerName).toBe('Passenger details pending');
  });

  it('uses passenger and flight data when available', () => {
    component.passengers = [{ title: 'Mr', firstName: 'Akash', lastName: 'Sharma', seatNumber: '14C' } as any];
    component.flight = { flightNumber: 'ai-220', originAirportCode: 'del', destinationAirportCode: 'blr', departureTime: '2026-05-03T12:00:00Z' } as any;

    expect(component.passengerName).toBe('Mr Akash Sharma');
    expect(component.departureLabel).toBe('DEL');
    expect(component.arrivalLabel).toBe('BLR');
    expect(component.flightNumber).toBe('AI-220');
    expect(component.seatLabel).toBe('14C');
  });

  it('emits ticketClick on openTicket user action', () => {
    const emitSpy = spyOn(component.ticketClick, 'emit');

    component.openTicket();

    expect(emitSpy).toHaveBeenCalled();
  });

  it('builds qr fallback payload with booking context', () => {
    const value = component.qrCodeValue;

    expect(value).toContain('PNR: PNR-1');
    expect(value).toContain('Booking ID: BKG-1');
  });
});
