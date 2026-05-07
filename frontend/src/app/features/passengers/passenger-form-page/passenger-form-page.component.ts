import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, finalize, forkJoin, of, switchMap, throwError } from 'rxjs';

import { BookingResponse } from '../../../core/models/booking.models';
import { PassengerRequest, PassengerResponse } from '../../../core/models/passenger.models';
import { BookingApiService } from '../../../core/services/booking-api.service';
import { BookingJourneyService } from '../../../core/services/booking-journey.service';
import { PassengerApiService } from '../../../core/services/passenger-api.service';
import { SeatApiService } from '../../../core/services/seat-api.service';
import { MEAL_OPTIONS, MealFilterType, MealOption, MealType } from './meal-options.config';

@Component({
  selector: 'app-passenger-form-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './passenger-form-page.component.html',
  styleUrl: './passenger-form-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PassengerFormPageComponent implements OnInit {
  readonly titleOptions = ['MR', 'MRS', 'MS', 'MISS', 'DR'];
  readonly genderOptions = ['MALE', 'FEMALE', 'OTHER'];
  readonly passengerTypeOptions: Array<{ value: PassengerRequest['passengerType']; label: string }> = [
    { value: 'ADULT', label: 'Adult' },
    { value: 'CHILD', label: 'Child' },
    { value: 'INFANT', label: 'Infant' }
  ];
  readonly mealOptions: MealOption[] = MEAL_OPTIONS;
  readonly mealTabs: Array<{ label: string; value: MealFilterType }> = [
    { label: 'All', value: 'ALL' },
    { label: 'Veg', value: 'VEG' },
    { label: 'Non-Veg', value: 'NON_VEG' }
  ];
  booking?: BookingResponse;
  bookingId = '';
  pnr = '';
  userId = 1;
  amount = 0;
  selectedSeats: Array<{ seatId: number; seatNumber: string }> = [];
  isLoading = false;
  isSubmitting = false;
  submitMessage = '';
  errorMessage = '';
  activeMealTypeByPassenger: MealFilterType[] = [];

  readonly form = this.formBuilder.group({
    passengers: this.formBuilder.array([])
  });

  constructor(
    private readonly formBuilder: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly bookingApiService: BookingApiService,
    private readonly bookingJourneyService: BookingJourneyService,
    private readonly passengerApiService: PassengerApiService,
    private readonly seatApiService: SeatApiService,
    private readonly cdr: ChangeDetectorRef
  ) {
    this.bookingId = String(this.route.snapshot.queryParamMap.get('bookingId') ?? '');
  }

  get passengers(): FormArray {
    return this.form.get('passengers') as FormArray;
  }

  ngOnInit(): void {
    if (!this.bookingId) {
      this.bookingId = this.bookingJourneyService.getActiveBookingContext()?.bookingId ?? '';
    }

    if (!this.bookingId) {
      this.errorMessage = 'Booking ID is required to capture passenger details.';
      return;
    }

    this.isLoading = true;
    this.bookingApiService.getBookingById(this.bookingId)
      .pipe(
        switchMap((booking) => {
          this.booking = booking;
          this.pnr = booking.pnrCode;
          this.userId = booking.userId;
          this.amount = Number(booking.totalFare);
          this.bookingJourneyService.saveActiveBookingContext({
            bookingId: booking.bookingId,
            pnr: booking.pnrCode,
            flightId: booking.flightId,
            seatIds: booking.seatIds,
            userId: booking.userId,
            amount: booking.totalFare
          });

          return forkJoin({
            seatMap: this.seatApiService.getSeatMap(booking.flightId).pipe(catchError(() => of(null))),
            passengers: this.passengerApiService.getPassengersByBooking(booking.bookingId).pipe(catchError(() => of([])))
          });
        }),
        finalize(() => {
          this.isLoading = false;
          this.cdr.markForCheck();
        })
      )
      .subscribe({
        next: ({ seatMap, passengers }) => {
          const selectedSeatIds = this.booking?.seatIds ?? [];
          this.selectedSeats = selectedSeatIds.map((seatId) => ({
            seatId,
            seatNumber: seatMap?.seats.find((seat) => seat.seatId === seatId)?.seatNumber ?? `Seat ${seatId}`
          }));
          this.buildPassengerForms(passengers);
        },
        error: () => {
          this.errorMessage = 'Unable to load booking details for passenger capture.';
        }
      });
  }

  seatLabelAt(index: number): string {
    return this.selectedSeats[index]?.seatNumber ?? `Seat ${index + 1}`;
  }

  setActiveMealType(passengerIndex: number, mealType: MealFilterType): void {
    this.activeMealTypeByPassenger[passengerIndex] = mealType;
  }

  activeMealTypeAt(passengerIndex: number): MealFilterType {
    return this.activeMealTypeByPassenger[passengerIndex] ?? 'ALL';
  }

  mealsForPassenger(passengerIndex: number): MealOption[] {
    const activeType = this.activeMealTypeAt(passengerIndex);
    const noMealOption = this.mealOptions.find((meal) => meal.id === 'NONE');
    const filteredMeals = this.mealOptions.filter((meal) =>
      meal.id !== 'NONE' && (activeType === 'ALL' || meal.type === activeType)
    );
    return noMealOption ? [noMealOption, ...filteredMeals] : filteredMeals;
  }

  selectMeal(passengerIndex: number, meal: MealOption): void {
    const group = this.passengers.at(passengerIndex);
    group.patchValue({
      mealId: meal.id,
      mealPrice: meal.price,
      mealPreference: meal.name
    });
  }

  isMealSelected(passengerIndex: number, mealId: string): boolean {
    const selectedMealId = String(this.passengers.at(passengerIndex)?.get('mealId')?.value ?? '');
    return selectedMealId === mealId;
  }

  selectedMealAt(passengerIndex: number): MealOption | undefined {
    const selectedMealId = String(this.passengers.at(passengerIndex)?.get('mealId')?.value ?? '');
    return this.resolveMealById(selectedMealId);
  }

  private buildPassengerForms(existingPassengers: PassengerResponse[]): void {
    this.passengers.clear();
    this.activeMealTypeByPassenger = [];
    const passengerCount = Math.max(this.selectedSeats.length || this.booking?.seatIds.length || 1, 1);

    for (let index = 0; index < passengerCount; index += 1) {
      const existingPassenger = existingPassengers[index] as
        | {
            passengerId?: number;
            title?: string;
            firstName?: string;
            lastName?: string;
            dateOfBirth?: string;
            gender?: string;
            passengerType?: string;
            passportNumber?: string;
            nationality?: string;
            passportExpiry?: string;
            mealPreference?: string;
            extraBaggageKg?: number;
          }
        | undefined;
      const meal = this.resolveMealFromExisting(existingPassenger?.mealPreference);
      const defaultMeal = meal ?? this.mealOptions.find((option) => option.id === 'NONE') ?? this.mealOptions[0];
      this.activeMealTypeByPassenger[index] = defaultMeal?.type === 'NONE' ? 'ALL' : defaultMeal?.type ?? 'ALL';

      this.passengers.push(
        this.formBuilder.group({
          passengerId: [existingPassenger?.passengerId ?? null],
          title: [String(existingPassenger?.title ?? '').toUpperCase(), Validators.required],
          firstName: [existingPassenger?.firstName ?? '', Validators.required],
          lastName: [existingPassenger?.lastName ?? '', Validators.required],
          dateOfBirth: [existingPassenger?.dateOfBirth ?? '', Validators.required],
          gender: [String(existingPassenger?.gender ?? '').toUpperCase(), Validators.required],
          passengerType: [existingPassenger?.passengerType ?? null, Validators.required],
          passportNumber: [existingPassenger?.passportNumber ?? '', Validators.required],
          nationality: [existingPassenger?.nationality ?? '', Validators.required],
          passportExpiry: [existingPassenger?.passportExpiry ?? '', Validators.required],
          mealId: [defaultMeal?.id ?? '', Validators.required],
          mealPrice: [defaultMeal?.price ?? 0],
          mealPreference: [defaultMeal?.name ?? existingPassenger?.mealPreference ?? 'No Meal'],
          extraBaggageKg: [existingPassenger?.extraBaggageKg ?? 0, [Validators.min(0), Validators.max(50)]]
        })
      );
    }
  }

  submitPassengers(): void {
    if (!this.booking) {
      this.submitMessage = 'Booking details are not available yet.';
      return;
    }
    if (this.form.invalid) {
      this.submitMessage = 'Please fill all passenger fields.';
      return;
    }

    this.isSubmitting = true;
    this.submitMessage = '';
    const booking = this.booking;
    const bookingId = booking.bookingId;

    const requests = this.passengers.controls.map((group) => {
      const value = group.getRawValue();
      return {
        passengerId: Number(value.passengerId ?? 0),
        request: {
          bookingId,
          title: String(value.title ?? ''),
          firstName: String(value.firstName ?? ''),
          lastName: String(value.lastName ?? ''),
          dateOfBirth: String(value.dateOfBirth ?? ''),
          gender: String(value.gender ?? ''),
          passengerType: value.passengerType as PassengerRequest['passengerType'],
          passportNumber: String(value.passportNumber ?? ''),
          nationality: String(value.nationality ?? ''),
          passportExpiry: String(value.passportExpiry ?? ''),
          mealPreference: this.resolveMealById(String(value.mealId ?? ''))?.name ?? String(value.mealPreference ?? ''),
          extraBaggageKg: Number(value.extraBaggageKg ?? 0)
        } satisfies PassengerRequest
      };
    });
    const totalBaggageKg = requests.reduce((sum, item) => sum + Math.max(0, Number(item.request.extraBaggageKg ?? 0)), 0);
    const mealSelections = this.passengers.controls.map((group, index) => {
      const selectedMealId = String(group.get('mealId')?.value ?? 'NONE');
      const meal = this.resolveMealById(selectedMealId);
      return {
        passengerIndex: index,
        mealId: meal?.id ?? 'NONE',
        mealName: meal?.name ?? 'No Meal',
        mealPrice: Number(meal?.price ?? group.get('mealPrice')?.value ?? 0)
      };
    });

    if (!bookingId) {
      this.isSubmitting = false;
      this.submitMessage = 'Booking reference is missing. Please reopen the booking flow.';
      this.cdr.markForCheck();
      return;
    }

    if (this.selectedSeats.length !== requests.length) {
      this.isSubmitting = false;
      this.submitMessage = 'Seat and passenger count mismatch. Please re-open booking summary and try again.';
      this.cdr.markForCheck();
      return;
    }

    const normalizedPassports = requests
      .map((item) => item.request.passportNumber.trim().toUpperCase())
      .filter((passportNumber) => !!passportNumber);
    if (new Set(normalizedPassports).size !== normalizedPassports.length) {
      this.isSubmitting = false;
      this.submitMessage = 'Duplicate passport numbers are not allowed within the same booking.';
      this.cdr.markForCheck();
      return;
    }

    const saveRequests$ = forkJoin(
      requests.map((item, index) =>
        (
          item.passengerId > 0
            ? this.passengerApiService.updatePassenger(item.passengerId, item.request)
            : this.passengerApiService.addPassenger(item.request)
        ).pipe(
          catchError((error) =>
            throwError(() => new Error(`Traveller ${index + 1}: ${this.extractApiError(error)}`))
          )
        )
      )
    );

    saveRequests$
      .pipe(
        switchMap((savedPassengers) => {
          const assignRequests = savedPassengers.map((savedPassenger, index) => {
            const seat = this.selectedSeats[index];
            if (!seat || !seat.seatId) {
              return throwError(() => new Error(`Traveller ${index + 1}: Seat assignment details missing.`));
            }

            return this.passengerApiService.assignSeat(savedPassenger.passengerId, {
              seatId: seat.seatId,
              seatNumber: seat.seatNumber
            }).pipe(
              catchError((error) =>
                throwError(() => new Error(`Traveller ${index + 1} seat assignment: ${this.extractApiError(error)}`))
              )
            );
          });
          return forkJoin(assignRequests);
        }),
        switchMap(() =>
          this.bookingApiService.updateBookingFare(bookingId, {
            luggageKg: totalBaggageKg,
            mealSelections
          })
        )
      )
      .subscribe({
        next: (updatedBooking) => {
          this.isSubmitting = false;
          this.submitMessage = `${requests.length} passengers saved successfully.`;
          this.booking = updatedBooking;
          this.amount = Number(updatedBooking.totalFare);
          this.bookingJourneyService.saveActiveBookingContext({
            bookingId: updatedBooking.bookingId,
            pnr: this.pnr,
            flightId: updatedBooking.flightId,
            seatIds: updatedBooking.seatIds,
            userId: this.userId,
            amount: updatedBooking.totalFare
          });
          this.router.navigate(['/booking-summary'], {
            queryParams: {
              bookingId: updatedBooking.bookingId,
              pnr: this.pnr,
              userId: this.userId,
              amount: updatedBooking.totalFare
            }
          });
          this.cdr.markForCheck();
        },
        error: (error) => {
          this.isSubmitting = false;
          this.submitMessage = error instanceof Error ? error.message : this.extractApiError(error);
          this.cdr.markForCheck();
        }
      });
  }

  backToSeatSelection(): void {
    if (!this.booking) {
      return;
    }

    this.bookingJourneyService.saveActiveBookingContext({
      bookingId: this.booking.bookingId,
      pnr: this.pnr,
      flightId: this.booking.flightId,
      seatIds: this.booking.seatIds,
      userId: this.userId,
      amount: this.amount
    });

    this.router.navigate(['/seats/select'], {
      queryParams: {
        flightId: this.booking.flightId,
        seatIds: this.booking.seatIds.join(','),
        bookingId: this.booking.bookingId,
        returnUrl: '/passenger-details'
      }
    });
  }

  private extractApiError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const payload = error.error;
      if (typeof payload === 'string' && payload.trim()) {
        return payload;
      }
      if (payload && typeof payload === 'object') {
        const message = (payload as { message?: string }).message;
        if (message && message.trim()) {
          return message;
        }
        const details = (payload as { details?: string }).details;
        if (details && details.trim()) {
          return details;
        }
      }
      return error.message || 'Request failed.';
    }

    if (error instanceof Error) {
      return error.message;
    }

    return 'Failed to save passenger details.';
  }

  private resolveMealById(mealId: string): MealOption | undefined {
    const normalizedId = mealId.trim().toLowerCase();
    return this.mealOptions.find((meal) => meal.id.toLowerCase() === normalizedId);
  }

  private resolveMealFromExisting(mealPreference?: string): MealOption | undefined {
    const normalizedPreference = String(mealPreference ?? '').trim().toLowerCase();
    if (!normalizedPreference) {
      return undefined;
    }
    return this.mealOptions.find(
      (meal) => meal.id.toLowerCase() === normalizedPreference || meal.name.toLowerCase() === normalizedPreference
    );
  }
}
