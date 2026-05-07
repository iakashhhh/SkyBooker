import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FinalTicketComponent } from './final-ticket.component';

describe('FinalTicketComponent', () => {
  let fixture: ComponentFixture<FinalTicketComponent>;
  let component: FinalTicketComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FinalTicketComponent]
    })
      .overrideComponent(FinalTicketComponent, { set: { template: '' } })
      .compileComponents();

    fixture = TestBed.createComponent(FinalTicketComponent);
    component = fixture.componentInstance;
    component.booking = {
      bookingId: 'BKG-2',
      pnrCode: 'PNR-2',
      status: 'CONFIRMED',
      flightId: 9,
      seatIds: [1],
      totalFare: 5000,
      bookedAt: '2026-05-02T09:00:00Z'
    } as any;
    fixture.detectChanges();
  });

  it('returns passenger fallback labels when passenger data missing', () => {
    expect(component.primaryPassenger).toBeNull();
    expect(component.passengerLabel).toBe('Passenger details pending');
    expect(component.seatLabel).toBe('S-1');
  });

  it('uses flight and passenger fields when available', () => {
    component.passenger = { title: 'Ms', firstName: 'Riya', lastName: 'S', seatNumber: '6A' } as any;
    component.flight = {
      flightNumber: 'ai-991',
      originAirportCode: 'del',
      destinationAirportCode: 'bom',
      departureTime: '2026-05-03T12:30:00Z'
    } as any;

    expect(component.passengerLabel).toBe('Ms Riya S');
    expect(component.routeFrom).toBe('DEL');
    expect(component.routeTo).toBe('BOM');
    expect(component.flightNumber).toBe('AI-991');
    expect(component.gateLabel).toBe('G6');
  });

  it('handles invalid booking date with fallback labels', () => {
    component.booking = { ...component.booking, bookedAt: 'bad-date' } as any;
    component.flight = null;

    expect(component.travelDate).toBe('Date pending');
    expect(component.departureTime).toBe('Time pending');
    expect(component.boardingTime).toBe('TBD');
  });

  it('builds qr payload using booking context', () => {
    const payload = component.qrCodeValue;

    expect(payload).toContain('PNR: PNR-2');
    expect(payload).toContain('Booking ID: BKG-2');
    expect(payload).toContain('Status: CONFIRMED');
  });
});
