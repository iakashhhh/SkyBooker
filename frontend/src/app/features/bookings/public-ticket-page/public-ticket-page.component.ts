import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';

import { BookingResponse } from '../../../core/models/booking.models';
import { FlightResponse } from '../../../core/models/flight.models';
import { PassengerResponse } from '../../../core/models/passenger.models';
import { BookingApiService } from '../../../core/services/booking-api.service';
import { FlightApiService } from '../../../core/services/flight-api.service';
import { PassengerApiService } from '../../../core/services/passenger-api.service';
import { FinalTicketComponent } from '../final-ticket/final-ticket.component';

@Component({
  selector: 'app-public-ticket-page',
  standalone: true,
  imports: [CommonModule, RouterLink, FinalTicketComponent],
  templateUrl: './public-ticket-page.component.html',
  styleUrl: './public-ticket-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PublicTicketPageComponent implements OnInit {
  booking: BookingResponse | null = null;
  passengers: PassengerResponse[] = [];
  flight: FlightResponse | null = null;
  isLoading = false;
  errorMessage = '';

  constructor(
    private readonly route: ActivatedRoute,
    private readonly bookingApiService: BookingApiService,
    private readonly passengerApiService: PassengerApiService,
    private readonly flightApiService: FlightApiService
  ) {}

  ngOnInit(): void {
    const ticketId = String(this.route.snapshot.paramMap.get('id') ?? '').trim();
    if (!ticketId) {
      this.errorMessage = 'Invalid ticket link.';
      return;
    }

    this.isLoading = true;
    this.bookingApiService.getBookingById(ticketId).subscribe({
      next: (booking) => {
        this.booking = booking;
        forkJoin({
          passengers: this.passengerApiService.getPassengersByBooking(booking.bookingId).pipe(catchError(() => of([] as PassengerResponse[]))),
          flight: this.flightApiService.getFlightById(booking.flightId).pipe(catchError(() => of(null)))
        }).subscribe({
          next: ({ passengers, flight }) => {
            this.passengers = passengers;
            this.flight = flight;
            this.isLoading = false;
          },
          error: () => {
            this.errorMessage = 'Unable to load ticket details.';
            this.isLoading = false;
          }
        });
      },
      error: () => {
        this.errorMessage = 'Ticket not found or unavailable.';
        this.isLoading = false;
      }
    });
  }

  get ticketUrl(): string {
    if (!this.booking) {
      return '';
    }
    if (typeof window === 'undefined') {
      return `/ticket/${this.booking.bookingId}`;
    }
    return `${window.location.origin}/ticket/${this.booking.bookingId}`;
  }
}
