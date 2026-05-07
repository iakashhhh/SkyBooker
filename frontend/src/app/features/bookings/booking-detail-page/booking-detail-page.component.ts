import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';

import { BookingResponse } from '../../../core/models/booking.models';
import { PassengerResponse } from '../../../core/models/passenger.models';
import { PaymentResponse } from '../../../core/models/payment.models';
import { BookingApiService } from '../../../core/services/booking-api.service';
import { PassengerApiService } from '../../../core/services/passenger-api.service';
import { PaymentApiService } from '../../../core/services/payment-api.service';

@Component({
  selector: 'app-booking-detail-page',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './booking-detail-page.component.html',
  styleUrl: './booking-detail-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BookingDetailPageComponent implements OnInit {
  booking?: BookingResponse;
  passengers: PassengerResponse[] = [];
  payment: PaymentResponse | null = null;
  isLoading = false;
  errorMessage = '';

  constructor(
    private readonly route: ActivatedRoute,
    private readonly bookingApiService: BookingApiService,
    private readonly passengerApiService: PassengerApiService,
    private readonly paymentApiService: PaymentApiService
  ) {}

  ngOnInit(): void {
    const bookingId = String(this.route.snapshot.paramMap.get('id') ?? '').trim();
    if (!bookingId) {
      this.errorMessage = 'Booking ID is missing.';
      return;
    }

    this.isLoading = true;
    this.bookingApiService.getBookingById(bookingId).subscribe({
      next: (response) => {
        this.booking = response;
        forkJoin({
          passengers: this.passengerApiService.getPassengersByBooking(response.bookingId).pipe(catchError(() => of([]))),
          payment: this.paymentApiService.getPaymentByBooking(response.bookingId).pipe(catchError(() => of(null)))
        }).subscribe({
          next: ({ passengers, payment }) => {
            this.passengers = passengers;
            this.payment = payment;
            this.isLoading = false;
          },
          error: () => {
            this.errorMessage = 'Unable to load payment and passenger details.';
            this.isLoading = false;
          }
        });
      },
      error: () => {
        this.errorMessage = 'Unable to load booking details.';
        this.isLoading = false;
      }
    });
  }

  downloadTicket(): void {
    if (!this.booking) {
      return;
    }

    this.bookingApiService.downloadTicketPdf(this.booking.bookingId).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `ticket-${this.booking?.bookingId}.pdf`;
        anchor.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.errorMessage = 'Unable to download ticket PDF right now.';
      }
    });
  }

  get qrImageUrl(): string {
    return this.booking ? this.bookingApiService.getTicketQrUrl(this.booking.bookingId) : '';
  }
}
