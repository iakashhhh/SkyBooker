import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';

import { BookingResponse } from '../../../core/models/booking.models';
import { PaymentResponse } from '../../../core/models/payment.models';
import { BookingApiService } from '../../../core/services/booking-api.service';
import { PaymentApiService } from '../../../core/services/payment-api.service';

@Component({
  selector: 'app-payment-success-page',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './payment-success-page.component.html',
  styleUrl: './payment-success-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PaymentSuccessPageComponent implements OnInit {
  bookingId = '';
  pnr = '';
  paymentId = '';
  status = 'SUCCESS';
  isLoading = false;
  booking?: BookingResponse;
  payment?: PaymentResponse;
  errorMessage = '';

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly bookingApiService: BookingApiService,
    private readonly paymentApiService: PaymentApiService
  ) {}

  ngOnInit(): void {
    this.bookingId = String(this.route.snapshot.queryParamMap.get('bookingId') ?? '').trim();
    this.pnr = String(this.route.snapshot.queryParamMap.get('pnr') ?? '').trim();
    this.paymentId = String(this.route.snapshot.queryParamMap.get('paymentId') ?? '').trim();
    this.status = String(this.route.snapshot.queryParamMap.get('status') ?? 'SUCCESS').trim() || 'SUCCESS';

    if (!this.bookingId) {
      this.errorMessage = 'Booking reference is missing for payment receipt.';
      return;
    }

    this.isLoading = true;
    this.bookingApiService.getBookingById(this.bookingId).subscribe({
      next: (booking) => {
        this.booking = booking;
        this.pnr = booking.pnrCode;
        if (!this.paymentId) {
          this.paymentId = String(booking.paymentId ?? '').trim();
        }
        this.isLoading = false;
        this.loadPaymentReceiptAsync();
      },
      error: () => {
        this.errorMessage = 'Unable to load payment receipt details.';
        this.isLoading = false;
      }
    });
  }

  continueToConfirmation(): void {
    if (!this.bookingId) {
      return;
    }

    this.router.navigate(['/booking-success'], {
      queryParams: {
        bookingId: this.bookingId,
        pnr: this.pnr
      }
    });
  }

  private loadPaymentReceiptAsync(): void {
    this.paymentApiService.getPaymentByBooking(this.bookingId).pipe(
      catchError(() => of(null))
    ).subscribe((payment) => {
      if (!payment) {
        return;
      }
      this.payment = payment;
      this.paymentId = payment.paymentId;
      this.status = payment.status;
    });
  }
}
