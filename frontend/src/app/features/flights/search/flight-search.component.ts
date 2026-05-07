import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, of } from 'rxjs';

import { BookingApiService } from '../../../core/services/booking-api.service';
import { AirlineAirportApiService, AirlineRecord, AirportRecord } from '../../../core/services/airline-airport-api.service';

/**
 * This page captures one-way and round-trip search input.
 * It forwards all filters as query params to result page.
 */
@Component({
  selector: 'app-flight-search',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: './flight-search.component.html',
  styleUrl: './flight-search.component.css'
})
export class FlightSearchComponent implements OnInit {
  readonly todayDate = this.toIsoDate(new Date());
  readonly seatClassOptions = ['ECONOMY', 'PREMIUM_ECONOMY', 'BUSINESS', 'FIRST'];
  readonly departureWindowOptions = ['MORNING', 'AFTERNOON', 'EVENING', 'NIGHT'];
  readonly sortOptions = [
    { label: 'Price: Low to High', value: 'price_asc' },
    { label: 'Price: High to Low', value: 'price_desc' },
    { label: 'Departure: Earliest', value: 'departure_asc' },
    { label: 'Departure: Latest', value: 'departure_desc' },
    { label: 'Duration: Shortest', value: 'duration_asc' },
    { label: 'Duration: Longest', value: 'duration_desc' }
  ];

  airportOptions: AirportRecord[] = [];
  airlineOptions: AirlineRecord[] = [];
  pnrCode = '';
  pnrError = '';
  isPnrLoading = false;

  readonly form = this.formBuilder.group({
    tripType: ['ONE_WAY', Validators.required],
    origin: ['', [Validators.required, Validators.pattern(/^[A-Z]{3}$/)]],
    destination: ['', [Validators.required, Validators.pattern(/^[A-Z]{3}$/)]],
    journeyDate: ['', Validators.required],
    returnDate: [''],
    minPrice: [''],
    maxPrice: [''],
    airlineId: [''],
    departureWindow: [''],
    maxStops: ['0'],
    seatClass: ['ECONOMY'],
    sortBy: ['price_asc']
  });

  constructor(
    private readonly formBuilder: FormBuilder,
    private readonly router: Router,
    private readonly activatedRoute: ActivatedRoute,
    private readonly airlineAirportApiService: AirlineAirportApiService,
    private readonly bookingApiService: BookingApiService
  ) {}

  ngOnInit(): void {
    this.loadReferenceData();
    const path = this.activatedRoute.snapshot.routeConfig?.path ?? '';
    if (path === 'pnr-status') {
      window.setTimeout(() => {
        document.getElementById('pnr-status-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }, 0);
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const tripType = value.tripType ?? 'ONE_WAY';

    if (tripType === 'ROUND_TRIP' && !value.returnDate) {
      this.form.controls.returnDate.setErrors({ required: true });
      this.form.controls.returnDate.markAsTouched();
      return;
    }

    if (tripType === 'ROUND_TRIP' && value.returnDate && value.journeyDate && value.returnDate <= value.journeyDate) {
      this.form.controls.returnDate.setErrors({ dateOrder: true });
      this.form.controls.returnDate.markAsTouched();
      return;
    }

    if (value.journeyDate && value.journeyDate < this.todayDate) {
      this.form.controls.journeyDate.setErrors({ pastDate: true });
      this.form.controls.journeyDate.markAsTouched();
      return;
    }

    if (String(value.origin ?? '').toUpperCase() === String(value.destination ?? '').toUpperCase()) {
      this.form.controls.destination.setErrors({ sameAsOrigin: true });
      this.form.controls.destination.markAsTouched();
      return;
    }

    this.router.navigate(['/flights/results'], {
      queryParams: {
        tripType,
        origin: String(value.origin ?? '').toUpperCase(),
        destination: String(value.destination ?? '').toUpperCase(),
        journeyDate: value.journeyDate ?? '',
        returnDate: value.returnDate ?? '',
        minPrice: value.minPrice ?? '',
        maxPrice: value.maxPrice ?? '',
        airlineId: value.airlineId ?? '',
        departureWindow: value.departureWindow ?? '',
        maxStops: value.maxStops ?? '',
        seatClass: value.seatClass ?? 'ECONOMY',
        sortBy: value.sortBy ?? 'price_asc'
      }
    });
  }

  findBookingByPnr(): void {
    const pnr = this.pnrCode.trim().toUpperCase();
    if (!pnr) {
      this.pnrError = 'Enter your PNR to check booking status.';
      return;
    }

    this.isPnrLoading = true;
    this.pnrError = '';

    this.bookingApiService.getBookingByPnr(pnr).subscribe({
      next: (booking) => {
        this.isPnrLoading = false;
        this.router.navigate(['/ticket', booking.bookingId]);
      },
      error: () => {
        this.isPnrLoading = false;
        this.pnrError = 'No booking found for this PNR.';
      }
    });
  }

  private loadReferenceData(): void {
    this.airlineAirportApiService.getAirports().pipe(catchError(() => of([] as AirportRecord[]))).subscribe((airports) => {
      this.airportOptions = airports
        .filter((airport) => !!airport.iataCode)
        .sort((a, b) => a.city.localeCompare(b.city));
    });

    this.airlineAirportApiService.getAirlines().pipe(catchError(() => of([] as AirlineRecord[]))).subscribe((airlines) => {
      this.airlineOptions = airlines
        .filter((airline) => (airline.active ?? airline.isActive ?? true) && !!airline.airlineId)
        .sort((a, b) => a.name.localeCompare(b.name));
    });
  }

  private toIsoDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}
