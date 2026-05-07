import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { SeatMapComponent } from './seat-map.component';

describe('SeatMapComponent', () => {
  let fixture: ComponentFixture<SeatMapComponent>;
  let component: SeatMapComponent;

  const seats: Array<{
    seatId: number;
    flightId: number;
    seatNumber: string;
    isWindow: boolean;
    isAisle: boolean;
    hasExtraLegroom: boolean;
    row: number;
    column: string;
    seatClass: 'FIRST' | 'BUSINESS' | 'ECONOMY';
    status: 'AVAILABLE' | 'HELD' | 'CONFIRMED' | 'BLOCKED';
    priceMultiplier: number;
    holdExpiresAt?: string | null;
  }> = [
    { seatId: 1, flightId: 77, row: 1, column: 'A', seatClass: 'FIRST', status: 'AVAILABLE', priceMultiplier: 2.5, seatNumber: '1A', isWindow: true, isAisle: true, hasExtraLegroom: true, holdExpiresAt: null },
    { seatId: 2, flightId: 77, row: 1, column: 'D', seatClass: 'FIRST', status: 'HELD', priceMultiplier: 2.5, seatNumber: '1D', isWindow: false, isAisle: true, hasExtraLegroom: true, holdExpiresAt: null },
    { seatId: 3, flightId: 77, row: 1, column: 'G', seatClass: 'FIRST', status: 'CONFIRMED', priceMultiplier: 2.5, seatNumber: '1G', isWindow: true, isAisle: true, hasExtraLegroom: true, holdExpiresAt: null },
    { seatId: 4, flightId: 77, row: 9, column: 'A', seatClass: 'ECONOMY', status: 'AVAILABLE', priceMultiplier: 1, seatNumber: '9A', isWindow: true, isAisle: false, hasExtraLegroom: true, holdExpiresAt: null },
    { seatId: 5, flightId: 77, row: 9, column: 'B', seatClass: 'ECONOMY', status: 'BLOCKED', priceMultiplier: 1, seatNumber: '9B', isWindow: false, isAisle: false, hasExtraLegroom: true, holdExpiresAt: null },
    { seatId: 6, flightId: 77, row: 9, column: 'C', seatClass: 'ECONOMY', status: 'HELD', priceMultiplier: 1, seatNumber: '9C', isWindow: false, isAisle: true, hasExtraLegroom: true, holdExpiresAt: null },
    { seatId: 7, flightId: 77, row: 9, column: 'D', seatClass: 'ECONOMY', status: 'AVAILABLE', priceMultiplier: 1, seatNumber: '9D', isWindow: false, isAisle: true, hasExtraLegroom: true, holdExpiresAt: null },
    { seatId: 8, flightId: 77, row: 9, column: 'E', seatClass: 'ECONOMY', status: 'AVAILABLE', priceMultiplier: 1, seatNumber: '9E', isWindow: false, isAisle: false, hasExtraLegroom: true, holdExpiresAt: null },
    { seatId: 9, flightId: 77, row: 9, column: 'F', seatClass: 'ECONOMY', status: 'AVAILABLE', priceMultiplier: 1, seatNumber: '9F', isWindow: false, isAisle: true, hasExtraLegroom: true, holdExpiresAt: null },
    { seatId: 10, flightId: 77, row: 9, column: 'G', seatClass: 'ECONOMY', status: 'AVAILABLE', priceMultiplier: 1, seatNumber: '9G', isWindow: false, isAisle: true, hasExtraLegroom: true, holdExpiresAt: null },
    { seatId: 11, flightId: 77, row: 9, column: 'H', seatClass: 'ECONOMY', status: 'AVAILABLE', priceMultiplier: 1, seatNumber: '9H', isWindow: false, isAisle: false, hasExtraLegroom: true, holdExpiresAt: null },
    { seatId: 12, flightId: 77, row: 9, column: 'I', seatClass: 'ECONOMY', status: 'AVAILABLE', priceMultiplier: 1, seatNumber: '9I', isWindow: true, isAisle: false, hasExtraLegroom: true, holdExpiresAt: null }
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SeatMapComponent, NoopAnimationsModule]
    }).compileComponents();

    fixture = TestBed.createComponent(SeatMapComponent);
    component = fixture.componentInstance;
    component.baseFare = 5000;
    component.seats = seats;
    component.selectedSeatIds = [2, 6];
    fixture.detectChanges();
  });

  it('creates class fare bands from available cabin classes', () => {
    const bands = component.classFareBands;

    expect(bands.length).toBe(2);
    expect(bands[0]).toEqual(jasmine.objectContaining({ seatClass: 'FIRST', amount: 12500 }));
    expect(bands[1]).toEqual(jasmine.objectContaining({ seatClass: 'ECONOMY', amount: 5000 }));
  });

  it('groups seats into cabin sections and split blocks per row', () => {
    const sections = component.cabinSections;
    const economy = sections.find((section) => section.seatClass === 'ECONOMY');

    expect(sections.map((section) => section.seatClass)).toEqual(['FIRST', 'ECONOMY']);
    expect(economy?.rows[0].leftSeats.map((seat) => seat.column)).toEqual(['A', 'B', 'C']);
    expect(economy?.rows[0].middleSeats.map((seat) => seat.column)).toEqual(['D', 'E', 'F']);
    expect(economy?.rows[0].rightSeats.map((seat) => seat.column)).toEqual(['G', 'H', 'I']);
  });

  it('marks seat unavailable for confirmed, blocked, or held-by-others', () => {
    const confirmed = seats.find((seat) => seat.seatId === 3)!;
    const blocked = seats.find((seat) => seat.seatId === 5)!;
    const heldByCurrent = seats.find((seat) => seat.seatId === 6)!;

    expect(component.isUnavailable(confirmed)).toBeTrue();
    expect(component.isUnavailable(blocked)).toBeTrue();
    expect(component.isUnavailable(heldByCurrent)).toBeFalse();
  });

  it('emits toggle event only for available seat', () => {
    const emitSpy = spyOn(component.seatToggled, 'emit');
    const available = seats.find((seat) => seat.seatId === 1)!;
    const blocked = seats.find((seat) => seat.seatId === 5)!;

    component.onSeatClick(available);
    component.onSeatClick(blocked);

    expect(emitSpy).toHaveBeenCalledTimes(1);
    expect(emitSpy).toHaveBeenCalledWith(available);
  });

  it('computes selection and seat price helpers', () => {
    const firstHeld = seats.find((seat) => seat.seatId === 2)!;

    expect(component.isSelected(firstHeld)).toBeTrue();
    expect(component.getSeatPrice(firstHeld)).toBe(12500);
  });
});
