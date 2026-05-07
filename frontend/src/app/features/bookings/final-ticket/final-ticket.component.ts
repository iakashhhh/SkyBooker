import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { QRCodeModule } from 'angularx-qrcode';

import { BookingResponse } from '../../../core/models/booking.models';
import { FlightResponse } from '../../../core/models/flight.models';
import { PassengerResponse } from '../../../core/models/passenger.models';

@Component({
  selector: 'app-final-ticket',
  standalone: true,
  imports: [CommonModule, QRCodeModule],
  templateUrl: './final-ticket.component.html',
  styleUrl: './final-ticket.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FinalTicketComponent {
  @Input() booking!: BookingResponse;
  @Input() passenger: PassengerResponse | null = null;
  @Input() passengers: PassengerResponse[] = [];
  @Input() flight: FlightResponse | null = null;
  @Input() airlineName = 'SkyBooker';
  @Input() qrValue = '';
  @Input() mode: 'modal' | 'public' = 'modal';

  get primaryPassenger(): PassengerResponse | null {
    return this.passenger ?? this.passengers[0] ?? null;
  }

  get passengerLabel(): string {
    const person = this.primaryPassenger;
    if (!person) {
      return 'Passenger details pending';
    }
    return `${person.title} ${person.firstName} ${person.lastName}`.trim();
  }

  get routeFrom(): string {
    return this.flight?.originAirportCode?.trim().toUpperCase() || '---';
  }

  get routeTo(): string {
    return this.flight?.destinationAirportCode?.trim().toUpperCase() || '---';
  }

  get flightNumber(): string {
    if (this.flight?.flightNumber?.trim()) {
      return this.flight.flightNumber.trim().toUpperCase();
    }
    return `SKY${String(this.booking.flightId ?? '').padStart(3, '0')}`;
  }

  get seatLabel(): string {
    if (this.primaryPassenger?.seatNumber) {
      return this.primaryPassenger.seatNumber;
    }
    if (this.booking?.seatIds?.length) {
      return this.booking.seatIds.map((seatId) => `S-${seatId}`).join(', ');
    }
    return 'TBD';
  }

  get travelDate(): string {
    const date = this.resolveDate(this.flight?.departureTime) ?? this.resolveDate(this.booking.bookedAt);
    if (!date) {
      return 'Date pending';
    }
    return date.toLocaleDateString([], { day: '2-digit', month: 'short', year: 'numeric' });
  }

  get departureTime(): string {
    const date = this.resolveDate(this.flight?.departureTime) ?? this.resolveDate(this.booking.bookedAt);
    if (!date) {
      return 'Time pending';
    }
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  get boardingTime(): string {
    const departure = this.resolveDate(this.flight?.departureTime);
    if (!departure) {
      return 'TBD';
    }
    departure.setMinutes(departure.getMinutes() - 45);
    return departure.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  get gateLabel(): string {
    if (!this.primaryPassenger?.seatNumber) {
      return 'TBD';
    }
    const seat = this.primaryPassenger.seatNumber.trim();
    const firstChar = seat.charAt(0).toUpperCase();
    return firstChar ? `G${firstChar}` : 'TBD';
  }

  get passengerCountLabel(): string {
    const count = this.passengers.length || (this.primaryPassenger ? 1 : 0);
    return count <= 1 ? '1 passenger' : `${count} passengers`;
  }

  get bookingDateTime(): string {
    const date = this.resolveDate(this.booking.bookedAt);
    if (!date) {
      return 'Booking time unavailable';
    }
    return date.toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
  }

  get qrCodeValue(): string {
    return this.buildFallbackPayload();
  }

  private resolveDate(raw?: string | null): Date | null {
    const value = String(raw ?? '').trim();
    if (!value) {
      return null;
    }

    const normalized = /z$/i.test(value) || /[+-]\d{2}:\d{2}$/.test(value) ? value : `${value}Z`;
    const date = new Date(normalized);
    return Number.isNaN(date.getTime()) ? null : date;
  }

  private buildFallbackPayload(): string {
    return [
      'SkyBooker Boarding Pass',
      `PNR: ${String(this.booking?.pnrCode ?? 'N/A').trim() || 'N/A'}`,
      `Booking ID: ${String(this.booking?.bookingId ?? 'N/A').trim() || 'N/A'}`,
      `Passenger: ${this.passengerLabel}`,
      `Route: ${this.routeFrom} -> ${this.routeTo}`,
      `Date: ${this.travelDate}`,
      `Departure: ${this.departureTime}`,
      `Flight: ${this.flightNumber}`,
      `Seat: ${this.seatLabel}`,
      `Gate: ${this.gateLabel}`,
      `Status: ${String(this.booking?.status ?? 'PENDING').trim().toUpperCase()}`,
      `Total Fare: INR ${Number(this.booking?.totalFare ?? 0).toFixed(2)}`
    ].join('\n');
  }
}
