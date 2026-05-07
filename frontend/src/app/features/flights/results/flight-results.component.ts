import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { firstValueFrom } from 'rxjs';

import {
  DepartureWindow,
  FlightResponse,
  OneWaySearchParams,
  RoundTripSearchParams,
  RoundTripSearchResponse,
  SeatClass
} from '../../../core/models/flight.models';
import { AirlineAirportApiService, AirlineRecord, AirportRecord } from '../../../core/services/airline-airport-api.service';
import { FlightApiService } from '../../../core/services/flight-api.service';

interface FlexibleDateOption {
  date: string;
  label: string;
  count: number;
  minPrice?: number;
}

interface FlightSegment {
  fromCode: string;
  toCode: string;
  fromName: string;
  toName: string;
  departureTime: string;
  arrivalTime: string;
}

interface AirportOption {
  code: string;
  city: string;
  airport: string;
}

/**
 * This page renders one-way or round-trip search results.
 * It reloads backend data when query params (filters) change.
 */
@Component({
  selector: 'app-flight-results',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './flight-results.component.html',
  styleUrl: './flight-results.component.css'
})
export class FlightResultsComponent implements OnInit {
  readonly todayDate = this.toIsoDate(new Date());
  readonly sortOptions = [
    { label: 'Price: Low to High', value: 'price_asc' },
    { label: 'Price: High to Low', value: 'price_desc' },
    { label: 'Departure: Earliest', value: 'departure_asc' },
    { label: 'Departure: Latest', value: 'departure_desc' },
    { label: 'Duration: Shortest', value: 'duration_asc' },
    { label: 'Duration: Longest', value: 'duration_desc' }
  ];
  readonly departureWindowOptions: DepartureWindow[] = ['MORNING', 'AFTERNOON', 'EVENING', 'NIGHT'];
  readonly seatClassOptions: SeatClass[] = ['ECONOMY', 'PREMIUM_ECONOMY', 'BUSINESS', 'FIRST'];
  isLoading = false;
  isReferenceLoading = false;
  errorMessage = '';
  flexibleDateMode = false;
  flexibleDateOptions: FlexibleDateOption[] = [];
  selectedFlexibleDate = '';
  isFilterPanelOpen = false;
  filterOrigin = '';
  filterDestination = '';
  filterJourneyDate = '';
  filterReturnDate = '';
  filterTripType: 'ONE_WAY' | 'ROUND_TRIP' = 'ONE_WAY';
  filterSeatClass = 'ECONOMY';
  filterSortBy = 'price_asc';
  filterDepartureWindow = '';
  filterMaxStops = '0';
  filterMinPrice = 0;
  filterMaxPrice = 25000;
  readonly pageSizeOptions = [10, 25, 50];
  selectedPageSize = 10;

  tripType: 'ONE_WAY' | 'ROUND_TRIP' = 'ONE_WAY';
  outboundFlights: FlightResponse[] = [];
  returnFlights: FlightResponse[] = [];
  outboundCurrentPage = 1;
  returnCurrentPage = 1;
  isDetailsOpen = false;
  selectedFlight?: FlightResponse;
  selectedFlightDirection: 'OUTBOUND' | 'RETURN' = 'OUTBOUND';
  private flexibleOneWayResults: Record<string, FlightResponse[]> = {};
  private flexibleRoundTripResults: Record<string, RoundTripSearchResponse> = {};
  private flexibleRoundTripReturnDates: Record<string, string> = {};
  airportOptions: AirportOption[] = [];
  airlineNameById: Record<number, string> = {};
  readonly filterCitySuggestions: Record<'origin' | 'destination', AirportOption[]> = {
    origin: [],
    destination: []
  };
  activeFilterCityField: 'origin' | 'destination' | null = null;
  private filterBlurTimeoutId?: ReturnType<typeof setTimeout>;

  constructor(
    private readonly activatedRoute: ActivatedRoute,
    private readonly router: Router,
    private readonly flightApiService: FlightApiService,
    private readonly airlineAirportApiService: AirlineAirportApiService
  ) {}

  ngOnInit(): void {
    this.loadReferenceData();
    this.activatedRoute.queryParamMap.subscribe((params) => {
      this.tripType = params.get('tripType') === 'ROUND_TRIP' ? 'ROUND_TRIP' : 'ONE_WAY';
      this.syncFilterState(this.activatedRoute.snapshot.queryParams);
      this.loadResults();
    });
  }

  reloadWithSort(sortBy: string): void {
    const current = this.activatedRoute.snapshot.queryParams;
    this.router.navigate([], {
      relativeTo: this.activatedRoute,
      queryParams: { ...current, sortBy },
      queryParamsHandling: 'merge'
    });
  }

  toggleFilterPanel(): void {
    this.isFilterPanelOpen = !this.isFilterPanelOpen;
  }

  applyFilters(): void {
    if (this.filterJourneyDate && this.filterJourneyDate < this.todayDate) {
      this.errorMessage = 'Departure date cannot be in the past.';
      return;
    }

    if (this.filterTripType === 'ROUND_TRIP' && this.filterReturnDate && this.filterReturnDate <= this.filterJourneyDate) {
      this.errorMessage = 'Return date must be after the departure date.';
      return;
    }

    const minPrice = Math.min(this.filterMinPrice, this.filterMaxPrice);
    const maxPrice = Math.max(this.filterMinPrice, this.filterMaxPrice);

    const originCode = this.resolveAirportCode(this.filterOrigin);
    const destinationCode = this.resolveAirportCode(this.filterDestination);

    if (!originCode || !destinationCode) {
      this.errorMessage = 'Please enter supported city names or airport codes for From and To.';
      return;
    }

    if (originCode === destinationCode) {
      this.errorMessage = 'Origin and destination cannot be the same.';
      return;
    }

    this.errorMessage = '';

    this.router.navigate([], {
      relativeTo: this.activatedRoute,
      queryParams: {
        tripType: this.filterTripType,
        origin: originCode,
        destination: destinationCode,
        journeyDate: this.filterJourneyDate,
        returnDate: this.filterTripType === 'ROUND_TRIP' ? this.filterReturnDate : '',
        seatClass: this.filterSeatClass,
        sortBy: this.filterSortBy,
        departureWindow: this.filterDepartureWindow,
        maxStops: this.filterMaxStops,
        minPrice,
        maxPrice
      }
    });
  }

  onFilterCityFocus(field: 'origin' | 'destination'): void {
    this.activeFilterCityField = field;
    this.filterCitySuggestions[field] = this.filterAirportOptions(
      field === 'origin' ? this.filterOrigin : this.filterDestination
    );
  }

  onFilterCityBlur(field: 'origin' | 'destination'): void {
    window.clearTimeout(this.filterBlurTimeoutId);
    this.filterBlurTimeoutId = window.setTimeout(() => {
      if (this.activeFilterCityField === field) {
        this.activeFilterCityField = null;
      }
    }, 120);
  }

  onFilterCityInput(field: 'origin' | 'destination', rawValue: string): void {
    const normalized = rawValue.trimStart();
    if (field === 'origin') {
      this.filterOrigin = normalized;
    } else {
      this.filterDestination = normalized;
    }
    this.activeFilterCityField = field;
    this.filterCitySuggestions[field] = this.filterAirportOptions(normalized);
  }

  selectFilterCity(field: 'origin' | 'destination', option: AirportOption): void {
    const value = `${option.city} (${option.code})`;
    if (field === 'origin') {
      this.filterOrigin = value;
    } else {
      this.filterDestination = value;
    }
    this.filterCitySuggestions[field] = [];
    this.activeFilterCityField = null;
  }

  showFilterCityDropdown(field: 'origin' | 'destination'): boolean {
    return this.activeFilterCityField === field && this.filterCitySuggestions[field].length > 0;
  }

  resetFilters(): void {
    this.syncFilterState({
      ...this.activatedRoute.snapshot.queryParams,
      minPrice: '',
      maxPrice: '',
      departureWindow: '',
      maxStops: '0',
      sortBy: 'price_asc'
    });
    this.applyFilters();
  }

  get routeHeadline(): string {
    if (!this.filterOrigin || !this.filterDestination) {
      return 'Flight Results';
    }

    return `${this.filterOrigin} to ${this.filterDestination}`;
  }

  get travelHeadline(): string {
    const departure = this.filterJourneyDate ? this.formatDateLong(this.filterJourneyDate) : 'Flexible date';
    if (this.tripType === 'ROUND_TRIP' && this.filterReturnDate) {
      return `${departure} · Return ${this.formatDateLong(this.filterReturnDate)}`;
    }

    return `${departure} · ${this.tripType === 'ROUND_TRIP' ? 'Round Trip' : 'One Way'}`;
  }

  get activeSearchTags(): string[] {
    const tags = [
      this.filterSeatClass.replaceAll('_', ' '),
      this.filterDepartureWindow || 'Any departure window',
      `${this.filterMaxStops || '0'} stop max`
    ];

    if (this.filterMinPrice || this.filterMaxPrice) {
      tags.push(`INR ${this.filterMinPrice} - ${this.filterMaxPrice}`);
    }

    return tags;
  }

  get priceCap(): number {
    const livePrices = [...this.outboundFlights, ...this.returnFlights].map((flight) => Number(flight.displayedPrice));
    return Math.max(25000, this.filterMaxPrice, ...(livePrices.length ? livePrices : [0]));
  }

  get paginatedOutboundFlights(): FlightResponse[] {
    return this.paginate(this.outboundFlights, this.outboundCurrentPage);
  }

  get paginatedReturnFlights(): FlightResponse[] {
    return this.paginate(this.returnFlights, this.returnCurrentPage);
  }

  get outboundTotalPages(): number {
    return this.getTotalPages(this.outboundFlights.length);
  }

  get returnTotalPages(): number {
    return this.getTotalPages(this.returnFlights.length);
  }

  onPageSizeChange(rawValue: string | number): void {
    const parsed = Number(rawValue);
    this.selectedPageSize = this.pageSizeOptions.includes(parsed) ? parsed : 10;
    this.outboundCurrentPage = 1;
    this.returnCurrentPage = 1;
  }

  getVisiblePageButtons(direction: 'OUTBOUND' | 'RETURN'): Array<number | '...'> {
    const totalPages = this.getTotalPagesForDirection(direction);
    const currentPage = this.getCurrentPageForDirection(direction);

    if (totalPages <= 0) {
      return [];
    }

    if (totalPages <= 7) {
      return Array.from({ length: totalPages }, (_, index) => index + 1);
    }

    const pages = new Set<number>();
    pages.add(1);
    pages.add(totalPages);

    for (let page = currentPage - 2; page <= currentPage + 2; page += 1) {
      if (page > 1 && page < totalPages) {
        pages.add(page);
      }
    }

    const sortedPages = [...pages].sort((a, b) => a - b);
    const buttons: Array<number | '...'> = [];
    sortedPages.forEach((page, index) => {
      const previousPage = index === 0 ? null : sortedPages[index - 1];
      if (previousPage !== null && page - previousPage > 1) {
        buttons.push('...');
      }
      buttons.push(page);
    });

    return buttons;
  }

  isPageActive(direction: 'OUTBOUND' | 'RETURN', page: number | '...'): boolean {
    return page !== '...' && this.getCurrentPageForDirection(direction) === page;
  }

  isPreviousDisabled(direction: 'OUTBOUND' | 'RETURN'): boolean {
    return this.getCurrentPageForDirection(direction) <= 1;
  }

  isNextDisabled(direction: 'OUTBOUND' | 'RETURN'): boolean {
    return this.getCurrentPageForDirection(direction) >= this.getTotalPagesForDirection(direction);
  }

  goToPreviousPage(direction: 'OUTBOUND' | 'RETURN'): void {
    this.setCurrentPageForDirection(direction, this.getCurrentPageForDirection(direction) - 1);
  }

  goToNextPage(direction: 'OUTBOUND' | 'RETURN'): void {
    this.setCurrentPageForDirection(direction, this.getCurrentPageForDirection(direction) + 1);
  }

  goToPage(direction: 'OUTBOUND' | 'RETURN', page: number | '...'): void {
    if (page === '...') {
      return;
    }

    this.setCurrentPageForDirection(direction, page);
  }

  getShowingText(direction: 'OUTBOUND' | 'RETURN'): string {
    const flights = direction === 'OUTBOUND' ? this.outboundFlights : this.returnFlights;
    const totalFlights = flights.length;
    if (totalFlights === 0) {
      return 'Showing 0-0 of 0 flights';
    }

    const currentPage = this.getCurrentPageForDirection(direction);
    const start = (currentPage - 1) * this.selectedPageSize + 1;
    const end = Math.min(start + this.selectedPageSize - 1, totalFlights);
    return `Showing ${start}-${end} of ${totalFlights} flights`;
  }

  getAirlineName(flight: FlightResponse): string {
    return this.airlineNameById[flight.airlineId] ?? `Airline ${flight.airlineId}`;
  }

  private loadResults(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.flexibleDateOptions = [];
    this.selectedFlexibleDate = '';
    this.flexibleOneWayResults = {};
    this.flexibleRoundTripResults = {};
    this.flexibleRoundTripReturnDates = {};
    this.outboundFlights = [];
    this.returnFlights = [];
    this.outboundCurrentPage = 1;
    this.returnCurrentPage = 1;

    const params = this.activatedRoute.snapshot.queryParams;
    if (!this.validateSearchDates(params)) {
      this.isLoading = false;
      return;
    }
    this.flexibleDateMode = String(params['flexibleDate']) === 'true';

    if (this.flexibleDateMode) {
      void this.loadFlexibleResults(params);
      return;
    }

    if (this.tripType === 'ROUND_TRIP') {
      const request: RoundTripSearchParams = {
        origin: params['origin'] as string,
        destination: params['destination'] as string,
        onwardDate: params['journeyDate'] as string,
        returnDate: params['returnDate'] as string,
        minPrice: this.parseOptionalNumber(params['minPrice']),
        maxPrice: this.parseOptionalNumber(params['maxPrice']),
        airlineId: this.parseOptionalNumber(params['airlineId']),
        departureWindow: this.parseDepartureWindow(params['departureWindow']),
        maxStops: this.parseOptionalNumber(params['maxStops']),
        seatClass: this.parseSeatClass(params['seatClass']),
        sortBy: this.parseOptionalString(params['sortBy'])
      };

      this.flightApiService.searchRoundTrip(request).subscribe({
        next: (response: RoundTripSearchResponse) => {
          this.outboundFlights = response.outboundFlights;
          this.returnFlights = response.returnFlights;
          this.outboundCurrentPage = 1;
          this.returnCurrentPage = 1;
          this.isLoading = false;
        },
        error: (error) => {
          this.errorMessage = error?.error?.message ?? 'Unable to fetch round-trip results.';
          this.isLoading = false;
        }
      });
      return;
    }

    const request: OneWaySearchParams = {
      origin: params['origin'] as string,
      destination: params['destination'] as string,
      journeyDate: params['journeyDate'] as string,
      minPrice: this.parseOptionalNumber(params['minPrice']),
      maxPrice: this.parseOptionalNumber(params['maxPrice']),
      airlineId: this.parseOptionalNumber(params['airlineId']),
      departureWindow: this.parseDepartureWindow(params['departureWindow']),
      maxStops: this.parseOptionalNumber(params['maxStops']),
      seatClass: this.parseSeatClass(params['seatClass']),
      sortBy: this.parseOptionalString(params['sortBy'])
    };

    this.flightApiService.searchOneWay(request).subscribe({
      next: (response: FlightResponse[]) => {
        this.outboundFlights = response;
        this.outboundCurrentPage = 1;
        this.isLoading = false;
      },
      error: (error) => {
        this.errorMessage = error?.error?.message ?? 'Unable to fetch one-way results.';
        this.isLoading = false;
      }
    });
  }

  openFlightDetails(flight: FlightResponse, direction: 'OUTBOUND' | 'RETURN'): void {
    this.selectedFlight = flight;
    this.selectedFlightDirection = direction;
    this.isDetailsOpen = true;
  }

  closeFlightDetails(): void {
    this.isDetailsOpen = false;
    this.selectedFlight = undefined;
  }

  goToSeatSelection(): void {
    if (!this.selectedFlight) {
      return;
    }

    const returnUrl = this.router.url;

    this.router.navigate(['/seats/select'], {
      queryParams: {
        flightId: this.selectedFlight.flightId,
        flightNumber: this.selectedFlight.flightNumber,
        origin: this.selectedFlight.originAirportCode,
        destination: this.selectedFlight.destinationAirportCode,
        baseFare: this.selectedFlight.displayedPrice,
        totalSeats: this.selectedFlight.totalSeats,
        aircraftType: this.selectedFlight.aircraftType,
        returnUrl
      }
    });
  }

  getAirportName(code: string): string {
    const match = this.airportOptions.find((airport) => airport.code === code.toUpperCase());
    return match?.airport ?? `${code} Airport`;
  }

  getFlightSegments(flight: FlightResponse | undefined): FlightSegment[] {
    if (!flight) {
      return [];
    }

    return [
      {
        fromCode: flight.originAirportCode,
        toCode: flight.destinationAirportCode,
        fromName: this.getAirportName(flight.originAirportCode),
        toName: this.getAirportName(flight.destinationAirportCode),
        departureTime: flight.departureTime,
        arrivalTime: flight.arrivalTime
      }
    ];
  }

  formatFlightDuration(durationMinutes: number): string {
    const hours = Math.floor(durationMinutes / 60);
    const minutes = durationMinutes % 60;
    return `${hours}h ${minutes}m`;
  }

  getSeatsLeft(flight: FlightResponse): number {
    return Math.max(Number(flight.availableSeats ?? 0), 0);
  }

  selectFlexibleDate(date: string): void {
    this.selectedFlexibleDate = date;
    if (this.tripType === 'ROUND_TRIP') {
      const result = this.flexibleRoundTripResults[date];
      if (result) {
        this.outboundFlights = result.outboundFlights;
        this.returnFlights = result.returnFlights;
        this.outboundCurrentPage = 1;
        this.returnCurrentPage = 1;
      }
      return;
    }

    const flights = this.flexibleOneWayResults[date];
    if (flights) {
      this.outboundFlights = flights;
      this.outboundCurrentPage = 1;
    }
  }

  private syncFilterState(params: Record<string, string>): void {
    this.filterTripType = params['tripType'] === 'ROUND_TRIP' ? 'ROUND_TRIP' : 'ONE_WAY';
    this.filterOrigin = String(params['origin'] ?? '').toUpperCase();
    this.filterDestination = String(params['destination'] ?? '').toUpperCase();
    this.filterJourneyDate = String(params['journeyDate'] ?? '');
    this.filterReturnDate = String(params['returnDate'] ?? '');
    this.filterSeatClass = String(params['seatClass'] ?? 'ECONOMY');
    this.filterSortBy = String(params['sortBy'] ?? 'price_asc');
    this.filterDepartureWindow = String(params['departureWindow'] ?? '');
    this.filterMaxStops = String(params['maxStops'] ?? '0');
    this.filterMinPrice = Number(params['minPrice'] ?? 0) || 0;
    this.filterMaxPrice = Number(params['maxPrice'] ?? 25000) || 25000;
  }

  private validateSearchDates(params: Record<string, string>): boolean {
    const journeyDate = String(params['journeyDate'] ?? '');
    const returnDate = String(params['returnDate'] ?? '');
    const flexibleSearch = String(params['flexibleDate']) === 'true';

    if (flexibleSearch) {
      const journeyMonth = String(params['journeyMonth'] ?? journeyDate.slice(0, 7) ?? '');
      if (journeyMonth && journeyMonth < this.todayDate.slice(0, 7)) {
        this.errorMessage = 'Flexible searches cannot start in a past month.';
        return false;
      }
      return true;
    }

    if (journeyDate && journeyDate < this.todayDate) {
      this.errorMessage = 'Departure date cannot be in the past.';
      return false;
    }

    if (this.tripType === 'ROUND_TRIP' && returnDate && returnDate <= journeyDate) {
      this.errorMessage = 'Return date must be after the departure date.';
      return false;
    }

    return true;
  }

  private async loadFlexibleResults(params: Record<string, string>): Promise<void> {
    if (this.tripType === 'ROUND_TRIP') {
      await this.loadFlexibleRoundTripResults(params);
      return;
    }

    await this.loadFlexibleOneWayResults(params);
  }

  private async loadFlexibleOneWayResults(params: Record<string, string>): Promise<void> {
    const month = this.parseOptionalString(params['journeyMonth'])
      ?? this.parseOptionalString(params['journeyDate'])?.slice(0, 7)
      ?? '';

    if (!month) {
      this.errorMessage = 'Unable to run flexible search without a departure month.';
      this.isLoading = false;
      return;
    }

    const baseRequest: OneWaySearchParams = {
      origin: params['origin'] as string,
      destination: params['destination'] as string,
      journeyDate: `${month}-01`,
      minPrice: this.parseOptionalNumber(params['minPrice']),
      maxPrice: this.parseOptionalNumber(params['maxPrice']),
      airlineId: this.parseOptionalNumber(params['airlineId']),
      departureWindow: this.parseDepartureWindow(params['departureWindow']),
      maxStops: this.parseOptionalNumber(params['maxStops']),
      seatClass: this.parseSeatClass(params['seatClass']),
      sortBy: this.parseOptionalString(params['sortBy'])
    };

    const dates = this.getMonthDates(month);
    const dayResults = await Promise.all(
      dates.map(async (journeyDate) => {
        try {
          const flights = await firstValueFrom(this.flightApiService.searchOneWay({ ...baseRequest, journeyDate }));
          return { journeyDate, flights };
        } catch {
          return { journeyDate, flights: [] as FlightResponse[] };
        }
      })
    );

    dayResults.forEach((result) => {
      this.flexibleOneWayResults[result.journeyDate] = result.flights;
    });

    this.flexibleDateOptions = dayResults.map((result) => ({
      date: result.journeyDate,
      label: this.formatDateLabel(result.journeyDate),
      count: result.flights.length,
      minPrice: result.flights.length ? Math.min(...result.flights.map((flight) => flight.displayedPrice)) : undefined
    }));

    const firstAvailable = dayResults.find((result) => result.flights.length > 0);
    if (firstAvailable) {
      this.selectedFlexibleDate = firstAvailable.journeyDate;
      this.outboundFlights = firstAvailable.flights;
      this.outboundCurrentPage = 1;
      this.isLoading = false;
      return;
    }

    this.outboundFlights = [];
    this.errorMessage = `No flights available for ${month}.`;
    this.isLoading = false;
  }

  private async loadFlexibleRoundTripResults(params: Record<string, string>): Promise<void> {
    const journeyMonth = this.parseOptionalString(params['journeyMonth'])
      ?? this.parseOptionalString(params['journeyDate'])?.slice(0, 7)
      ?? '';
    const returnMonth = this.parseOptionalString(params['returnMonth']) ?? journeyMonth;

    if (!journeyMonth) {
      this.errorMessage = 'Unable to run flexible search without a departure month.';
      this.isLoading = false;
      return;
    }

    const stayDays = this.calculateStayDays(
      this.parseOptionalString(params['journeyDate']) ?? `${journeyMonth}-01`,
      this.parseOptionalString(params['returnDate']) ?? `${returnMonth}-02`
    );

    const baseRequest: RoundTripSearchParams = {
      origin: params['origin'] as string,
      destination: params['destination'] as string,
      onwardDate: `${journeyMonth}-01`,
      returnDate: `${returnMonth}-02`,
      minPrice: this.parseOptionalNumber(params['minPrice']),
      maxPrice: this.parseOptionalNumber(params['maxPrice']),
      airlineId: this.parseOptionalNumber(params['airlineId']),
      departureWindow: this.parseDepartureWindow(params['departureWindow']),
      maxStops: this.parseOptionalNumber(params['maxStops']),
      seatClass: this.parseSeatClass(params['seatClass']),
      sortBy: this.parseOptionalString(params['sortBy'])
    };

    const dates = this.getMonthDates(journeyMonth);
    const dayResults = await Promise.all(
      dates.map(async (onwardDate) => {
        const returnDate = this.shiftDate(onwardDate, stayDays);
        try {
          const response = await firstValueFrom(
            this.flightApiService.searchRoundTrip({
              ...baseRequest,
              onwardDate,
              returnDate
            })
          );
          return { onwardDate, returnDate, response };
        } catch {
          return {
            onwardDate,
            returnDate,
            response: { outboundFlights: [] as FlightResponse[], returnFlights: [] as FlightResponse[] }
          };
        }
      })
    );

    dayResults.forEach((result) => {
      this.flexibleRoundTripResults[result.onwardDate] = result.response;
      this.flexibleRoundTripReturnDates[result.onwardDate] = result.returnDate;
    });

    this.flexibleDateOptions = dayResults.map((result) => {
      const allFlights = [...result.response.outboundFlights, ...result.response.returnFlights];
      return {
        date: result.onwardDate,
        label: this.formatDateLabel(result.onwardDate),
        count: allFlights.length,
        minPrice: allFlights.length ? Math.min(...allFlights.map((flight) => flight.displayedPrice)) : undefined
      };
    });

    const firstAvailable = dayResults.find(
      (result) => result.response.outboundFlights.length || result.response.returnFlights.length
    );
    if (firstAvailable) {
      this.selectedFlexibleDate = firstAvailable.onwardDate;
      this.outboundFlights = firstAvailable.response.outboundFlights;
      this.returnFlights = firstAvailable.response.returnFlights;
      this.outboundCurrentPage = 1;
      this.returnCurrentPage = 1;
      this.isLoading = false;
      return;
    }

    this.outboundFlights = [];
    this.returnFlights = [];
    this.errorMessage = `No round-trip flights available for ${journeyMonth}.`;
    this.isLoading = false;
  }

  private paginate(flights: FlightResponse[], page: number): FlightResponse[] {
    if (!flights.length) {
      return [];
    }

    const clampedPage = this.clampPage(page, this.getTotalPages(flights.length));
    const startIndex = (clampedPage - 1) * this.selectedPageSize;
    return flights.slice(startIndex, startIndex + this.selectedPageSize);
  }

  private getTotalPages(totalItems: number): number {
    if (totalItems <= 0) {
      return 0;
    }

    return Math.ceil(totalItems / this.selectedPageSize);
  }

  private clampPage(page: number, totalPages: number): number {
    if (totalPages <= 0) {
      return 1;
    }

    return Math.min(Math.max(page, 1), totalPages);
  }

  private getCurrentPageForDirection(direction: 'OUTBOUND' | 'RETURN'): number {
    return direction === 'OUTBOUND' ? this.outboundCurrentPage : this.returnCurrentPage;
  }

  private setCurrentPageForDirection(direction: 'OUTBOUND' | 'RETURN', page: number): void {
    const totalPages = this.getTotalPagesForDirection(direction);
    const clamped = this.clampPage(page, totalPages);
    if (direction === 'OUTBOUND') {
      this.outboundCurrentPage = clamped;
      return;
    }

    this.returnCurrentPage = clamped;
  }

  private getTotalPagesForDirection(direction: 'OUTBOUND' | 'RETURN'): number {
    return direction === 'OUTBOUND' ? this.outboundTotalPages : this.returnTotalPages;
  }

  private parseOptionalNumber(value: string | undefined): number | undefined {
    if (!value || value.trim() === '') {
      return undefined;
    }

    const parsed = Number(value);
    return Number.isNaN(parsed) ? undefined : parsed;
  }

  private parseOptionalString(value: string | undefined): string | undefined {
    return value && value.trim() !== '' ? value : undefined;
  }

  private parseSeatClass(value: string | undefined): SeatClass | undefined {
    const parsed = this.parseOptionalString(value);
    if (!parsed) {
      return undefined;
    }

    const validValues: SeatClass[] = ['ECONOMY', 'PREMIUM_ECONOMY', 'BUSINESS', 'FIRST'];
    return validValues.includes(parsed as SeatClass) ? (parsed as SeatClass) : undefined;
  }

  private parseDepartureWindow(value: string | undefined): DepartureWindow | undefined {
    const parsed = this.parseOptionalString(value);
    if (!parsed) {
      return undefined;
    }

    const validValues: DepartureWindow[] = ['MORNING', 'AFTERNOON', 'EVENING', 'NIGHT'];
    return validValues.includes(parsed as DepartureWindow) ? (parsed as DepartureWindow) : undefined;
  }


  private shiftDate(isoDate: string, dayOffset: number): string {
    const date = new Date(`${isoDate}T00:00:00`);
    date.setDate(date.getDate() + dayOffset);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private calculateStayDays(onwardDate: string, returnDate: string): number {
    const onward = new Date(`${onwardDate}T00:00:00`).getTime();
    const ret = new Date(`${returnDate}T00:00:00`).getTime();
    const diffDays = Math.round((ret - onward) / (1000 * 60 * 60 * 24));
    return Math.max(1, diffDays);
  }

  private getMonthDates(month: string): string[] {
    const [yearText, monthText] = month.split('-');
    const year = Number(yearText);
    const monthNumber = Number(monthText);
    if (!year || !monthNumber) {
      return [];
    }

    const daysInMonth = new Date(year, monthNumber, 0).getDate();
    const dates: string[] = [];
    for (let day = 1; day <= daysInMonth; day += 1) {
      const formattedDay = String(day).padStart(2, '0');
      dates.push(`${yearText}-${monthText}-${formattedDay}`);
    }
    return dates;
  }

  private formatDateLabel(isoDate: string): string {
    const [year, month, day] = isoDate.split('-').map(Number);
    const parsed = new Date(year, (month || 1) - 1, day || 1);
    return parsed.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
  }

  private formatDateLong(isoDate: string): string {
    const [year, month, day] = isoDate.split('-').map(Number);
    const parsed = new Date(year, (month || 1) - 1, day || 1);
    return parsed.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
  }

  private toIsoDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private resolveAirportCode(input: string): string | null {
    const trimmed = (input ?? '').trim();
    if (!trimmed) {
      return null;
    }

    const formatted = trimmed.match(/\(([A-Za-z]{3})\)$/);
    if (formatted) {
      return formatted[1].toUpperCase();
    }

    if (/^[A-Za-z]{3}$/.test(trimmed)) {
      return trimmed.toUpperCase();
    }

    const match = this.airportOptions.find((option) =>
      option.city.toLowerCase() === trimmed.toLowerCase()
      || option.airport.toLowerCase() === trimmed.toLowerCase()
    );
    return match?.code ?? null;
  }

  private filterAirportOptions(rawQuery: string): AirportOption[] {
    const query = (rawQuery ?? '').trim().toLowerCase();
    if (!query) {
      return this.airportOptions.slice(0, 6);
    }

    return this.airportOptions
      .filter((option) =>
        option.code.toLowerCase().includes(query)
        || option.city.toLowerCase().includes(query)
        || option.airport.toLowerCase().includes(query)
      )
      .slice(0, 6);
  }

  private loadReferenceData(): void {
    this.isReferenceLoading = true;
    forkJoin({
      airports: this.airlineAirportApiService.getAirports().pipe(catchError(() => of([] as AirportRecord[]))),
      airlines: this.airlineAirportApiService.getAirlines().pipe(catchError(() => of([] as AirlineRecord[])))
    }).subscribe(({ airports, airlines }) => {
      this.airportOptions = airports
        .filter((airport) => Boolean(airport.iataCode))
        .map((airport) => ({
          code: airport.iataCode.toUpperCase(),
          city: airport.city,
          airport: airport.name
        }))
        .sort((a, b) => a.city.localeCompare(b.city));

      this.airlineNameById = {};
      airlines
        .filter((airline) => (airline.active ?? airline.isActive ?? true) && airline.airlineId)
        .forEach((airline) => {
          this.airlineNameById[Number(airline.airlineId)] = airline.name;
        });
      this.isReferenceLoading = false;
    });
  }
}
