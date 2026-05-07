import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, finalize, map, of, switchMap } from 'rxjs';

import { BookingResponse } from '../../../core/models/booking.models';
import { PassengerResponse } from '../../../core/models/passenger.models';
import { BookingApiService } from '../../../core/services/booking-api.service';
import { PassengerApiService } from '../../../core/services/passenger-api.service';
import { TicketCardComponent } from '../ticket-card/ticket-card.component';

@Component({
  selector: 'app-booking-success-page',
  standalone: true,
  imports: [CommonModule, RouterLink, TicketCardComponent],
  templateUrl: './booking-success-page.component.html',
  styleUrl: './booking-success-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BookingSuccessPageComponent implements OnInit {
  booking?: BookingResponse;
  passengers: PassengerResponse[] = [];
  selectedPassenger: PassengerResponse | null = null;
  isTicketModalOpen = false;
  bookingId = '';
  pnr = '';
  errorMessage = '';
  isLoading = false;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly bookingApiService: BookingApiService,
    private readonly passengerApiService: PassengerApiService,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.bookingId = String(this.route.snapshot.queryParamMap.get('bookingId') ?? '').trim();
    this.pnr = String(this.route.snapshot.queryParamMap.get('pnr') ?? '').trim();

    if (!this.bookingId && !this.pnr) {
      this.errorMessage = 'No booking reference found.';
      return;
    }

    const bookingRequest$ = this.bookingId
      ? this.bookingApiService.getBookingById(this.bookingId)
      : this.bookingApiService.getBookingByPnr(this.pnr);

    this.isLoading = true;
    bookingRequest$
      .pipe(
        switchMap((response) =>
          this.passengerApiService.getPassengersByBooking(response.bookingId).pipe(
            map((passengers) => ({ response, passengers })),
            catchError(() => of({ response, passengers: [] as PassengerResponse[] }))
          )
        ),
        finalize(() => {
          this.isLoading = false;
          this.cdr.markForCheck();
        })
      )
      .subscribe({
        next: ({ response, passengers }) => {
          this.booking = response;
          this.bookingId = response.bookingId;
          this.pnr = response.pnrCode;
          this.passengers = passengers;
        },
        error: () => {
          this.errorMessage = 'Unable to load booking confirmation details.';
        }
      });
  }

  downloadTicket(): void {
    if (!this.bookingId) {
      return;
    }

    this.bookingApiService.downloadTicketPdf(this.bookingId).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `ticket-${this.bookingId}.pdf`;
        anchor.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.errorMessage = 'Unable to download ticket PDF right now.';
      }
    });
  }

  get qrImageUrl(): string {
    return this.bookingId ? this.bookingApiService.getTicketQrUrl(this.bookingId) : '';
  }

  openTicketModal(passenger: PassengerResponse): void {
    this.selectedPassenger = passenger;
    this.isTicketModalOpen = true;
  }

  closeTicketModal(): void {
    this.isTicketModalOpen = false;
    this.selectedPassenger = null;
  }
}
