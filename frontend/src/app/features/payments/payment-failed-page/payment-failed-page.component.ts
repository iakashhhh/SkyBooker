import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, of, switchMap } from 'rxjs';

import { BookingResponse } from '../../../core/models/booking.models';
import { BookingApiService } from '../../../core/services/booking-api.service';

@Component({
  selector: 'app-payment-failed-page',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './payment-failed-page.component.html',
  styleUrl: './payment-failed-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PaymentFailedPageComponent implements OnInit {
  booking?: BookingResponse;
  bookingId = '';
  pnr = '';
  paymentId = '';
  amount = 0;
  status = 'FAILED';
  reason = 'Payment failed or cancelled';

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly bookingApiService: BookingApiService,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.bookingId = String(this.route.snapshot.queryParamMap.get('bookingId') ?? '').trim();
    this.pnr = String(this.route.snapshot.queryParamMap.get('pnr') ?? '').trim();
    this.paymentId = String(this.route.snapshot.queryParamMap.get('paymentId') ?? '').trim();
    this.status = String(this.route.snapshot.queryParamMap.get('status') ?? 'FAILED').trim() || 'FAILED';
    this.reason = String(this.route.snapshot.queryParamMap.get('reason') ?? 'Payment failed or cancelled').trim()
      || 'Payment failed or cancelled';
    this.amount = Number(this.route.snapshot.queryParamMap.get('amount') ?? 0);

    if (this.bookingId) {
      this.bookingApiService.getBookingById(this.bookingId)
        .pipe(catchError(() => (this.pnr ? this.bookingApiService.getBookingByPnr(this.pnr) : of(null))))
        .subscribe({
          next: (booking) => {
            if (booking) {
              this.booking = booking;
              this.amount = Number(booking.totalFare);
              this.pnr = booking.pnrCode;
              this.cdr.markForCheck();
            }
          }
        });
    }
  }

  retryPayment(): void {
    if (!this.bookingId) {
      this.router.navigate(['/payment']);
      return;
    }

    this.router.navigate(['/payment'], {
      queryParams: {
        bookingId: this.bookingId,
        pnr: this.pnr,
        amount: this.amount
      }
    });
  }

  goToBookings(): void {
    this.router.navigate(['/my-bookings']);
  }

  get displayAmount(): number {
    return Number(this.booking?.totalFare ?? this.amount ?? 0);
  }

  get displayStatus(): string {
    return this.booking?.status ?? this.status;
  }

  get detailReason(): string {
    return this.reason;
  }

  get iconLabel(): string {
    return 'Payment failed';
  }

  get hasBookingReference(): boolean {
    return !!this.bookingId || !!this.pnr;
  }

  get bookingReferenceLabel(): string {
    return this.booking?.bookingId || this.bookingId || 'NA';
  }

  get pnrLabel(): string {
    return this.booking?.pnrCode || this.pnr || 'NA';
  }

  backToBooking(): void {
    this.goToBookings();
  }

  goToBookingsPage(): void {
    this.goToBookings();
  }

  navigateIfNoReference(): void {
    if (!this.hasBookingReference) {
      this.goToBookings();
      return;
    }
  }
}
