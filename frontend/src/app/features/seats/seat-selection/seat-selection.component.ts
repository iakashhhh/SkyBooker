import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, Subscription, interval, startWith, switchMap, takeUntil } from 'rxjs';

import { Seat, SeatMapResponse } from '../../../core/models/seat.models';
import { BookingApiService } from '../../../core/services/booking-api.service';
import { AuthApiService } from '../../../core/services/auth-api.service';
import { SeatApiService } from '../../../core/services/seat-api.service';
import { BookingJourneyService } from '../../../core/services/booking-journey.service';
import { FlightApiService } from '../../../core/services/flight-api.service';
import { TimerService } from '../../../core/services/timer.service';
import { TokenStorageService } from '../../../core/services/token-storage.service';
import { BookingSummaryComponent } from '../booking-summary/booking-summary.component';
import { SeatMapComponent } from '../seat-map/seat-map.component';

@Component({
  selector: 'app-seat-selection',
  standalone: true,
  imports: [CommonModule, RouterLink, SeatMapComponent, BookingSummaryComponent],
  templateUrl: './seat-selection.component.html',
  styleUrl: './seat-selection.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SeatSelectionComponent implements OnInit, OnDestroy {
  private static readonly SEAT_STATE_STORAGE_KEY = 'skybooker.seatSelectionState';
  isLoading = false;
  errorMessage = '';
  toastMessage = '';

  flightId = 0;
  flightNumber = '';
  routeLabel = '';
  baseFare = 0;
  totalSeats = 180;

  seatMap?: SeatMapResponse;
  selectedSeatIds: number[] = [];
  countdownSeconds = 0;
  returnUrl = '/flights/results';
  userId = 1;
  contactEmail = 'passenger@skybooker.com';
  contactPhone = '9999999999';

  private readonly destroy$ = new Subject<void>();
  private countdownSub?: Subscription;

  constructor(
    private readonly activatedRoute: ActivatedRoute,
    private readonly router: Router,
    private readonly bookingApiService: BookingApiService,
    private readonly authApiService: AuthApiService,
    private readonly seatApiService: SeatApiService,
    private readonly bookingJourneyService: BookingJourneyService,
    private readonly flightApiService: FlightApiService,
    private readonly timerService: TimerService,
    private readonly tokenStorageService: TokenStorageService,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    const params = this.activatedRoute.snapshot.queryParams;
    this.flightId = Number(params['flightId'] ?? 0);
    this.flightNumber = String(params['flightNumber'] ?? 'SB-XXX');
    const origin = String(params['origin'] ?? '').trim();
    const destination = String(params['destination'] ?? '').trim();
    this.routeLabel = origin && destination ? `${origin} -> ${destination}` : '';
    const routeFare = Number(params['baseFare']);
    this.baseFare = Number.isFinite(routeFare) && routeFare > 0 ? routeFare : 0;
    this.totalSeats = this.resolveTotalSeats(params['totalSeats']);
    this.returnUrl = String(params['returnUrl'] ?? '/flights/results');
    this.selectedSeatIds = this.parseSeatIds(params['seatIds']);

    if (!this.flightId) {
      this.errorMessage = 'Flight ID missing for seat selection.';
      return;
    }
    if (!this.selectedSeatIds.length) {
      this.selectedSeatIds = this.readSeatSelectionFromSession();
    }
    this.persistSeatSelection();
    this.resolveBaseFareFromBackend();

    const savedUserId = this.tokenStorageService.getUserId();
    if (savedUserId) {
      this.userId = savedUserId;
    }

    this.authApiService.getProfile().subscribe({
      next: (profile) => {
        if (profile.userId) {
          this.userId = profile.userId;
        }
        this.contactEmail = profile.email || this.contactEmail;
        this.contactPhone = profile.phone || this.contactPhone;
        this.cdr.markForCheck();
      },
      error: () => {
        // Keep existing fallback contact info already loaded from local state.
        this.cdr.markForCheck();
      }
    });

    interval(8000)
      .pipe(
        startWith(0),
        switchMap(() => this.seatApiService.getSeatMap(this.flightId, this.totalSeats)),
        takeUntil(this.destroy$)
      )
      .subscribe({
        next: (seatMap) => {
          this.errorMessage = '';
          this.seatMap = seatMap;
          this.syncSelectedSeatsFromHoldState(seatMap);
          this.isLoading = false;
          this.cdr.markForCheck();
        },
        error: () => {
          if (!this.seatMap) {
            this.errorMessage = 'Unable to load seat map from backend. Showing fallback layout.';
            this.seatMap = this.buildFallbackSeatMap();
          }
          this.isLoading = false;
          this.cdr.markForCheck();
        }
      });

    this.countdownSub = this.timerService.countdownSeconds$
      .pipe(takeUntil(this.destroy$))
      .subscribe((seconds) => {
        this.countdownSeconds = seconds;
        if (seconds === 0 && this.selectedSeatIds.length) {
          this.releaseAllSeats(true);
        }
        this.cdr.markForCheck();
      });
  }

  private parseSeatIds(rawValue: unknown): number[] {
    const raw = String(rawValue ?? '').trim();
    if (!raw) {
      return [];
    }

    return raw
      .split(',')
      .map((value) => Number(value.trim()))
      .filter((value) => Number.isFinite(value) && value > 0);
  }

  private syncSelectedSeatsFromHoldState(seatMap: SeatMapResponse): void {
    if (!this.selectedSeatIds.length) {
      return;
    }

    const selectedSeatMap = new Map(
      seatMap.seats
        .filter((seat) => this.selectedSeatIds.includes(seat.seatId))
        .map((seat) => [seat.seatId, seat])
    );
    this.selectedSeatIds = this.selectedSeatIds.filter((seatId) => selectedSeatMap.get(seatId)?.status === 'HELD');
    this.persistSeatSelection();

    if (!this.selectedSeatIds.length || this.countdownSeconds > 0) {
      return;
    }

    const latestHoldExpiryTs = this.selectedSeatIds
      .map((seatId) => selectedSeatMap.get(seatId)?.holdExpiresAt)
      .filter((holdExpiresAt): holdExpiresAt is string => !!holdExpiresAt)
      .map((holdExpiresAt) => new Date(holdExpiresAt).getTime())
      .reduce((max, ts) => Math.max(max, ts), 0);

    if (latestHoldExpiryTs > Date.now()) {
      const seconds = Math.max(1, Math.floor((latestHoldExpiryTs - Date.now()) / 1000));
      this.timerService.start(seconds);
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.countdownSub?.unsubscribe();
    this.timerService.reset();
  }

  get selectedSeats(): Seat[] {
    if (!this.seatMap) {
      return [];
    }
    return this.seatMap.seats.filter((seat) => this.selectedSeatIds.includes(seat.seatId));
  }

  onSeatToggle(seat: Seat): void {
    if (!this.seatMap) {
      return;
    }

    const alreadySelected = this.selectedSeatIds.includes(seat.seatId);
    if (alreadySelected) {
      this.releaseSeat(seat);
      return;
    }

    const previousStatus = seat.status;
    seat.status = 'HELD';
    this.selectedSeatIds = [...this.selectedSeatIds, seat.seatId];
    this.cdr.markForCheck();

    this.seatApiService.holdSeats({ flightId: this.flightId, seatIds: [seat.seatId] }).subscribe({
      next: (response) => {
        if (response.holdExpiresAt && this.countdownSeconds === 0) {
          const holdUntil = new Date(response.holdExpiresAt).getTime();
          const now = Date.now();
          const seconds = Math.max(0, Math.floor((holdUntil - now) / 1000));
          this.timerService.start(seconds || 900);
        }
        this.toastMessage = `Seat ${seat.seatNumber} held for 15 minutes.`;
        this.persistSeatSelection();
        this.cdr.markForCheck();
      },
      error: () => {
        seat.status = previousStatus;
        this.selectedSeatIds = this.selectedSeatIds.filter((id) => id !== seat.seatId);
        this.persistSeatSelection();
        this.toastMessage = 'Sorry, this seat was just taken!';
        this.cdr.markForCheck();
      }
    });
  }

  confirmSelection(): void {
    if (!this.selectedSeatIds.length) {
      return;
    }
    if (!(Number.isFinite(this.baseFare) && this.baseFare > 0)) {
      this.toastMessage = 'Unable to start booking because fare details are unavailable. Please retry from search.';
      this.cdr.markForCheck();
      return;
    }

    if (!this.tokenStorageService.getToken()) {
      this.bookingJourneyService.savePendingBookingDraft({
        flightId: this.flightId,
        tripType: 'ONE_WAY',
        baseFare: this.baseFare,
        seatIds: [...this.selectedSeatIds],
        luggageKg: 0,
        mealPreference: '',
        contactEmail: this.contactEmail,
        contactPhone: this.contactPhone
      });

      this.router.navigate(['/auth/login'], {
        queryParams: {
          returnUrl: '/passenger-details',
          context: 'booking',
          role: 'PASSENGER'
        }
      });
      return;
    }

    this.bookingApiService.createBooking({
      userId: this.userId,
      flightId: this.flightId,
      tripType: 'ONE_WAY',
      baseFare: this.baseFare,
      seatIds: [...this.selectedSeatIds],
      luggageKg: 0,
      mealPreference: '',
      contactEmail: this.contactEmail,
      contactPhone: this.contactPhone
    }).subscribe({
      next: (booking) => {
        this.bookingJourneyService.saveActiveBookingContext({
          bookingId: booking.bookingId,
          pnr: booking.pnrCode,
          flightId: booking.flightId,
          seatIds: booking.seatIds,
          userId: booking.userId,
          amount: booking.totalFare
        });
        this.toastMessage = 'Seats reserved. Continue with passenger details and payment.';
        this.clearSeatSelectionFromSession();
        this.router.navigate(['/passenger-details'], {
          queryParams: {
            pnr: booking.pnrCode,
            bookingId: booking.bookingId,
            userId: booking.userId,
            amount: booking.totalFare,
            fromSeatSelection: true
          }
        });
        this.cdr.markForCheck();
      },
      error: () => {
        this.toastMessage = 'Booking creation failed. Please try holding the seats again.';
        this.cdr.markForCheck();
      }
    });
  }

  releaseAllSeats(fromTimer = false): void {
    if (!this.selectedSeatIds.length) {
      return;
    }

    this.seatApiService
      .releaseSeats({ flightId: this.flightId, seatIds: this.selectedSeatIds })
      .subscribe({
        next: () => {
          this.selectedSeatIds = [];
          this.clearSeatSelectionFromSession();
          this.timerService.reset();
          this.loadSeatMap();
          this.toastMessage = fromTimer ? 'Hold expired. Selected seats were released.' : 'Selected seats released.';
          this.cdr.markForCheck();
        },
        error: () => {
          this.toastMessage = 'Unable to release selected seats.';
          this.cdr.markForCheck();
        }
      });
  }

  goBack(): void {
    if (this.returnUrl.startsWith('/flights/results')) {
      const urlTree = this.router.parseUrl(this.returnUrl);
      urlTree.queryParams = {
        ...urlTree.queryParams,
        refreshAt: Date.now()
      };
      this.router.navigateByUrl(urlTree);
      return;
    }

    if (this.returnUrl.startsWith('/')) {
      this.router.navigateByUrl(this.returnUrl);
      return;
    }

    this.router.navigate(['/flights/results']);
  }

  private releaseSeat(seat: Seat): void {
    this.seatApiService.releaseSeats({ flightId: this.flightId, seatIds: [seat.seatId] }).subscribe({
      next: () => {
        this.selectedSeatIds = this.selectedSeatIds.filter((id) => id !== seat.seatId);
        this.persistSeatSelection();
        this.toastMessage = `Seat ${seat.seatNumber} released.`;
        if (!this.selectedSeatIds.length) {
          this.clearSeatSelectionFromSession();
          this.timerService.reset();
        }
        this.loadSeatMap();
        this.cdr.markForCheck();
      },
      error: () => {
        this.toastMessage = 'Unable to release seat.';
        this.cdr.markForCheck();
      }
    });
  }

  private loadSeatMap(): void {
    this.isLoading = true;
    this.errorMessage = '';

    this.seatApiService.getSeatMap(this.flightId, this.totalSeats).subscribe({
      next: (seatMap) => {
        this.seatMap = seatMap;
        this.syncSelectedSeatsFromHoldState(seatMap);
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.errorMessage = 'Unable to load seat map from backend. Showing fallback layout.';
        this.seatMap = this.buildFallbackSeatMap();
        this.isLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  private readSeatSelectionFromSession(): number[] {
    try {
      const raw = sessionStorage.getItem(SeatSelectionComponent.SEAT_STATE_STORAGE_KEY);
      if (!raw) {
        return [];
      }
      const parsed = JSON.parse(raw) as { flightId?: number; seatIds?: number[] };
      if (!parsed || Number(parsed.flightId) !== this.flightId || !Array.isArray(parsed.seatIds)) {
        return [];
      }
      return parsed.seatIds.filter((seatId) => Number.isFinite(seatId) && seatId > 0);
    } catch {
      return [];
    }
  }

  private persistSeatSelection(): void {
    try {
      sessionStorage.setItem(
        SeatSelectionComponent.SEAT_STATE_STORAGE_KEY,
        JSON.stringify({
          flightId: this.flightId,
          seatIds: this.selectedSeatIds
        })
      );
    } catch {
      // Ignore storage failure and keep runtime selection in memory.
    }
  }

  private clearSeatSelectionFromSession(): void {
    try {
      const raw = sessionStorage.getItem(SeatSelectionComponent.SEAT_STATE_STORAGE_KEY);
      if (!raw) {
        return;
      }
      const parsed = JSON.parse(raw) as { flightId?: number };
      if (Number(parsed?.flightId) === this.flightId) {
        sessionStorage.removeItem(SeatSelectionComponent.SEAT_STATE_STORAGE_KEY);
      }
    } catch {
      // Ignore storage failure.
    }
  }

  private buildFallbackSeatMap(): SeatMapResponse {
    const seats: Seat[] = [];
    let idCounter = 1;

    const safeTotalSeats = Math.max(40, this.totalSeats);

    let remaining = safeTotalSeats;
    let row = 1;
    while (remaining > 0) {
      const seatClass = this.resolveSeatClassByRow(row);
      const columns = this.resolveColumnsBySeatClass(seatClass);
      const multiplier = seatClass === 'FIRST' ? 2.5 : (seatClass === 'BUSINESS' ? 1.8 : 1.0);

      for (const column of columns) {
        if (remaining <= 0) {
          break;
        }

        seats.push({
          seatId: idCounter++,
          flightId: this.flightId,
          seatNumber: `${row}${column}`,
          seatClass,
          row,
          column,
          isWindow: this.isWindowSeat(column, seatClass),
          isAisle: this.isAisleSeat(column, seatClass),
          hasExtraLegroom: false,
          status: 'AVAILABLE',
          priceMultiplier: multiplier,
          holdExpiresAt: null
        });
        remaining -= 1;
      }

      row += 1;
    }

    const firstStartRow = 1;
    const businessStartRow = 4;
    const economyStartRow = 9;
    const extraLegroomRows = new Set([firstStartRow, businessStartRow, economyStartRow, economyStartRow + 6]);

    for (const seat of seats) {
      const extraLegroom = extraLegroomRows.has(seat.row);
      seat.hasExtraLegroom = extraLegroom;
      if (extraLegroom) {
        seat.priceMultiplier = Number((seat.priceMultiplier + 0.2).toFixed(2));
      }
    }

    return {
      flightId: this.flightId,
      seats,
      availableSeatsByClass: {
        FIRST: seats.filter((s) => s.seatClass === 'FIRST').length,
        BUSINESS: seats.filter((s) => s.seatClass === 'BUSINESS').length,
        ECONOMY: seats.filter((s) => s.seatClass === 'ECONOMY').length
      }
    };
  }

  private resolveTotalSeats(totalSeatsParam: unknown): number {
    const parsed = Number(totalSeatsParam);
    if (Number.isFinite(parsed) && parsed >= 40) {
      return parsed;
    }

    return 180;
  }

  private resolveBaseFareFromBackend(): void {
    if (!this.flightId || this.baseFare > 0) {
      return;
    }

    this.flightApiService.getFlightById(this.flightId).subscribe({
      next: (flight) => {
        const serverFare = Number(flight.displayedPrice ?? flight.basePrice ?? 0);
        if (Number.isFinite(serverFare) && serverFare > 0) {
          this.baseFare = serverFare;
        }
        this.cdr.markForCheck();
      },
      error: () => {
        this.toastMessage = 'Unable to fetch latest fare details from backend.';
        this.cdr.markForCheck();
      }
    });
  }

  private resolveSeatClassByRow(row: number): 'FIRST' | 'BUSINESS' | 'ECONOMY' {
    if (row <= 3) {
      return 'FIRST';
    }
    if (row <= 8) {
      return 'BUSINESS';
    }
    return 'ECONOMY';
  }

  private resolveColumnsBySeatClass(seatClass: 'FIRST' | 'BUSINESS' | 'ECONOMY'): string[] {
    if (seatClass === 'FIRST') {
      return ['A', 'D', 'G'];
    }
    if (seatClass === 'BUSINESS') {
      return ['A', 'B', 'D', 'E', 'G', 'H'];
    }
    return ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I'];
  }

  private isWindowSeat(column: string, seatClass: 'FIRST' | 'BUSINESS' | 'ECONOMY'): boolean {
    if (seatClass === 'FIRST') {
      return column === 'A' || column === 'G';
    }
    if (seatClass === 'BUSINESS') {
      return column === 'A' || column === 'H';
    }
    return column === 'A' || column === 'I';
  }

  private isAisleSeat(column: string, seatClass: 'FIRST' | 'BUSINESS' | 'ECONOMY'): boolean {
    if (seatClass === 'FIRST') {
      return true;
    }
    if (seatClass === 'BUSINESS') {
      return column === 'B' || column === 'D' || column === 'E' || column === 'G';
    }
    return column === 'C' || column === 'D' || column === 'F' || column === 'G';
  }
}
