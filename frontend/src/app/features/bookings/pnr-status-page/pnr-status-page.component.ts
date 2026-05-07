import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError, forkJoin, of } from 'rxjs';

import { BookingResponse } from '../../../core/models/booking.models';
import { FlightResponse } from '../../../core/models/flight.models';
import { PassengerResponse } from '../../../core/models/passenger.models';
import { AirlineAirportApiService, AirlineRecord } from '../../../core/services/airline-airport-api.service';
import { BookingApiService } from '../../../core/services/booking-api.service';
import { FlightApiService } from '../../../core/services/flight-api.service';
import { PassengerApiService } from '../../../core/services/passenger-api.service';

@Component({
  selector: 'app-pnr-status-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './pnr-status-page.component.html',
  styleUrl: './pnr-status-page.component.scss'
})
export class PnrStatusPageComponent {
  isLoading = false;
  errorMessage = '';
  booking: BookingResponse | null = null;
  flight: FlightResponse | null = null;
  passengers: PassengerResponse[] = [];
  airlineNameById: Record<number, string> = {};

  readonly pnrForm = this.formBuilder.group({
    pnr: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9]{5,12}$/)]]
  });

  constructor(
    private readonly formBuilder: FormBuilder,
    private readonly bookingApiService: BookingApiService,
    private readonly flightApiService: FlightApiService,
    private readonly passengerApiService: PassengerApiService,
    private readonly airlineAirportApiService: AirlineAirportApiService
  ) {
    this.airlineAirportApiService.getAirlines().pipe(catchError(() => of([] as AirlineRecord[]))).subscribe((airlines) => {
      this.airlineNameById = airlines.reduce((map: Record<number, string>, airline) => {
        if (airline.airlineId) {
          map[airline.airlineId] = airline.name;
        }
        return map;
      }, {});
    });
  }

  checkStatus(): void {
    if (this.pnrForm.invalid) {
      this.pnrForm.markAllAsTouched();
      return;
    }

    const pnr = String(this.pnrForm.controls.pnr.value ?? '').trim().toUpperCase();
    this.pnrForm.controls.pnr.setValue(pnr);
    this.errorMessage = '';
    this.booking = null;
    this.flight = null;
    this.passengers = [];
    this.isLoading = true;

    this.bookingApiService.getBookingByPnr(pnr).subscribe({
      next: (booking) => {
        this.booking = booking;
        forkJoin({
          flight: this.flightApiService.getFlightById(booking.flightId).pipe(catchError(() => of(null))),
          passengers: this.passengerApiService.getPassengersByBooking(booking.bookingId).pipe(catchError(() => of([] as PassengerResponse[])))
        }).subscribe({
          next: ({ flight, passengers }) => {
            this.flight = flight;
            this.passengers = passengers;
            this.isLoading = false;
          },
          error: () => {
            this.isLoading = false;
          }
        });
      },
      error: (error) => {
        this.isLoading = false;
        this.errorMessage = error?.status === 404
          ? 'No booking found for this PNR'
          : 'Unable to fetch booking details right now. Please try again.';
      }
    });
  }

  get showFieldError(): boolean {
    const control = this.pnrForm.controls.pnr;
    return control.invalid && (control.dirty || control.touched);
  }

  get passengerName(): string {
    const first = this.passengers[0];
    if (!first) {
      return 'Passenger details unavailable';
    }
    return `${first.title} ${first.firstName} ${first.lastName}`.replace(/\s+/g, ' ').trim();
  }

  get routeText(): string {
    if (!this.flight) {
      return 'Route unavailable';
    }
    return `${this.flight.originAirportCode} → ${this.flight.destinationAirportCode}`;
  }

  get flightDateTime(): string {
    const dateText = this.flight?.departureTime || this.booking?.bookedAt;
    if (!dateText) {
      return 'Date unavailable';
    }

    const parsed = new Date(dateText);
    if (Number.isNaN(parsed.getTime())) {
      return 'Date unavailable';
    }

    return `${parsed.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })} · ${parsed.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' })}`;
  }

  get seatText(): string {
    const passengerSeats = this.passengers
      .map((passenger) => passenger.seatNumber)
      .filter((seat): seat is string => !!seat);

    if (passengerSeats.length) {
      return passengerSeats.join(', ');
    }

    return (this.booking?.seatIds?.length ?? 0) > 0
      ? this.booking?.seatIds.join(', ') ?? 'Not assigned'
      : 'Not assigned';
  }

  get airlineName(): string {
    if (!this.flight?.airlineId) {
      return 'Airline unavailable';
    }
    return this.airlineNameById[this.flight.airlineId] ?? 'Airline unavailable';
  }

  get statusClass(): string {
    switch (this.booking?.status) {
      case 'CONFIRMED':
      case 'COMPLETED':
        return 'status-positive';
      case 'CANCELLED':
      case 'NO_SHOW':
        return 'status-negative';
      default:
        return 'status-neutral';
    }
  }
}
