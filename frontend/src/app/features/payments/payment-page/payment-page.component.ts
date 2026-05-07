import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, finalize, firstValueFrom, forkJoin, map, Observable, of, switchMap } from 'rxjs';

import { BookingResponse } from '../../../core/models/booking.models';
import { PassengerResponse } from '../../../core/models/passenger.models';
import { InitiatePaymentRequest, PaymentMode, PaymentResponse } from '../../../core/models/payment.models';
import { environment } from '../../../core/config/environment';
import { BookingApiService } from '../../../core/services/booking-api.service';
import { BookingJourneyService } from '../../../core/services/booking-journey.service';
import { PassengerApiService } from '../../../core/services/passenger-api.service';
import { PaymentApiService } from '../../../core/services/payment-api.service';

type RazorpaySuccessResponse = {
  razorpay_payment_id: string;
  razorpay_order_id: string;
  razorpay_signature: string;
};

type RazorpayFailureResponse = {
  error?: {
    description?: string;
    reason?: string;
  };
};

type RazorpayCheckoutOptions = {
  key: string;
  amount: number;
  currency: string;
  name: string;
  description: string;
  handler: (response: RazorpaySuccessResponse) => void;
  modal: {
    ondismiss: () => void;
  };
  prefill: {
    contact: string;
    email?: string;
    name?: string;
  };
  theme: {
    color: string;
  };
  order_id?: string;
  method?: {
    card?: boolean;
    upi?: boolean;
    netbanking?: boolean;
    wallet?: boolean;
    emi?: boolean;
    paylater?: boolean;
  };
  config?: {
    display: {
      blocks: Record<string, { name: string; instruments: Array<{ method: string }> }>;
      sequence: string[];
      preferences: {
        show_default_blocks: boolean;
      };
    };
  };
};

type RazorpayInstance = {
  open: () => void;
  on: (event: 'payment.failed', handler: (response: RazorpayFailureResponse) => void) => void;
};

type RazorpayWindow = Window & {
  Razorpay?: new (options: RazorpayCheckoutOptions) => RazorpayInstance;
};

type FlightSummaryResponse = {
  originAirportCode?: string;
  destinationAirportCode?: string;
  airlineId?: number;
};

type AirlineSummaryResponse = {
  name?: string;
};

type FlightMeta = {
  routeLabel: string;
  airlineName: string;
};

type CheckoutPreference = {
  method: NonNullable<RazorpayCheckoutOptions['method']>;
  config: NonNullable<RazorpayCheckoutOptions['config']>;
};

@Component({
  selector: 'app-payment-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './payment-page.component.html',
  styleUrl: './payment-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PaymentPageComponent implements OnInit, OnDestroy {
  readonly paymentModes: Array<{ value: PaymentMode; label: string; note: string }> = [
    { value: 'UPI', label: 'UPI', note: 'Pay instantly using your UPI app.' },
    { value: 'CARD', label: 'Card', note: 'Use credit or debit cards securely.' },
    { value: 'NET_BANKING', label: 'Net Banking', note: 'Continue with your bank account.' },
    { value: 'WALLET', label: 'Wallet', note: 'Pay using your preferred wallet.' }
  ];

  booking?: BookingResponse;
  passengers: PassengerResponse[] = [];
  paymentId = '';
  pnr = '';
  routeLabel = 'Route unavailable';
  airlineName = 'Airline unavailable';

  errorMessage = '';
  isLoading = false;
  isProcessing = false;

  private razorpayKey = '';
  private razorpayScriptPromise?: Promise<boolean>;
  private razorpayKeyPromise?: Promise<string>;
  private paymentFailureHandled = false;
  private checkoutCompletionInitiated = false;
  private dismissFailureTimerId: number | null = null;

  readonly paymentForm = this.formBuilder.group({
    paymentMode: ['UPI' as PaymentMode, Validators.required]
  });

  constructor(
    private readonly formBuilder: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly httpClient: HttpClient,
    private readonly bookingApiService: BookingApiService,
    private readonly bookingJourneyService: BookingJourneyService,
    private readonly passengerApiService: PassengerApiService,
    private readonly paymentApiService: PaymentApiService,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    void this.ensureRazorpayScript();
    this.loadCheckoutDetails();
  }

  ngOnDestroy(): void {
    this.clearDismissFailureTimer();
  }

  retryLoad(): void {
    this.loadCheckoutDetails();
  }

  initiate(): void {
    this.errorMessage = '';

    if (!this.booking) {
      this.errorMessage = 'Unable to load payment details';
      return;
    }

    if (!this.hasAllPassengerDetails) {
      this.errorMessage = 'Please complete passenger details before payment.';
      return;
    }

    if (this.paymentForm.invalid) {
      this.errorMessage = 'Please select a payment method.';
      return;
    }

    this.isProcessing = true;

    const request: InitiatePaymentRequest = {
      bookingId: this.booking.bookingId,
      userId: Number(this.booking.userId),
      amount: Number(this.booking.totalFare),
      currency: 'INR',
      paymentMode: (this.paymentForm.controls.paymentMode.value ?? 'UPI') as PaymentMode
    };

    this.paymentApiService.initiatePayment(request).subscribe({
      next: (response) => {
        console.log('[PaymentPage] initiate payment response:', response);
        this.paymentId = response.paymentId;
        this.cdr.markForCheck();
        void this.openRazorpayCheckout(response);
      },
      error: () => {
        this.isProcessing = false;
        this.errorMessage = 'Unable to start payment. Please try again.';
        this.cdr.markForCheck();
      }
    });
  }

  get requiredPassengerCount(): number {
    return this.booking?.seatIds.length ?? 0;
  }

  get hasAllPassengerDetails(): boolean {
    return this.requiredPassengerCount > 0 && this.passengers.length === this.requiredPassengerCount;
  }

  get canInitiatePayment(): boolean {
    return !!this.booking && this.hasAllPassengerDetails && !this.isLoading && !this.isProcessing;
  }

  private loadCheckoutDetails(): void {
    const bookingId = this.resolveBookingId();
    console.log('[PaymentPage] bookingId:', bookingId);

    this.errorMessage = '';
    this.isLoading = true;
    this.booking = undefined;
    this.passengers = [];

    if (!bookingId) {
      this.errorMessage = 'Unable to load payment details';
      this.isLoading = false;
      this.cdr.markForCheck();
      return;
    }

    this.bookingApiService.getBookingById(bookingId)
      .pipe(
        switchMap((booking) => {
          this.booking = booking;
          this.pnr = booking.pnrCode;
          console.log('[PaymentPage] booking response:', booking);

          this.bookingJourneyService.saveActiveBookingContext({
            bookingId: booking.bookingId,
            pnr: booking.pnrCode,
            flightId: booking.flightId,
            seatIds: booking.seatIds,
            userId: booking.userId,
            amount: booking.totalFare
          });

          this.loadFlightMetaAsync(booking.flightId);

          return forkJoin({
            passengers: this.passengerApiService.getPassengersByBooking(booking.bookingId).pipe(
              catchError(() => of([]))
            ),
            payment: this.paymentApiService.getPaymentByBooking(booking.bookingId).pipe(
              catchError(() => of(null))
            )
          });
        }),
        finalize(() => {
          this.isLoading = false;
          this.cdr.markForCheck();
        })
      )
      .subscribe({
        next: ({ passengers, payment }) => {
          this.passengers = passengers;
          this.paymentId = payment?.paymentId ?? '';
          this.cdr.markForCheck();
        },
        error: () => {
          this.errorMessage = 'Unable to load payment details';
          this.cdr.markForCheck();
        }
      });
  }

  private loadFlightMetaAsync(flightId: number): void {
    this.fetchFlightMeta(flightId)
      .pipe(
        catchError(() => of(null))
      )
      .subscribe((flightMeta) => {
        if (!flightMeta) {
          this.cdr.markForCheck();
          return;
        }
        this.routeLabel = flightMeta.routeLabel;
        this.airlineName = flightMeta.airlineName;
        this.cdr.markForCheck();
      });
  }

  private resolveBookingId(): string {
    const activeBookingContext = this.bookingJourneyService.getActiveBookingContext();
    return String(
      this.route.snapshot.paramMap.get('bookingId')
      ?? this.route.snapshot.queryParamMap.get('bookingId')
      ?? activeBookingContext?.bookingId
      ?? ''
    ).trim();
  }

  private fetchFlightMeta(flightId: number): Observable<FlightMeta | null> {
    if (!Number.isFinite(flightId) || flightId <= 0) {
      return of(null);
    }

    return this.httpClient.get<FlightSummaryResponse>(`${environment.apiBaseUrl}/flights/${flightId}`).pipe(
      switchMap((flight) => {
        const origin = String(flight.originAirportCode ?? '').trim().toUpperCase();
        const destination = String(flight.destinationAirportCode ?? '').trim().toUpperCase();
        const routeLabel = origin && destination ? `${origin} -> ${destination}` : 'Route unavailable';

        const airlineId = Number(flight.airlineId ?? 0);
        if (!Number.isFinite(airlineId) || airlineId <= 0) {
          return of({ routeLabel, airlineName: 'Airline unavailable' });
        }

        return this.httpClient.get<AirlineSummaryResponse>(`${environment.apiBaseUrl}/airlines/${airlineId}`).pipe(
          map((airline) => ({
            routeLabel,
            airlineName: String(airline.name ?? '').trim() || 'Airline unavailable'
          })),
          catchError(() => of({ routeLabel, airlineName: 'Airline unavailable' }))
        );
      })
    );
  }

  private async openRazorpayCheckout(paymentResponse: PaymentResponse): Promise<void> {
    if (!this.booking) {
      this.isProcessing = false;
      this.errorMessage = 'Unable to load payment details';
      this.cdr.markForCheck();
      return;
    }

    try {
      await this.ensureRazorpayKey();
    } catch {
      this.isProcessing = false;
      this.errorMessage = 'Unable to start payment. Please try again.';
      this.cdr.markForCheck();
      return;
    }

    const scriptLoaded = await this.ensureRazorpayScript();
    const win = window as RazorpayWindow;

    if (!scriptLoaded || !win.Razorpay) {
      this.isProcessing = false;
      this.errorMessage = 'Unable to start payment. Please try again.';
      this.cdr.markForCheck();
      return;
    }

    this.paymentFailureHandled = false;
    this.checkoutCompletionInitiated = false;
    this.clearDismissFailureTimer();
    const selectedMode = (this.paymentForm.controls.paymentMode.value ?? 'UPI') as PaymentMode;
    const checkoutPreference = this.buildCheckoutPreference(selectedMode);

    const options: RazorpayCheckoutOptions = {
      key: this.razorpayKey,
      amount: Math.round(Number(this.booking.totalFare) * 100),
      currency: 'INR',
      name: 'SkyBooker',
      description: 'Flight Booking',
      handler: (response: RazorpaySuccessResponse) => {
        this.checkoutCompletionInitiated = true;
        this.clearDismissFailureTimer();
        void this.verifyRazorpayPayment(response);
      },
      modal: {
        ondismiss: () => {
          this.scheduleDismissFailure();
        }
      },
      prefill: {
        contact: String(this.booking?.contactPhone ?? '').trim(),
        email: String(this.booking?.contactEmail ?? '').trim() || undefined
      },
      theme: {
        color: '#3399cc'
      },
      method: checkoutPreference.method,
      config: checkoutPreference.config
    };

    if (paymentResponse.gatewayOrderId) {
      options.order_id = paymentResponse.gatewayOrderId;
    }

    const razorpay = new win.Razorpay(options);
    razorpay.on('payment.failed', (response: RazorpayFailureResponse) => {
      if (this.checkoutCompletionInitiated) {
        return;
      }
      this.clearDismissFailureTimer();
      const reason = response.error?.description || response.error?.reason || 'Payment failed at checkout.';
      void this.markPaymentFailed(reason);
    });

    razorpay.open();
    this.isProcessing = false;
    this.cdr.markForCheck();
  }

  private async verifyRazorpayPayment(response: RazorpaySuccessResponse): Promise<void> {
    if (!this.booking) {
      this.errorMessage = 'Unable to load payment details';
      this.cdr.markForCheck();
      return;
    }

    this.paymentApiService.verifyPayment({
      bookingId: this.booking.bookingId,
      paymentId: this.paymentId || undefined,
      razorpayOrderId: response.razorpay_order_id,
      razorpayPaymentId: response.razorpay_payment_id,
      razorpaySignature: response.razorpay_signature,
      gatewayResponse: 'Approved by Razorpay'
    }).subscribe({
      next: (verifyResponse) => {
        if (verifyResponse.status !== 'PAID') {
          this.paymentFailureHandled = true;
          this.isProcessing = false;
          const reason = String(verifyResponse.gatewayResponse ?? '').trim() || 'Payment verification failed.';
          this.navigateToFailurePage(verifyResponse.status, reason);
          this.cdr.markForCheck();
          return;
        }

        this.paymentFailureHandled = true;
        this.router.navigate(['/payment-success'], {
          queryParams: {
            bookingId: this.booking?.bookingId,
            pnr: this.pnr,
            paymentId: verifyResponse.paymentId,
            status: verifyResponse.status
          }
        });
      },
      error: () => {
        this.clearDismissFailureTimer();
        this.checkoutCompletionInitiated = false;
        void this.markPaymentFailed('Payment verification failed.');
      }
    });
  }

  private async markPaymentFailed(reason: string): Promise<void> {
    if (this.paymentFailureHandled) {
      return;
    }

    this.clearDismissFailureTimer();
    this.paymentFailureHandled = true;
    this.isProcessing = false;

    if (!this.paymentId) {
      this.navigateToFailurePage('FAILED', reason);
      return;
    }

    try {
      const response = await firstValueFrom(this.paymentApiService.processPayment({
        paymentId: this.paymentId,
        success: false,
        gatewayResponse: reason
      }));
      this.navigateToFailurePage(response.status, reason);
    } catch {
      this.navigateToFailurePage('FAILED', reason);
    }
  }

  private scheduleDismissFailure(): void {
    if (this.checkoutCompletionInitiated || this.paymentFailureHandled) {
      return;
    }

    this.clearDismissFailureTimer();
    // Razorpay can close the modal slightly before invoking success handler in redirect-based flows.
    this.dismissFailureTimerId = window.setTimeout(() => {
      if (this.checkoutCompletionInitiated || this.paymentFailureHandled) {
        return;
      }
      void this.markPaymentFailed('Payment cancelled by user.');
    }, 5500);
  }

  private clearDismissFailureTimer(): void {
    if (this.dismissFailureTimerId === null) {
      return;
    }
    window.clearTimeout(this.dismissFailureTimerId);
    this.dismissFailureTimerId = null;
  }

  private ensureRazorpayKey(): Promise<string> {
    if (this.razorpayKey) {
      return Promise.resolve(this.razorpayKey);
    }

    if (this.razorpayKeyPromise) {
      return this.razorpayKeyPromise;
    }

    this.razorpayKeyPromise = firstValueFrom(this.paymentApiService.getPaymentKey())
      .then((response) => {
        const key = String(response.key ?? '').trim();
        if (!key) {
          throw new Error('Missing Razorpay key.');
        }

        this.razorpayKey = key;
        console.log('[PaymentPage] razorpay key:', key);
        return key;
      })
      .catch((error) => {
        this.razorpayKeyPromise = undefined;
        throw error;
      });

    return this.razorpayKeyPromise;
  }

  private ensureRazorpayScript(): Promise<boolean> {
    const win = window as RazorpayWindow;
    if (win.Razorpay) {
      return Promise.resolve(true);
    }

    if (this.razorpayScriptPromise) {
      return this.razorpayScriptPromise;
    }

    this.razorpayScriptPromise = new Promise<boolean>((resolve) => {
      const existing = document.querySelector<HTMLScriptElement>('script[data-razorpay-checkout]');
      if (existing) {
        existing.addEventListener('load', () => resolve(true), { once: true });
        existing.addEventListener('error', () => resolve(false), { once: true });
        return;
      }

      const script = document.createElement('script');
      script.src = 'https://checkout.razorpay.com/v1/checkout.js';
      script.async = true;
      script.dataset['razorpayCheckout'] = 'true';
      script.onload = () => resolve(true);
      script.onerror = () => resolve(false);
      document.body.appendChild(script);
    });

    return this.razorpayScriptPromise;
  }

  private buildCheckoutPreference(mode: PaymentMode): CheckoutPreference {
    const method = {
      card: false,
      upi: false,
      netbanking: false,
      wallet: false,
      emi: false,
      paylater: false
    };

    let instrumentMethod = 'upi';
    if (mode === 'CARD') {
      method.card = true;
      instrumentMethod = 'card';
    } else if (mode === 'NET_BANKING') {
      method.netbanking = true;
      instrumentMethod = 'netbanking';
    } else if (mode === 'WALLET') {
      method.wallet = true;
      instrumentMethod = 'wallet';
    } else {
      method.upi = true;
      instrumentMethod = 'upi';
    }

    return {
      method,
      config: {
        display: {
          blocks: {
            preferred: {
              name: 'Preferred payment method',
              instruments: [{ method: instrumentMethod }]
            }
          },
          sequence: ['block.preferred'],
          preferences: {
            show_default_blocks: false
          }
        }
      }
    };
  }

  private navigateToFailurePage(status: string, reason: string): void {
    this.router.navigate(['/payment-failed'], {
      queryParams: {
        bookingId: this.booking?.bookingId,
        pnr: this.pnr,
        paymentId: this.paymentId,
        status,
        reason,
        amount: this.booking?.totalFare
      }
    });
  }
}
