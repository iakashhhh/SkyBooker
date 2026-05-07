import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { QRCodeModule } from 'angularx-qrcode';

import { BookingResponse } from '../../../core/models/booking.models';
import { FlightResponse } from '../../../core/models/flight.models';
import { PassengerResponse } from '../../../core/models/passenger.models';

@Component({
  selector: 'app-ticket-card',
  standalone: true,
  imports: [CommonModule, QRCodeModule],
  templateUrl: './ticket-card.component.html',
  styleUrl: './ticket-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TicketCardComponent {
  @Input() booking!: BookingResponse;
  @Input() passenger: PassengerResponse | null = null;
  @Input() passengers: PassengerResponse[] = [];
  @Input() flight: FlightResponse | null = null;
  @Input() airlineName = 'SkyBooker Airlines';
  @Input() qrValue = '';
  @Output() ticketClick = new EventEmitter<void>();

  get primaryPassenger(): PassengerResponse | null {
    return this.passenger ?? this.passengers[0] ?? null;
  }

  get passengerName(): string {
    const person = this.primaryPassenger;
    if (!person) {
      return 'Passenger details pending';
    }
    return `${person.title} ${person.firstName} ${person.lastName}`.trim();
  }

  get travelDate(): string {
    const value = this.resolveDate(this.flight?.departureTime) ?? this.resolveDate(this.booking?.bookedAt);
    if (!value) {
      return 'Date pending';
    }
    return value.toLocaleDateString([], { day: 'numeric', month: 'short', year: 'numeric' });
  }

  get departureTime(): string {
    const value = this.resolveDate(this.flight?.departureTime) ?? this.resolveDate(this.booking?.bookedAt);
    if (!value) {
      return 'Time pending';
    }
    return value.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  get departureLabel(): string {
    if (this.flight?.originAirportCode) {
      return this.flight.originAirportCode.trim().toUpperCase();
    }
    return '---';
  }

  get arrivalLabel(): string {
    if (this.flight?.destinationAirportCode) {
      return this.flight.destinationAirportCode.trim().toUpperCase();
    }
    return '---';
  }

  get flightNumber(): string {
    if (this.flight?.flightNumber?.trim()) {
      return this.flight.flightNumber.trim().toUpperCase();
    }
    if (this.booking?.flightId != null) {
      return `SKY${String(this.booking.flightId).padStart(3, '0')}`;
    }
    return 'SKY---';
  }

  get seatLabel(): string {
    if (this.primaryPassenger?.seatNumber) {
      return this.primaryPassenger.seatNumber;
    }

    if (this.booking?.seatIds?.length) {
      return this.booking.seatIds.map((seatId) => `Seat ${seatId}`).join(', ');
    }

    return 'Pending';
  }

  get qrCodeValue(): string {
    return this.buildFallbackPayload();
  }

  get passengerCountLabel(): string {
    const passengerCount = this.passengers.length || (this.primaryPassenger ? 1 : 0);
    if (!passengerCount) {
      return 'No passenger details yet';
    }

    return passengerCount === 1 ? '1 passenger' : `${passengerCount} passengers`;
  }

  openTicket(): void {
    this.ticketClick.emit();
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
      'SkyBooker Ticket',
      `PNR: ${String(this.booking?.pnrCode ?? 'N/A').trim() || 'N/A'}`,
      `Booking ID: ${String(this.booking?.bookingId ?? 'N/A').trim() || 'N/A'}`,
      `Passenger: ${this.passengerName}`,
      `Route: ${this.departureLabel} -> ${this.arrivalLabel}`,
      `Date: ${this.travelDate}`,
      `Time: ${this.departureTime}`,
      `Seat: ${this.seatLabel}`,
      `Status: ${String(this.booking?.status ?? 'PENDING').trim().toUpperCase()}`,
      `Fare: INR ${Number(this.booking?.totalFare ?? 0).toFixed(2)}`
    ].join('\n');
  }

}
