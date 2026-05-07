import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Router } from '@angular/router';
import { catchError, finalize, forkJoin, of, switchMap } from 'rxjs';

import { BookingResponse } from '../../../core/models/booking.models';
import { PassengerResponse } from '../../../core/models/passenger.models';
import { PaymentResponse } from '../../../core/models/payment.models';
import { BookingApiService } from '../../../core/services/booking-api.service';
import { BookingJourneyService } from '../../../core/services/booking-journey.service';
import { PassengerApiService } from '../../../core/services/passenger-api.service';
import { PaymentApiService } from '../../../core/services/payment-api.service';
import { SeatApiService } from '../../../core/services/seat-api.service';

@Component({
  selector: 'app-booking-summary-page',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './booking-summary-page.component.html',
  styleUrl: './booking-summary-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BookingSummaryPageComponent implements OnInit {
  isLoading = false;
  errorMessage = '';
  booking?: BookingResponse;
  passengers: PassengerResponse[] = [];
  payment: PaymentResponse | null = null;
  seatLabels: string[] = [];

  constructor(
    private readonly activatedRoute: ActivatedRoute,
    private readonly bookingApiService: BookingApiService,
    private readonly bookingJourneyService: BookingJourneyService,
    private readonly passengerApiService: PassengerApiService,
    private readonly paymentApiService: PaymentApiService,
    private readonly seatApiService: SeatApiService,
    private readonly router: Router,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    const activeBookingContext = this.bookingJourneyService.getActiveBookingContext();
    const bookingId = String(this.activatedRoute.snapshot.queryParamMap.get('bookingId') ?? activeBookingContext?.bookingId ?? '').trim();
    const pnr = String(this.activatedRoute.snapshot.queryParamMap.get('pnr') ?? activeBookingContext?.pnr ?? '').trim();
    if (!pnr && !bookingId) {
      this.errorMessage = 'No booking reference found. Please complete booking first.';
      return;
    }

    this.isLoading = true;
    const bookingRequest$ = bookingId
      ? this.bookingApiService.getBookingById(bookingId)
      : this.bookingApiService.getBookingByPnr(pnr);

    bookingRequest$
      .pipe(
        switchMap((response) => {
          this.booking = response;
          this.bookingJourneyService.saveActiveBookingContext({
            bookingId: response.bookingId,
            pnr: response.pnrCode,
            flightId: response.flightId,
            seatIds: response.seatIds,
            userId: response.userId,
            amount: response.totalFare
          });
          return forkJoin({
            passengers: this.passengerApiService.getPassengersByBooking(response.bookingId).pipe(catchError(() => of([]))),
            payment: this.paymentApiService.getPaymentByBooking(response.bookingId).pipe(catchError(() => of(null))),
            seatMap: this.seatApiService.getSeatMap(response.flightId).pipe(catchError(() => of(null)))
          });
        }),
        finalize(() => {
          this.isLoading = false;
          this.cdr.markForCheck();
        })
      )
      .subscribe({
        next: ({ passengers, payment, seatMap }) => {
          this.passengers = passengers;
          this.payment = payment;
          this.seatLabels = this.booking?.seatIds.map((seatId) =>
            seatMap?.seats.find((seat) => seat.seatId === seatId)?.seatNumber ?? `Seat ${seatId}`
          ) ?? [];
        },
        error: () => {
          this.errorMessage = 'Unable to load booking summary for this booking.';
        }
      });
  }

  continueToPassengers(): void {
    if (!this.booking) {
      return;
    }

    this.router.navigate(['/passenger-details'], {
      queryParams: {
        bookingId: this.booking.bookingId,
        pnr: this.booking.pnrCode,
        userId: this.booking.userId,
        amount: this.booking.totalFare
      }
    });
  }

  continueToPayment(): void {
    if (!this.booking) {
      return;
    }
    if (!this.isPassengerStepComplete) {
      this.continueToPassengers();
      return;
    }

    this.router.navigate(['/payment'], {
      queryParams: {
        bookingId: this.booking.bookingId,
        pnr: this.booking.pnrCode,
        userId: this.booking.userId,
        amount: this.booking.totalFare
      }
    });
  }

  get requiredPassengerCount(): number {
    return this.booking?.seatIds.length ?? 0;
  }

  get isPassengerStepComplete(): boolean {
    return this.passengers.length === this.requiredPassengerCount && this.requiredPassengerCount > 0;
  }

  get isPaymentComplete(): boolean {
    return this.booking?.status === 'CONFIRMED' || this.payment?.status === 'PAID';
  }

  continueJourney(): void {
    if (!this.isPassengerStepComplete) {
      this.continueToPassengers();
      return;
    }

    if (!this.isPaymentComplete) {
      this.continueToPayment();
    }
  }
}
