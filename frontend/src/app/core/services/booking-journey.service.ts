import { Injectable } from '@angular/core';
import { Observable, catchError, of, switchMap, tap } from 'rxjs';

import { BookingResponse, CreateBookingRequest, TripType } from '../models/booking.models';
import { AuthApiService } from './auth-api.service';
import { BookingApiService } from './booking-api.service';
import { TokenStorageService } from './token-storage.service';

interface PendingBookingDraft {
  flightId: number;
  tripType: TripType;
  baseFare: number;
  seatIds: number[];
  luggageKg: number;
  mealPreference?: string;
  contactEmail?: string;
  contactPhone?: string;
}

export interface ActiveBookingContext {
  bookingId: string;
  pnr?: string;
  flightId?: number;
  seatIds?: number[];
  userId?: number;
  amount?: number;
}

@Injectable({
  providedIn: 'root'
})
export class BookingJourneyService {
  private readonly pendingBookingKey = 'skybooker_pending_booking';
  private readonly activeBookingContextKey = 'skybooker_active_booking_context';

  constructor(
    private readonly authApiService: AuthApiService,
    private readonly bookingApiService: BookingApiService,
    private readonly tokenStorageService: TokenStorageService
  ) {}

  savePendingBookingDraft(draft: PendingBookingDraft): void {
    sessionStorage.setItem(this.pendingBookingKey, JSON.stringify(draft));
  }

  hasPendingBookingDraft(): boolean {
    return !!this.getPendingBookingDraft();
  }

  clearPendingBookingDraft(): void {
    sessionStorage.removeItem(this.pendingBookingKey);
  }

  saveActiveBookingContext(context: ActiveBookingContext): void {
    sessionStorage.setItem(this.activeBookingContextKey, JSON.stringify(context));
  }

  getActiveBookingContext(): ActiveBookingContext | null {
    const raw = sessionStorage.getItem(this.activeBookingContextKey);
    if (!raw) {
      return null;
    }

    try {
      return JSON.parse(raw) as ActiveBookingContext;
    } catch {
      sessionStorage.removeItem(this.activeBookingContextKey);
      return null;
    }
  }

  resumePendingBooking(
    fallbackContact?: Partial<Pick<CreateBookingRequest, 'contactEmail' | 'contactPhone'>>
  ): Observable<BookingResponse | null> {
    const draft = this.getPendingBookingDraft();
    if (!draft) {
      return of(null);
    }

    return this.authApiService.getProfile().pipe(
      catchError(() => of(null)),
      switchMap((profile) => {
        const userId = profile?.userId ?? this.tokenStorageService.getUserId();
        if (!userId) {
          return of(null);
        }

        const request: CreateBookingRequest = {
          userId,
          flightId: draft.flightId,
          tripType: draft.tripType,
          baseFare: draft.baseFare,
          seatIds: draft.seatIds,
          luggageKg: draft.luggageKg,
          mealPreference: draft.mealPreference ?? '',
          contactEmail: fallbackContact?.contactEmail ?? draft.contactEmail ?? profile?.email ?? '',
          contactPhone: fallbackContact?.contactPhone ?? draft.contactPhone ?? profile?.phone ?? ''
        };

        return this.bookingApiService.createBooking(request).pipe(
          tap(() => this.clearPendingBookingDraft())
        );
      })
    );
  }

  private getPendingBookingDraft(): PendingBookingDraft | null {
    const raw = sessionStorage.getItem(this.pendingBookingKey);
    if (!raw) {
      return null;
    }

    try {
      return JSON.parse(raw) as PendingBookingDraft;
    } catch {
      sessionStorage.removeItem(this.pendingBookingKey);
      return null;
    }
  }
}
