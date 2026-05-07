import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, HostListener, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription, catchError, forkJoin, of } from 'rxjs';

import {
  AdminAnalytics,
  AdminApiService,
  CancelManagedBookingRequest,
  DelayManagedFlightRequest,
  ManagedFlight,
  ManagedFlightRequest,
  StaffAirlineMapping,
  StaffFlightDashboard
} from '../../../core/services/admin-api.service';
import {
  AirlineAirportApiService,
  AirlineRecord,
  AirportRecord
} from '../../../core/services/airline-airport-api.service';
import { TokenStorageService } from '../../../core/services/token-storage.service';

type DashboardStatCard = {
  label: string;
  value: string;
  icon: 'flight' | 'booking' | 'airline' | 'revenue' | 'clock' | 'passengers' | 'users' | 'airport';
  tone: 'blue' | 'purple' | 'orange' | 'green' | 'indigo' | 'cyan';
  delta?: string;
};

type DashboardActionCard = {
  title: string;
  description: string;
  route: string;
  tone: 'blue' | 'purple' | 'orange';
  icon: 'create' | 'booking' | 'schedule' | 'airline' | 'airport' | 'passenger';
};

type DashboardBookingRow = {
  code: string;
  passenger: string;
  route: string;
  date: string;
  status: string;
};

type DashboardSideRow = {
  name: string;
  sub: string;
  meta: string;
  status: string;
};

type SectionKey = 'flights' | 'schedules' | 'bookings' | 'passengers' | 'payments' | 'users' | 'airlines' | 'airports';
type BookingTab = 'ALL' | 'CONFIRMED' | 'CANCELLED';
type SortField = 'pnrCode' | 'status' | 'totalFare';
type SortDirection = 'asc' | 'desc' | null;

interface PaginationState {
  page: number;
  pageSize: number;
}

@Component({
  selector: 'app-admin-dashboard-page',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterLink],
  templateUrl: './admin-dashboard-page.component.html',
  styleUrl: './admin-dashboard-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdminDashboardPageComponent implements OnInit, OnDestroy {
  panel: 'admin' | 'airline' = 'admin';
  section:
    | 'dashboard'
    | 'airlines'
    | 'flights'
    | 'airports'
    | 'bookings'
    | 'payments'
    | 'users'
    | 'schedules'
    | 'passengers' = 'dashboard';
  pageTitle = 'Dashboard';
  pageSubtitle = '';
  role = '';
  userId: number | null = null;
  analytics?: AdminAnalytics;
  flightDashboard?: StaffFlightDashboard;
  staffAirlineMapping?: StaffAirlineMapping;

  flights: ManagedFlight[] = [];
  selectedFlight?: ManagedFlight;
  selectedFlightBookings: Array<Record<string, unknown>> = [];
  selectedFlightPassengers: Array<Record<string, unknown>> = [];

  airlines: AirlineRecord[] = [];
  airports: AirportRecord[] = [];
  users: Array<Record<string, unknown>> = [];
  bookings: Array<Record<string, unknown>> = [];
  managedBookings: Array<Record<string, unknown>> = [];
  managedPassengers: Array<Record<string, unknown>> = [];
  payments: Array<Record<string, unknown>> = [];

  isLoading = false;
  isSaving = false;
  isActionRunning = false;
  isFilterRunning = false;
  errorMessage = '';
  statusMessage = '';
  actionErrorMessage = '';

  airportSearchTerm = '';
  airportSearchResults: AirportRecord[] = [];
  isAirportSearchLoading = false;

  editingFlightId: number | null = null;
  editingAirlineId: number | null = null;
  editingAirportId: number | null = null;

  isFlightDrawerOpen = false;
  isPassengerDrawerOpen = false;
  selectedPassenger?: Record<string, unknown>;

  isDelayModalOpen = false;
  delayTargetFlight?: ManagedFlight;

  isCancelFlightModalOpen = false;
  cancelFlightTarget?: ManagedFlight;
  cancelFlightAffectedBookings = 0;

  isCancelBookingModalOpen = false;
  cancelBookingTarget?: Record<string, unknown>;

  duplicateFlightBanner = '';

  readonly delayReasons = [
    'Weather Conditions',
    'Air Traffic Control',
    'Mechanical Issue',
    'Crew Availability',
    'Airport Operations',
    'Other'
  ];

  readonly bookingCancelReasons = [
    'Passenger Request',
    'Duplicate Booking',
    'No Show',
    'Operational Disruption',
    'Payment Issue'
  ];

  readonly flightStatusOptions: ManagedFlightRequest['status'][] = ['ON_TIME', 'DELAYED', 'CANCELLED', 'DEPARTED', 'ARRIVED'];
  readonly aircraftTypeOptions = ['Airbus A320', 'Airbus A321', 'Boeing 737', 'Boeing 787', 'Airbus A220'];
  readonly weekdayLabels = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  readonly statusChips = ['ALL', 'ON_TIME', 'DELAYED', 'CANCELLED'];
  readonly pageSizeOptions = [10, 25, 50];

  readonly paginationState: Record<SectionKey, PaginationState> = {
    flights: { page: 1, pageSize: 25 },
    schedules: { page: 1, pageSize: 25 },
    bookings: { page: 1, pageSize: 25 },
    passengers: { page: 1, pageSize: 25 },
    payments: { page: 1, pageSize: 25 },
    users: { page: 1, pageSize: 25 },
    airlines: { page: 1, pageSize: 25 },
    airports: { page: 1, pageSize: 25 }
  };

  flightSearchTerm = '';
  flightSearchCommitted = '';
  flightStatusFilter = 'ALL';

  scheduleSearchTerm = '';
  scheduleSearchCommitted = '';
  scheduleStatusFilter = 'ALL';
  scheduleQuickFilter: 'ALL' | 'TODAY' | 'TOMORROW' | 'THIS_WEEK' = 'ALL';
  scheduleFromDate = '';
  scheduleToDate = '';
  scheduleStatusPopoverFlightId: number | null = null;

  bookingSearchTerm = '';
  bookingSearchCommitted = '';
  bookingStatusTab: BookingTab = 'ALL';
  bookingSortField: SortField | null = null;
  bookingSortDirection: SortDirection = null;
  expandedBookingId: string | null = null;

  passengerSearchTerm = '';
  passengerSearchCommitted = '';
  passengerFlightFilter = 'ALL';
  passengerTypeFilter = 'ALL';

  private flightSearchTimer: ReturnType<typeof setTimeout> | null = null;
  private scheduleSearchTimer: ReturnType<typeof setTimeout> | null = null;
  private bookingSearchTimer: ReturnType<typeof setTimeout> | null = null;
  private passengerSearchTimer: ReturnType<typeof setTimeout> | null = null;
  private readonly adminOnlySections = new Set<SectionKey>(['airlines', 'airports', 'payments', 'users']);
  private overlayTriggerElement: HTMLElement | null = null;

  readonly flightDelayForm = this.formBuilder.group({
    delayReason: ['', Validators.required],
    newEstimatedDepartureTime: ['', Validators.required],
    internalNotes: ['']
  });

  readonly bookingCancelForm = this.formBuilder.group({
    reason: ['Passenger Request', Validators.required]
  });

  readonly flightForm = this.formBuilder.group({
    flightNumber: ['', [Validators.required, Validators.minLength(3)]],
    airlineId: [0, [Validators.required, Validators.min(1)]],
    originAirportCode: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(5)]],
    destinationAirportCode: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(5)]],
    departureTime: ['', Validators.required],
    arrivalTime: ['', Validators.required],
    durationMinutes: [60, [Validators.required, Validators.min(1)]],
    numberOfStops: [0, [Validators.required, Validators.min(0), Validators.max(1)]],
    viaAirportCode: [''],
    status: ['ON_TIME' as ManagedFlightRequest['status'], Validators.required],
    aircraftType: ['Airbus A320', Validators.required],
    totalSeats: [180, [Validators.required, Validators.min(1)]],
    availableSeats: [180, [Validators.required, Validators.min(0)]],
    basePrice: [3000, [Validators.required, Validators.min(1)]]
  });

  readonly airlineForm = this.formBuilder.group({
    name: ['', Validators.required],
    iataCode: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(3)]],
    country: ['', Validators.required],
    active: [true]
  });

  readonly airportForm = this.formBuilder.group({
    name: ['', Validators.required],
    iataCode: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(3)]],
    city: ['', Validators.required],
    country: ['', Validators.required],
    timezone: ['', Validators.required],
    latitude: [0, Validators.required],
    longitude: [0, Validators.required]
  });

  private routeDataSub?: Subscription;

  constructor(
    private readonly adminApiService: AdminApiService,
    private readonly airlineAirportApiService: AirlineAirportApiService,
    private readonly tokenStorageService: TokenStorageService,
    private readonly activatedRoute: ActivatedRoute,
    private readonly formBuilder: FormBuilder,
    private readonly cdr: ChangeDetectorRef,
    private readonly elementRef: ElementRef<HTMLElement>
  ) {}

  ngOnInit(): void {
    this.role = this.tokenStorageService.getRole() ?? '';
    this.userId = this.tokenStorageService.getUserId();
    this.applyRouteData(this.activatedRoute?.snapshot?.data ?? {});

    if (!this.isOperationsUser) {
      this.errorMessage = '403 — You do not have permission to perform this action.';
      return;
    }

    if (this.panel === 'admin' && !this.isAdmin) {
      this.errorMessage = '403 — You do not have permission to perform this action.';
      return;
    }
    if (this.panel === 'airline' && !this.isStaff) {
      this.errorMessage = '403 — You do not have permission to perform this action.';
      return;
    }

    this.routeDataSub = this.activatedRoute.data.subscribe((data) => {
      this.applyRouteData(data ?? {});
      this.cdr.markForCheck();
    });

    this.loadData();
  }

  ngOnDestroy(): void {
    this.routeDataSub?.unsubscribe();
    this.clearDebounceTimers();
  }

  @HostListener('document:keydown.escape', ['$event'])
  onEscapePressed(event: KeyboardEvent): void {
    if (this.isDelayModalOpen) {
      event.preventDefault();
      this.closeDelayModal();
      return;
    }
    if (this.isCancelFlightModalOpen) {
      event.preventDefault();
      this.closeCancelFlightModal();
      return;
    }
    if (this.isCancelBookingModalOpen) {
      event.preventDefault();
      this.closeCancelBookingModal();
      return;
    }
    if (this.isFlightDrawerOpen) {
      event.preventDefault();
      this.clearFlightDetails();
      return;
    }
    if (this.isPassengerDrawerOpen) {
      event.preventDefault();
      this.closePassengerDetails();
      return;
    }
    if (this.scheduleStatusPopoverFlightId !== null) {
      event.preventDefault();
      this.scheduleStatusPopoverFlightId = null;
      this.cdr.markForCheck();
    }
  }

  get isAdmin(): boolean {
    return this.role === 'ADMIN';
  }

  get isStaff(): boolean {
    return this.role === 'AIRLINE_STAFF';
  }

  get isOperationsUser(): boolean {
    return this.isAdmin || this.isStaff;
  }

  get canAccessCurrentSection(): boolean {
    if (this.isAdmin) {
      return true;
    }
    return !this.adminOnlySections.has(this.section as SectionKey);
  }

  get permissionErrorMessage(): string {
    return '403 — You do not have permission to perform this action.';
  }

  get managedOrAllBookings(): Array<Record<string, unknown>> {
    return this.isAdmin ? this.bookings : this.managedBookings;
  }

  get totalRevenue(): number {
    return Number(this.analytics?.revenue ?? 0);
  }

  get totalManagedRecords(): number {
    return this.airlines.length + this.airports.length;
  }

  get dashboardStats(): DashboardStatCard[] {
    if (this.isAdmin) {
      return [
        { label: 'Total Users', value: this.formatNumber(this.users.length), icon: 'users', tone: 'blue' },
        { label: 'Total Airlines', value: this.formatNumber(this.airlines.length), icon: 'airline', tone: 'purple' },
        { label: 'Total Flights', value: this.formatNumber(this.flightDashboard?.totalFlights ?? this.flights.length), icon: 'flight', tone: 'orange' },
        { label: 'Total Bookings', value: this.formatNumber(this.bookings.length), icon: 'booking', tone: 'green' },
        { label: 'Revenue', value: this.formatCurrencyCompact(this.totalRevenue), icon: 'revenue', tone: 'indigo' },
        { label: 'Active Airports', value: this.formatNumber(this.airports.length), icon: 'airport', tone: 'cyan' }
      ];
    }

    return [
      { label: 'Total Flights', value: this.formatNumber(this.flightDashboard?.totalFlights ?? this.flights.length), icon: 'flight', tone: 'blue' },
      { label: 'Total Bookings', value: this.formatNumber(this.managedBookings.length), icon: 'booking', tone: 'purple' },
      { label: 'Active Airlines', value: this.formatNumber(this.activeAirlinesCount), icon: 'airline', tone: 'orange' },
      { label: 'Revenue', value: this.formatCurrencyCompact(this.staffRevenue), icon: 'revenue', tone: 'green' },
      { label: `Today's Flights`, value: this.formatNumber(this.flightDashboard?.todayFlights ?? this.todayFlightsDerived), icon: 'clock', tone: 'indigo' },
      { label: 'Managed Passengers', value: this.formatNumber(this.managedPassengers.length), icon: 'passengers', tone: 'cyan' }
    ];
  }

  get dashboardActions(): DashboardActionCard[] {
    if (this.isAdmin) {
      return [
        {
          title: 'Create Flight',
          description: 'Plan new departures and seat inventory',
          route: '/admin/flights',
          tone: 'blue',
          icon: 'create'
        },
        {
          title: 'View Bookings',
          description: 'Track booking status and volume',
          route: '/admin/bookings',
          tone: 'purple',
          icon: 'booking'
        },
        {
          title: 'Manage Airlines',
          description: 'Control airline records and activation',
          route: '/admin/airlines',
          tone: 'orange',
          icon: 'airline'
        },
        {
          title: 'Manage Airports',
          description: 'Add or remove airports',
          route: '/admin/airports',
          tone: 'blue',
          icon: 'airport'
        },
        {
          title: 'Manage Passengers',
          description: 'View and manage passenger data',
          route: '/admin/users',
          tone: 'purple',
          icon: 'passenger'
        }
      ];
    }

    return [
      {
        title: 'Create Flight',
        description: 'Plan new departures and seat inventory',
        route: '/airline/flights',
        tone: 'blue',
        icon: 'create'
      },
      {
        title: 'View Bookings',
        description: 'Track booking status and volume',
        route: '/airline/bookings',
        tone: 'purple',
        icon: 'booking'
      },
      {
        title: 'Review Schedule',
        description: 'Review timing and status for operations',
        route: '/airline/schedules',
        tone: 'orange',
        icon: 'schedule'
      }
    ];
  }

  get recentBookingRows(): DashboardBookingRow[] {
    const source = this.isAdmin ? this.bookings : this.managedBookings;
    return source
      .slice(0, 8)
      .map((entry) => this.toBookingRow(entry))
      .filter((row) => Boolean(row.code));
  }

  get sideRows(): DashboardSideRow[] {
    if (this.isAdmin) {
      return this.users.slice(0, 6).map((entry) => {
        const name = String(entry['fullName'] ?? entry['name'] ?? 'User');
        const email = String(entry['email'] ?? `User #${entry['userId'] ?? '-'}`);
        const status = Boolean(entry['active']) ? 'ACTIVE' : 'INACTIVE';
        const userRole = String(entry['role'] ?? 'USER');
        return {
          name,
          sub: email,
          meta: userRole,
          status
        };
      });
    }

    return this.managedPassengers.slice(0, 6).map((entry) => {
      const firstName = String(entry['firstName'] ?? '').trim();
      const lastName = String(entry['lastName'] ?? '').trim();
      const name = `${firstName} ${lastName}`.trim() || `Passenger #${entry['passengerId'] ?? '-'}`;
      const seat = String(entry['seatNumber'] ?? 'Seat pending');
      const flight = String(entry['flightNumber'] ?? `Flight ${entry['flightId'] ?? '-'}`);
      const status = String(entry['status'] ?? 'PENDING').toUpperCase();
      return {
        name,
        sub: seat,
        meta: flight,
        status
      };
    });
  }

  get bookingTrendSeries(): number[] {
    const data = this.weekdayLabels.map(() => 0);
    const source = this.isAdmin ? this.bookings : this.managedBookings;
    source.forEach((entry) => {
      const dateValue = String(entry['bookedAt'] ?? '');
      const date = new Date(dateValue);
      if (!Number.isNaN(date.getTime())) {
        const normalized = (date.getDay() + 6) % 7;
        data[normalized] += 1;
      }
    });
    return data;
  }

  get revenueTrendSeries(): number[] {
    const data = this.weekdayLabels.map(() => 0);
    if (this.isAdmin && this.payments.length) {
      this.payments.forEach((entry) => {
        const rawDate = String(entry['createdAt'] ?? entry['updatedAt'] ?? '');
        const date = new Date(rawDate);
        if (!Number.isNaN(date.getTime())) {
          const normalized = (date.getDay() + 6) % 7;
          data[normalized] += Number(entry['amount'] ?? 0);
        }
      });
      return data;
    }

    this.managedBookings.forEach((entry) => {
      const rawDate = String(entry['bookedAt'] ?? '');
      const date = new Date(rawDate);
      if (!Number.isNaN(date.getTime())) {
        const normalized = (date.getDay() + 6) % 7;
        data[normalized] += Number(entry['totalFare'] ?? 0);
      }
    });
    return data;
  }

  get hasBookingTrendData(): boolean {
    return this.bookingTrendSeries.some((value) => value > 0);
  }

  get hasRevenueTrendData(): boolean {
    return this.revenueTrendSeries.some((value) => value > 0);
  }

  get activeAirlinesCount(): number {
    return this.airlines.filter((entry) => this.isAirlineActive(entry)).length;
  }

  get staffRevenue(): number {
    return this.managedBookings.reduce((sum, entry) => sum + Number(entry['totalFare'] ?? 0), 0);
  }

  get todayFlightsDerived(): number {
    const today = new Date();
    const todayKey = today.toISOString().slice(0, 10);
    return this.flights.filter((flight) => String(flight.departureTime ?? '').slice(0, 10) === todayKey).length;
  }

  get bookingTrendPoints(): string {
    return this.toSvgPoints(this.bookingTrendSeries);
  }

  get revenueTrendPoints(): string {
    return this.toSvgPoints(this.revenueTrendSeries);
  }

  get bookingTrendArea(): string {
    return this.toSvgArea(this.bookingTrendSeries);
  }

  get revenueTrendArea(): string {
    return this.toSvgArea(this.revenueTrendSeries);
  }

  get filteredFlights(): ManagedFlight[] {
    const query = this.flightSearchCommitted.trim().toLowerCase();
    const statusFilter = this.flightStatusFilter;

    // TODO: switch to server-side filtering when API supports flightNumber/route/status query params.
    return this.flights.filter((flight) => {
      const statusMatch = statusFilter === 'ALL' || String(flight.status).toUpperCase() === statusFilter;
      if (!statusMatch) {
        return false;
      }

      if (!query) {
        return true;
      }

      const haystack = [
        flight.flightNumber,
        `${flight.originAirportCode}-${flight.destinationAirportCode}`,
        `${flight.originAirportCode}${flight.destinationAirportCode}`,
        flight.viaAirportCode ?? ''
      ]
        .join(' ')
        .toLowerCase();

      return haystack.includes(query);
    });
  }

  get pagedFlights(): ManagedFlight[] {
    return this.paginate(this.filteredFlights, this.paginationState.flights);
  }

  get filteredSchedules(): ManagedFlight[] {
    const query = this.scheduleSearchCommitted.trim().toLowerCase();
    const statusFilter = this.scheduleStatusFilter;
    const from = this.scheduleFromDate ? new Date(`${this.scheduleFromDate}T00:00:00`) : null;
    const to = this.scheduleToDate ? new Date(`${this.scheduleToDate}T23:59:59`) : null;
    const today = new Date();
    const todayStart = new Date(today.getFullYear(), today.getMonth(), today.getDate());

    return this.flights.filter((flight) => {
      const statusMatch = statusFilter === 'ALL' || String(flight.status).toUpperCase() === statusFilter;
      if (!statusMatch) {
        return false;
      }

      const departure = new Date(flight.departureTime);
      if (!Number.isNaN(departure.getTime())) {
        if (this.scheduleQuickFilter === 'TODAY') {
          const flightDate = new Date(departure.getFullYear(), departure.getMonth(), departure.getDate());
          if (flightDate.getTime() !== todayStart.getTime()) {
            return false;
          }
        }

        if (this.scheduleQuickFilter === 'TOMORROW') {
          const tomorrow = new Date(todayStart);
          tomorrow.setDate(todayStart.getDate() + 1);
          const flightDate = new Date(departure.getFullYear(), departure.getMonth(), departure.getDate());
          if (flightDate.getTime() !== tomorrow.getTime()) {
            return false;
          }
        }

        if (this.scheduleQuickFilter === 'THIS_WEEK') {
          const weekEnd = new Date(todayStart);
          weekEnd.setDate(todayStart.getDate() + 6);
          const flightDate = new Date(departure.getFullYear(), departure.getMonth(), departure.getDate());
          if (flightDate.getTime() < todayStart.getTime() || flightDate.getTime() > weekEnd.getTime()) {
            return false;
          }
        }

        if (from && departure.getTime() < from.getTime()) {
          return false;
        }
        if (to && departure.getTime() > to.getTime()) {
          return false;
        }
      }

      if (!query) {
        return true;
      }

      return (
        String(flight.originAirportCode).toLowerCase().startsWith(query)
        || String(flight.destinationAirportCode).toLowerCase().startsWith(query)
      );
    });
  }

  get pagedSchedules(): ManagedFlight[] {
    return this.paginate(this.filteredSchedules, this.paginationState.schedules);
  }

  get filteredBookings(): Array<Record<string, unknown>> {
    const query = this.bookingSearchCommitted.trim().toLowerCase();
    const source = this.managedOrAllBookings;

    let rows = source.filter((row) => {
      const status = String(row['status'] ?? 'PENDING').toUpperCase();
      if (this.bookingStatusTab === 'CONFIRMED' && status !== 'CONFIRMED') {
        return false;
      }
      if (this.bookingStatusTab === 'CANCELLED' && status !== 'CANCELLED') {
        return false;
      }

      if (!query) {
        return true;
      }

      const bookingId = String(row['bookingId'] ?? '').toLowerCase();
      const pnrCode = String(row['pnrCode'] ?? '').toLowerCase();
      return bookingId.includes(query) || pnrCode.includes(query);
    });

    if (this.bookingSortField && this.bookingSortDirection) {
      rows = [...rows].sort((a, b) => {
        const lhs = a[this.bookingSortField as string];
        const rhs = b[this.bookingSortField as string];

        if (this.bookingSortField === 'totalFare') {
          const leftNum = Number(lhs ?? 0);
          const rightNum = Number(rhs ?? 0);
          return this.bookingSortDirection === 'asc' ? leftNum - rightNum : rightNum - leftNum;
        }

        const leftStr = String(lhs ?? '').toUpperCase();
        const rightStr = String(rhs ?? '').toUpperCase();
        if (leftStr === rightStr) {
          return 0;
        }
        if (this.bookingSortDirection === 'asc') {
          return leftStr > rightStr ? 1 : -1;
        }
        return leftStr < rightStr ? 1 : -1;
      });
    }

    return rows;
  }

  get pagedBookings(): Array<Record<string, unknown>> {
    return this.paginate(this.filteredBookings, this.paginationState.bookings);
  }

  get confirmedBookingCount(): number {
    return this.managedOrAllBookings.filter((booking) => String(booking['status'] ?? '').toUpperCase() === 'CONFIRMED').length;
  }

  get cancelledBookingCount(): number {
    return this.managedOrAllBookings.filter((booking) => String(booking['status'] ?? '').toUpperCase() === 'CANCELLED').length;
  }

  get bookingsRevenueTotal(): number {
    return this.managedOrAllBookings
      .filter((booking) => String(booking['status'] ?? '').toUpperCase() === 'CONFIRMED')
      .reduce((total, booking) => total + Number(booking['totalFare'] ?? 0), 0);
  }

  get uniquePassengerFlightNumbers(): string[] {
    const set = new Set<string>();
    this.managedPassengers.forEach((passenger) => {
      const num = String(passenger['flightNumber'] ?? '').trim();
      if (num) {
        set.add(num);
      }
    });
    return [...set].sort();
  }

  get filteredPassengers(): Array<Record<string, unknown>> {
    const query = this.passengerSearchCommitted.trim().toLowerCase();

    return this.managedPassengers.filter((passenger) => {
      const first = String(passenger['firstName'] ?? '').trim();
      const last = String(passenger['lastName'] ?? '').trim();
      const ticketNumber = String(passenger['ticketNumber'] ?? '').trim();
      const type = String(passenger['passengerType'] ?? 'ADULT').toUpperCase();
      const flightNumber = String(passenger['flightNumber'] ?? '').trim();

      if (this.passengerTypeFilter !== 'ALL' && type !== this.passengerTypeFilter) {
        return false;
      }

      if (this.passengerFlightFilter !== 'ALL' && flightNumber !== this.passengerFlightFilter) {
        return false;
      }

      if (!query) {
        return true;
      }

      const haystack = `${first} ${last} ${ticketNumber}`.toLowerCase();
      return haystack.includes(query);
    });
  }

  get pagedPassengers(): Array<Record<string, unknown>> {
    return this.paginate(this.filteredPassengers, this.paginationState.passengers);
  }

  get pagedUsers(): Array<Record<string, unknown>> {
    return this.paginate(this.users, this.paginationState.users);
  }

  get pagedPayments(): Array<Record<string, unknown>> {
    return this.paginate(this.payments, this.paginationState.payments);
  }

  get pagedAirlines(): AirlineRecord[] {
    return this.paginate(this.airlines, this.paginationState.airlines);
  }

  get pagedAirports(): AirportRecord[] {
    return this.paginate(this.airports, this.paginationState.airports);
  }

  statusBadgeClass(status: string): string {
    const normalized = String(status ?? '').toUpperCase();
    if (normalized.includes('CONFIRMED') || normalized.includes('BOARDED') || normalized.includes('CHECKED') || normalized.includes('ON_TIME') || normalized.includes('ARRIVED')) {
      return 'status-success';
    }
    if (normalized.includes('PENDING') || normalized.includes('DELAYED')) {
      return 'status-warning';
    }
    if (normalized.includes('CANCEL')) {
      return 'status-danger';
    }
    return 'status-neutral';
  }

  formatBookingStatus(status: string): string {
    const normalized = String(status ?? '').toUpperCase();
    if (normalized === 'CONFIRMED') {
      return 'Confirmed';
    }
    if (normalized === 'PENDING') {
      return 'Pending';
    }
    if (normalized === 'CANCELLED') {
      return 'Cancelled';
    }
    if (normalized === 'CHECKED_IN') {
      return 'Checked In';
    }
    if (normalized === 'ON_TIME') {
      return 'On Time';
    }
    if (normalized === 'DELAYED') {
      return 'Delayed';
    }
    return normalized || 'Unknown';
  }

  getRecordStatus(entry: Record<string, unknown>, fallback = 'PENDING', key = 'status'): string {
    return String(entry[key] ?? fallback);
  }

  isRecordStatus(entry: Record<string, unknown>, expected: string, key = 'status'): boolean {
    return this.getRecordStatus(entry, '', key).toUpperCase() === expected.toUpperCase();
  }

  isUserActive(entry: Record<string, unknown>): boolean {
    return Boolean(entry['active']);
  }

  getPaginationPages(section: SectionKey): number[] {
    const state = this.paginationState[section];
    const total = this.getSectionTotal(section);
    const totalPages = Math.max(1, Math.ceil(total / state.pageSize));
    return Array.from({ length: totalPages }, (_, index) => index + 1);
  }

  getVisiblePaginationPages(section: SectionKey): Array<number | '...'> {
    const state = this.paginationState[section];
    const total = this.getSectionTotal(section);
    const totalPages = Math.max(1, Math.ceil(total / state.pageSize));
    const currentPage = Math.max(1, Math.min(totalPages, state.page));

    if (totalPages <= 7) {
      return Array.from({ length: totalPages }, (_, index) => index + 1);
    }

    const pages = new Set<number>([1, totalPages]);
    const start = Math.max(2, currentPage - 2);
    const end = Math.min(totalPages - 1, currentPage + 2);

    for (let page = start; page <= end; page += 1) {
      pages.add(page);
    }

    const sorted = [...pages].sort((a, b) => a - b);
    const visible: Array<number | '...'> = [];

    sorted.forEach((page, index) => {
      const prev = index > 0 ? sorted[index - 1] : null;
      if (prev !== null && page - prev > 1) {
        visible.push('...');
      }
      visible.push(page);
    });

    return visible;
  }

  getSectionTotal(section: SectionKey): number {
    switch (section) {
      case 'flights':
        return this.filteredFlights.length;
      case 'schedules':
        return this.filteredSchedules.length;
      case 'bookings':
        return this.filteredBookings.length;
      case 'passengers':
        return this.filteredPassengers.length;
      case 'users':
        return this.users.length;
      case 'payments':
        return this.payments.length;
      case 'airlines':
        return this.airlines.length;
      case 'airports':
        return this.airports.length;
      default:
        return 0;
    }
  }

  getRangeLabel(section: SectionKey): string {
    const total = this.getSectionTotal(section);
    if (!total) {
      return 'Showing 0 of 0';
    }
    const state = this.paginationState[section];
    const start = (state.page - 1) * state.pageSize + 1;
    const end = Math.min(total, state.page * state.pageSize);
    return `Showing ${start}-${end} of ${total}`;
  }

  changePage(section: SectionKey, page: number): void {
    const pages = this.getPaginationPages(section);
    if (!pages.length) {
      return;
    }
    const min = 1;
    const max = pages[pages.length - 1];
    const nextPage = Math.max(min, Math.min(max, page));
    this.paginationState[section] = { ...this.paginationState[section], page: nextPage };
    this.cdr.markForCheck();
  }

  isPreviousPageDisabled(section: SectionKey): boolean {
    return this.paginationState[section].page <= 1;
  }

  isNextPageDisabled(section: SectionKey): boolean {
    const pages = this.getPaginationPages(section);
    const lastPage = pages.length ? pages[pages.length - 1] : 1;
    return this.paginationState[section].page >= lastPage;
  }

  changePageSize(section: SectionKey, size: number): void {
    this.paginationState[section] = { page: 1, pageSize: size };
    this.cdr.markForCheck();
  }

  onFlightSearchInput(value: string): void {
    this.flightSearchTerm = value;
    this.runDebounced(() => {
      this.flightSearchCommitted = this.flightSearchTerm;
      this.changePage('flights', 1);
    }, 'flight');
  }

  onBookingSearchInput(value: string): void {
    this.bookingSearchTerm = value;
    this.runDebounced(() => {
      this.bookingSearchCommitted = this.bookingSearchTerm;
      this.changePage('bookings', 1);
    }, 'booking');
  }

  onPassengerSearchInput(value: string): void {
    this.passengerSearchTerm = value;
    this.runDebounced(() => {
      this.passengerSearchCommitted = this.passengerSearchTerm;
      this.changePage('passengers', 1);
    }, 'passenger');
  }

  onScheduleSearchInput(value: string): void {
    this.scheduleSearchTerm = value;
    this.runDebounced(() => {
      this.scheduleSearchCommitted = this.scheduleSearchTerm;
      this.changePage('schedules', 1);
    }, 'schedule');
  }

  clearFlightSearch(): void {
    this.flightSearchTerm = '';
    this.flightSearchCommitted = '';
    this.changePage('flights', 1);
  }

  clearBookingSearch(): void {
    this.bookingSearchTerm = '';
    this.bookingSearchCommitted = '';
    this.changePage('bookings', 1);
  }

  clearPassengerSearch(): void {
    this.passengerSearchTerm = '';
    this.passengerSearchCommitted = '';
    this.changePage('passengers', 1);
  }

  clearScheduleSearch(): void {
    this.scheduleSearchTerm = '';
    this.scheduleSearchCommitted = '';
    this.changePage('schedules', 1);
  }

  setFlightStatusFilter(status: string): void {
    this.flightStatusFilter = status;
    this.changePage('flights', 1);
  }

  setScheduleStatusFilter(status: string): void {
    this.scheduleStatusFilter = status;
    this.changePage('schedules', 1);
  }

  setScheduleQuickFilter(filter: 'ALL' | 'TODAY' | 'TOMORROW' | 'THIS_WEEK'): void {
    this.scheduleQuickFilter = filter;
    this.scheduleFromDate = '';
    this.scheduleToDate = '';
    this.changePage('schedules', 1);
  }

  onCustomScheduleDateChange(): void {
    if (this.scheduleFromDate || this.scheduleToDate) {
      this.scheduleQuickFilter = 'ALL';
    }
    this.changePage('schedules', 1);
  }

  toggleScheduleStatusPopover(flightId: number): void {
    this.scheduleStatusPopoverFlightId = this.scheduleStatusPopoverFlightId === flightId ? null : flightId;
    if (this.scheduleStatusPopoverFlightId !== null) {
      this.focusFirstInteractive(`[data-status-popover-id="${flightId}"]`);
    }
  }

  updateScheduleStatus(flight: ManagedFlight, status: ManagedFlightRequest['status']): void {
    this.scheduleStatusPopoverFlightId = null;
    if (status === 'DELAYED') {
      this.openDelayModal(flight);
      return;
    }
    if (status === 'CANCELLED') {
      this.openCancelFlightModal(flight);
      return;
    }
    this.updateFlightStatusDirect(flight, status);
  }

  sortBookingColumn(field: SortField): void {
    if (this.bookingSortField !== field) {
      this.bookingSortField = field;
      this.bookingSortDirection = 'asc';
      return;
    }

    if (this.bookingSortDirection === 'asc') {
      this.bookingSortDirection = 'desc';
      return;
    }

    if (this.bookingSortDirection === 'desc') {
      this.bookingSortField = null;
      this.bookingSortDirection = null;
      return;
    }

    this.bookingSortDirection = 'asc';
  }

  setBookingStatusTab(tab: BookingTab): void {
    this.bookingStatusTab = tab;
    this.changePage('bookings', 1);
  }

  bookingTabCount(tab: BookingTab): number {
    if (tab === 'ALL') {
      return this.managedOrAllBookings.length;
    }
    return this.managedOrAllBookings.filter((row) => String(row['status'] ?? '').toUpperCase() === tab).length;
  }

  toggleBookingExpand(booking: Record<string, unknown>): void {
    const bookingId = String(booking['bookingId'] ?? '');
    if (!bookingId) {
      return;
    }
    this.expandedBookingId = this.expandedBookingId === bookingId ? null : bookingId;
  }

  getExpandedBookingFlight(booking: Record<string, unknown>): ManagedFlight | undefined {
    const flightId = Number(booking['flightId'] ?? NaN);
    return this.findFlightById(flightId);
  }

  getExpandedBookingRoute(booking: Record<string, unknown>): string {
    const flight = this.getExpandedBookingFlight(booking);
    if (!flight) {
      return 'Route pending';
    }
    return `${flight.originAirportCode} -> ${flight.destinationAirportCode}`;
  }

  getExpandedBookingDeparture(booking: Record<string, unknown>): string {
    const flight = this.getExpandedBookingFlight(booking);
    if (!flight?.departureTime) {
      return '--';
    }
    return this.formatDateTime(flight.departureTime);
  }

  getExpandedBookingPassengers(booking: Record<string, unknown>): Array<Record<string, unknown>> {
    const bookingId = String(booking['bookingId'] ?? '').trim();
    if (!bookingId) {
      return [];
    }
    return this.managedPassengers.filter((passenger) => String(passenger['bookingId'] ?? '').trim() === bookingId);
  }

  getExpandedBookingPassengerSummary(booking: Record<string, unknown>): string {
    const passengers = this.getExpandedBookingPassengers(booking);
    if (!passengers.length) {
      return 'Passenger details pending';
    }
    return passengers
      .map((passenger) => {
        const first = String(passenger['firstName'] ?? '').trim();
        const last = String(passenger['lastName'] ?? '').trim();
        const seat = String(passenger['seatNumber'] ?? '').trim();
        const fullName = `${first} ${last}`.trim() || 'Passenger';
        return seat ? `${fullName} (${seat})` : fullName;
      })
      .join(', ');
  }

  openPassengerDetails(passenger: Record<string, unknown>, triggerEvent?: Event): void {
    this.captureOverlayTrigger(triggerEvent);
    this.selectedPassenger = passenger;
    this.isPassengerDrawerOpen = true;
    this.focusFirstInteractive('.detail-drawer');
  }

  closePassengerDetails(): void {
    this.selectedPassenger = undefined;
    this.isPassengerDrawerOpen = false;
    this.restoreOverlayTriggerFocus();
  }

  onOverlayKeydown(event: KeyboardEvent, containerSelector: string): void {
    if (event.key !== 'Tab') {
      return;
    }
    const container = this.elementRef.nativeElement.querySelector(containerSelector);
    if (!(container instanceof HTMLElement)) {
      return;
    }
    this.trapFocusWithin(container, event);
  }

  onStatusPopoverKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.scheduleStatusPopoverFlightId = null;
      this.cdr.markForCheck();
      return;
    }
    if (event.key !== 'Tab') {
      return;
    }
    const popover = event.currentTarget;
    if (!(popover instanceof HTMLElement)) {
      return;
    }
    this.trapFocusWithin(popover, event);
  }

  setPassengerFlightFilter(value: string): void {
    this.passengerFlightFilter = value;
    this.changePage('passengers', 1);
  }

  setPassengerTypeFilter(value: string): void {
    this.passengerTypeFilter = value;
    this.changePage('passengers', 1);
  }

  exportPassengersCsv(): void {
    try {
      const rows = this.filteredPassengers;
      const header = ['Name', 'Seat', 'Ticket', 'Passenger Type', 'Flight ID'];
      const csvRows = [header.join(',')];

      rows.forEach((passenger) => {
        const name = `${String(passenger['firstName'] ?? '').trim()} ${String(passenger['lastName'] ?? '').trim()}`.trim();
        const seat = String(passenger['seatNumber'] ?? '');
        const ticket = String(passenger['ticketNumber'] ?? '');
        const passengerType = String(passenger['passengerType'] ?? '');
        const flightId = String(passenger['flightId'] ?? '');
        csvRows.push([
          this.escapeCsv(name),
          this.escapeCsv(seat),
          this.escapeCsv(ticket),
          this.escapeCsv(passengerType),
          this.escapeCsv(flightId)
        ].join(','));
      });

      const content = csvRows.join('\n');
      const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      const today = new Date().toISOString().slice(0, 10);
      anchor.setAttribute('href', url);
      anchor.setAttribute('download', `passengers_export_${today}.csv`);
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      this.actionErrorMessage = 'Unable to export passengers right now.';
      this.cdr.markForCheck();
    }
  }

  getFlightLoadPercent(flight: ManagedFlight): number {
    const total = Number(flight.totalSeats ?? 0);
    const available = Number(flight.availableSeats ?? 0);
    if (!total || total <= 0) {
      return 0;
    }
    const booked = Math.max(0, total - available);
    return Math.round((booked / total) * 100);
  }

  getFlightLoadClass(flight: ManagedFlight): string {
    const percent = this.getFlightLoadPercent(flight);
    if (percent <= 60) {
      return 'seat-fill-low';
    }
    if (percent <= 85) {
      return 'seat-fill-mid';
    }
    return 'seat-fill-high';
  }

  getScheduleDelayText(flight: ManagedFlight): string {
    if (String(flight.status).toUpperCase() === 'CANCELLED') {
      return 'Cancelled';
    }
    const original = this.delayMetaByFlightId.get(flight.flightId);
    if (String(flight.status).toUpperCase() !== 'DELAYED' || !original) {
      return '—';
    }

    const originalDate = new Date(original);
    const nextDate = new Date(flight.departureTime);
    if (Number.isNaN(originalDate.getTime()) || Number.isNaN(nextDate.getTime())) {
      return 'Delayed';
    }

    const minutes = Math.max(0, Math.round((nextDate.getTime() - originalDate.getTime()) / 60000));
    return `+${minutes} min`;
  }

  get selectedFlightSeatSummaryPercent(): number {
    if (!this.selectedFlight) {
      return 0;
    }
    return this.getFlightLoadPercent(this.selectedFlight);
  }

  editFlight(flight: ManagedFlight): void {
    this.editingFlightId = flight.flightId;
    this.flightForm.patchValue({
      flightNumber: flight.flightNumber,
      airlineId: flight.airlineId,
      originAirportCode: flight.originAirportCode,
      destinationAirportCode: flight.destinationAirportCode,
      departureTime: this.toDateTimeLocalValue(flight.departureTime),
      arrivalTime: this.toDateTimeLocalValue(flight.arrivalTime),
      durationMinutes: flight.durationMinutes,
      numberOfStops: flight.numberOfStops,
      viaAirportCode: flight.viaAirportCode ?? '',
      status: (flight.status as ManagedFlightRequest['status']) ?? 'ON_TIME',
      aircraftType: flight.aircraftType,
      totalSeats: flight.totalSeats,
      availableSeats: flight.availableSeats,
      basePrice: flight.basePrice
    });
  }

  duplicateFlight(flight: ManagedFlight): void {
    this.editingFlightId = null;
    this.flightForm.patchValue({
      flightNumber: '',
      airlineId: flight.airlineId,
      originAirportCode: flight.originAirportCode,
      destinationAirportCode: flight.destinationAirportCode,
      departureTime: this.toDateTimeLocalValue(flight.departureTime),
      arrivalTime: this.toDateTimeLocalValue(flight.arrivalTime),
      durationMinutes: flight.durationMinutes,
      numberOfStops: flight.numberOfStops,
      viaAirportCode: flight.viaAirportCode ?? '',
      status: (flight.status as ManagedFlightRequest['status']) ?? 'ON_TIME',
      aircraftType: flight.aircraftType,
      totalSeats: flight.totalSeats,
      availableSeats: flight.availableSeats,
      basePrice: flight.basePrice
    });
    this.duplicateFlightBanner = `Duplicating ${flight.flightNumber} — enter a new flight number to save.`;
    this.statusMessage = this.duplicateFlightBanner;
    this.cdr.markForCheck();
  }

  resetFlightForm(): void {
    this.editingFlightId = null;
    this.duplicateFlightBanner = '';
    this.flightForm.reset({
      flightNumber: '',
      airlineId: this.staffAirlineMapping?.airlineId ?? 0,
      originAirportCode: '',
      destinationAirportCode: '',
      departureTime: '',
      arrivalTime: '',
      durationMinutes: 60,
      numberOfStops: 0,
      viaAirportCode: '',
      status: 'ON_TIME',
      aircraftType: 'Airbus A320',
      totalSeats: 180,
      availableSeats: 180,
      basePrice: 3000
    });
  }

  saveFlight(): void {
    if (this.flightForm.invalid) {
      this.flightForm.markAllAsTouched();
      return;
    }

    const raw = this.flightForm.getRawValue();
    const request: ManagedFlightRequest = {
      flightNumber: String(raw.flightNumber ?? '').trim().toUpperCase(),
      airlineId: this.isStaff
        ? Number(this.staffAirlineMapping?.airlineId ?? 0)
        : Number(raw.airlineId ?? 0),
      originAirportCode: String(raw.originAirportCode ?? '').trim().toUpperCase(),
      destinationAirportCode: String(raw.destinationAirportCode ?? '').trim().toUpperCase(),
      departureTime: String(raw.departureTime ?? '').trim(),
      arrivalTime: String(raw.arrivalTime ?? '').trim(),
      durationMinutes: Number(raw.durationMinutes ?? 0),
      numberOfStops: Number(raw.numberOfStops ?? 0),
      viaAirportCode: String(raw.viaAirportCode ?? '').trim().toUpperCase() || undefined,
      status: (raw.status ?? 'ON_TIME') as ManagedFlightRequest['status'],
      aircraftType: String(raw.aircraftType ?? '').trim(),
      totalSeats: Number(raw.totalSeats ?? 0),
      availableSeats: Number(raw.availableSeats ?? 0),
      basePrice: Number(raw.basePrice ?? 0)
    };

    this.isSaving = true;
    const save$ = this.editingFlightId
      ? this.adminApiService.updateManagedFlight(this.editingFlightId, request)
      : this.adminApiService.createManagedFlight(request);

    save$.subscribe({
      next: () => {
        this.statusMessage = this.editingFlightId ? 'Flight updated successfully.' : 'Flight created successfully.';
        this.isSaving = false;
        this.resetFlightForm();
        this.loadFlightOperationsData();
      },
      error: () => {
        this.statusMessage = 'Unable to save flight right now.';
        this.isSaving = false;
        this.cdr.markForCheck();
      }
    });
  }

  openDelayModal(flight: ManagedFlight, triggerEvent?: Event): void {
    this.captureOverlayTrigger(triggerEvent);
    this.delayTargetFlight = flight;
    this.flightDelayForm.reset({
      delayReason: '',
      newEstimatedDepartureTime: this.toDateTimeLocalValue(flight.departureTime),
      internalNotes: ''
    });
    this.actionErrorMessage = '';
    this.isDelayModalOpen = true;
    this.focusFirstInteractive('.modal-card');
  }

  closeDelayModal(): void {
    this.isDelayModalOpen = false;
    this.delayTargetFlight = undefined;
    this.actionErrorMessage = '';
    this.restoreOverlayTriggerFocus();
  }

  submitFlightDelay(): void {
    if (!this.delayTargetFlight) {
      return;
    }
    if (this.flightDelayForm.invalid) {
      this.flightDelayForm.markAllAsTouched();
      return;
    }

    const form = this.flightDelayForm.getRawValue();
    const nextDeparture = String(form.newEstimatedDepartureTime ?? '').trim();
    const originalDate = new Date(this.delayTargetFlight.departureTime);
    const nextDate = new Date(nextDeparture);
    if (Number.isNaN(nextDate.getTime()) || Number.isNaN(originalDate.getTime()) || nextDate.getTime() <= originalDate.getTime()) {
      this.actionErrorMessage = 'New estimated departure must be after original departure time.';
      return;
    }

    const payload: DelayManagedFlightRequest = {
      delayReason: String(form.delayReason ?? '').trim(),
      newEstimatedDepartureTime: nextDeparture,
      internalNotes: String(form.internalNotes ?? '').trim()
    };

    this.isActionRunning = true;
    this.adminApiService.delayManagedFlight(this.delayTargetFlight.flightId, payload).subscribe({
      next: () => {
        this.delayMetaByFlightId.set(this.delayTargetFlight!.flightId, this.delayTargetFlight!.departureTime);
        this.statusMessage = `Flight ${this.delayTargetFlight!.flightNumber} marked as delayed. Passengers will be notified.`;
        this.isActionRunning = false;
        this.closeDelayModal();
        this.loadFlightOperationsData();
      },
      error: () => {
        this.actionErrorMessage = 'Unable to mark flight as delayed right now.';
        this.isActionRunning = false;
        this.cdr.markForCheck();
      }
    });
  }

  openCancelFlightModal(flight: ManagedFlight, triggerEvent?: Event): void {
    this.captureOverlayTrigger(triggerEvent);
    this.cancelFlightTarget = flight;
    this.actionErrorMessage = '';
    this.cancelFlightAffectedBookings = this.managedOrAllBookings.filter((booking) => {
      const linkedFlightId = Number(booking['flightId'] ?? NaN);
      const status = String(booking['status'] ?? '').toUpperCase();
      return linkedFlightId === flight.flightId && status === 'CONFIRMED';
    }).length;
    this.isCancelFlightModalOpen = true;
    this.focusFirstInteractive('.modal-card');
  }

  closeCancelFlightModal(): void {
    this.isCancelFlightModalOpen = false;
    this.cancelFlightTarget = undefined;
    this.cancelFlightAffectedBookings = 0;
    this.actionErrorMessage = '';
    this.restoreOverlayTriggerFocus();
  }

  confirmCancelFlight(): void {
    if (!this.cancelFlightTarget) {
      return;
    }

    this.isActionRunning = true;
    this.adminApiService.cancelManagedFlight(this.cancelFlightTarget.flightId).subscribe({
      next: (response) => {
        const result = response as unknown as Record<string, unknown>;
        const affected = Number(result['affectedBookings'] ?? this.cancelFlightAffectedBookings ?? 0);
        this.statusMessage = `Flight ${this.cancelFlightTarget!.flightNumber} cancelled. All ${affected} affected bookings have been cancelled and passengers notified.`;
        this.isActionRunning = false;
        this.closeCancelFlightModal();
        this.loadFlightOperationsData();
      },
      error: () => {
        this.actionErrorMessage = 'Unable to cancel this flight.';
        this.isActionRunning = false;
        this.cdr.markForCheck();
      }
    });
  }

  cancelFlight(flightId: number, triggerEvent?: Event): void {
    const target = this.flights.find((flight) => flight.flightId === flightId);
    if (!target) {
      return;
    }
    this.openCancelFlightModal(target, triggerEvent);
  }

  deleteFlight(flightId: number): void {
    if (!this.isAdmin) {
      return;
    }

    this.adminApiService.deleteManagedFlight(flightId).subscribe({
      next: () => {
        this.statusMessage = 'Flight deleted successfully.';
        this.loadFlightOperationsData();
      },
      error: () => {
        this.statusMessage = 'Unable to delete this flight.';
        this.cdr.markForCheck();
      }
    });
  }

  openFlightDetails(flight: ManagedFlight, triggerEvent?: Event): void {
    this.captureOverlayTrigger(triggerEvent);
    this.selectedFlight = flight;
    this.selectedFlightBookings = [];
    this.selectedFlightPassengers = [];
    this.isFlightDrawerOpen = true;
    this.focusFirstInteractive('.detail-drawer');

    forkJoin({
      bookings: this.adminApiService.getBookingsByFlight(flight.flightId).pipe(catchError(() => of([]))),
      passengers: this.adminApiService.getPassengersByFlight(flight.flightId).pipe(catchError(() => of([])))
    }).subscribe({
      next: ({ bookings, passengers }) => {
        this.selectedFlightBookings = bookings;
        this.selectedFlightPassengers = passengers;
        this.cdr.markForCheck();
      }
    });
  }

  clearFlightDetails(): void {
    this.selectedFlight = undefined;
    this.selectedFlightBookings = [];
    this.selectedFlightPassengers = [];
    this.isFlightDrawerOpen = false;
    this.restoreOverlayTriggerFocus();
  }

  openCancelBookingModal(booking: Record<string, unknown>, triggerEvent?: Event): void {
    this.captureOverlayTrigger(triggerEvent);
    this.cancelBookingTarget = booking;
    this.bookingCancelForm.reset({ reason: 'Passenger Request' });
    this.actionErrorMessage = '';
    this.isCancelBookingModalOpen = true;
    this.focusFirstInteractive('.modal-card');
  }

  closeCancelBookingModal(): void {
    this.cancelBookingTarget = undefined;
    this.actionErrorMessage = '';
    this.isCancelBookingModalOpen = false;
    this.restoreOverlayTriggerFocus();
  }

  confirmCancelBooking(): void {
    if (!this.cancelBookingTarget) {
      return;
    }

    const bookingId = String(this.cancelBookingTarget['bookingId'] ?? '').trim();
    if (!bookingId) {
      this.actionErrorMessage = 'Booking ID is missing.';
      return;
    }

    const request: CancelManagedBookingRequest = {
      bookingId,
      reason: String(this.bookingCancelForm.getRawValue().reason ?? '').trim()
    };

    this.isActionRunning = true;
    this.adminApiService.cancelManagedBooking(request).subscribe({
      next: () => {
        const pnrCode = String(this.cancelBookingTarget?.['pnrCode'] ?? bookingId);
        this.statusMessage = `Booking ${pnrCode} cancelled successfully.`;
        this.isActionRunning = false;
        this.closeCancelBookingModal();
        this.loadFlightOperationsData();
      },
      error: () => {
        this.actionErrorMessage = 'Unable to cancel this booking right now.';
        this.isActionRunning = false;
        this.cdr.markForCheck();
      }
    });
  }

  updateFlightStatusDirect(flight: ManagedFlight, status: ManagedFlightRequest['status']): void {
    this.isActionRunning = true;
    const request = this.toManagedFlightRequest(flight, { status });
    this.adminApiService.updateManagedFlight(flight.flightId, request).subscribe({
      next: () => {
        this.statusMessage = `Flight ${flight.flightNumber} status updated to ${status}.`;
        this.isActionRunning = false;
        this.loadFlightOperationsData();
      },
      error: () => {
        this.actionErrorMessage = 'Unable to update flight status right now.';
        this.isActionRunning = false;
        this.cdr.markForCheck();
      }
    });
  }

  editAirline(airline: AirlineRecord): void {
    if (!this.isAdmin) {
      return;
    }

    this.editingAirlineId = airline.airlineId ?? null;
    this.airlineForm.patchValue({
      name: airline.name,
      iataCode: airline.iataCode,
      country: airline.country,
      active: this.isAirlineActive(airline)
    });
  }

  resetAirlineForm(): void {
    this.editingAirlineId = null;
    this.airlineForm.reset({
      name: '',
      iataCode: '',
      country: '',
      active: true
    });
  }

  saveAirline(): void {
    if (!this.isAdmin) {
      return;
    }

    if (this.airlineForm.invalid) {
      this.airlineForm.markAllAsTouched();
      return;
    }

    this.isSaving = true;
    const value = this.airlineForm.getRawValue();
    const request: AirlineRecord = {
      name: String(value.name ?? '').trim(),
      iataCode: String(value.iataCode ?? '').trim().toUpperCase(),
      country: String(value.country ?? '').trim(),
      active: Boolean(value.active)
    };

    const save$ = this.editingAirlineId
      ? this.airlineAirportApiService.updateAirline(this.editingAirlineId, request)
      : this.airlineAirportApiService.createAirline(request);

    save$.subscribe({
      next: () => {
        this.statusMessage = this.editingAirlineId ? 'Airline updated successfully.' : 'Airline created successfully.';
        this.isSaving = false;
        this.resetAirlineForm();
        this.loadData();
      },
      error: () => {
        this.statusMessage = 'Unable to save airline right now.';
        this.isSaving = false;
        this.cdr.markForCheck();
      }
    });
  }

  deleteAirline(airlineId: number | undefined): void {
    if (!this.isAdmin || !airlineId) {
      return;
    }

    this.airlineAirportApiService.deleteAirline(airlineId).subscribe({
      next: () => {
        this.statusMessage = 'Airline removed successfully.';
        this.loadData();
      },
      error: () => {
        this.statusMessage = 'Unable to delete airline.';
        this.cdr.markForCheck();
      }
    });
  }

  editAirport(airport: AirportRecord): void {
    if (!this.isAdmin) {
      return;
    }

    this.editingAirportId = airport.airportId ?? null;
    this.airportForm.patchValue({
      name: airport.name,
      iataCode: airport.iataCode,
      city: airport.city,
      country: airport.country,
      timezone: airport.timezone,
      latitude: airport.latitude,
      longitude: airport.longitude
    });
  }

  resetAirportForm(): void {
    this.editingAirportId = null;
    this.airportForm.reset({
      name: '',
      iataCode: '',
      city: '',
      country: '',
      timezone: '',
      latitude: 0,
      longitude: 0
    });
  }

  saveAirport(): void {
    if (!this.isAdmin) {
      return;
    }

    if (this.airportForm.invalid) {
      this.airportForm.markAllAsTouched();
      return;
    }

    this.isSaving = true;
    const value = this.airportForm.getRawValue();
    const request: AirportRecord = {
      name: String(value.name ?? '').trim(),
      iataCode: String(value.iataCode ?? '').trim().toUpperCase(),
      city: String(value.city ?? '').trim(),
      country: String(value.country ?? '').trim(),
      timezone: String(value.timezone ?? '').trim(),
      latitude: Number(value.latitude ?? 0),
      longitude: Number(value.longitude ?? 0)
    };

    const save$ = this.editingAirportId
      ? this.airlineAirportApiService.updateAirport(this.editingAirportId, request)
      : this.airlineAirportApiService.createAirport(request);

    save$.subscribe({
      next: () => {
        this.statusMessage = this.editingAirportId ? 'Airport updated successfully.' : 'Airport created successfully.';
        this.isSaving = false;
        this.resetAirportForm();
        this.loadData();
      },
      error: () => {
        this.statusMessage = 'Unable to save airport right now.';
        this.isSaving = false;
        this.cdr.markForCheck();
      }
    });
  }

  deleteAirport(airportId: number | undefined): void {
    if (!this.isAdmin || !airportId) {
      return;
    }

    this.airlineAirportApiService.deleteAirport(airportId).subscribe({
      next: () => {
        this.statusMessage = 'Airport removed successfully.';
        this.loadData();
      },
      error: () => {
        this.statusMessage = 'Unable to delete airport.';
        this.cdr.markForCheck();
      }
    });
  }

  searchAirports(): void {
    const query = this.airportSearchTerm.trim();
    if (query.length < 2) {
      this.airportSearchResults = [];
      return;
    }

    this.isAirportSearchLoading = true;
    this.airlineAirportApiService.searchAirports(query).pipe(
      catchError(() => of([]))
    ).subscribe((results) => {
      this.airportSearchResults = results;
      this.isAirportSearchLoading = false;
      this.cdr.markForCheck();
    });
  }

  retryLoadData(): void {
    this.loadData();
  }

  get bookingsForSelectedFlight(): number {
    return this.selectedFlightBookings.length;
  }

  private loadData(): void {
    this.isLoading = true;
    this.errorMessage = '';

    const catalog$ = forkJoin({
      airlines: this.airlineAirportApiService.getAirlines().pipe(catchError(() => of([]))),
      airports: this.airlineAirportApiService.getAirports().pipe(catchError(() => of([])))
    });

    const staffMapping$ = this.isStaff && this.userId
      ? this.adminApiService.getStaffAirline(this.userId).pipe(catchError(() => of(undefined)))
      : of(undefined);

    const operations$ = forkJoin({
      dashboard: this.adminApiService.getFlightDashboard().pipe(catchError(() => of(undefined))),
      flights: this.adminApiService.getManagedFlights().pipe(catchError(() => of([]))),
      managedBookings: this.adminApiService.getManagedBookings().pipe(catchError(() => of([]))),
      managedPassengers: this.isStaff
        ? this.adminApiService.getManagedPassengers().pipe(catchError(() => of([])))
        : this.adminApiService.getManagedPassengers().pipe(catchError(() => of([])))
    });

    if (this.isAdmin) {
      forkJoin({
        analytics: this.adminApiService.getAnalytics().pipe(catchError(() => of(undefined))),
        users: this.adminApiService.getUsers().pipe(catchError(() => of([]))),
        bookings: this.adminApiService.getBookings().pipe(catchError(() => of([]))),
        payments: this.adminApiService.getPayments().pipe(catchError(() => of([]))),
        catalog: catalog$,
        operations: operations$
      }).subscribe({
        next: ({ analytics, users, bookings, payments, catalog, operations }) => {
          this.analytics = analytics;
          this.users = users;
          this.bookings = bookings;
          this.managedBookings = operations.managedBookings;
          this.managedPassengers = operations.managedPassengers;
          this.payments = payments;
          this.airlines = catalog.airlines;
          this.airports = catalog.airports;
          this.flightDashboard = operations.dashboard;
          this.flights = operations.flights;
          this.seedDelayMeta();
          this.resetFlightForm();
          this.isLoading = false;
          this.cdr.markForCheck();
        },
        error: () => {
          this.errorMessage = 'Failed to load operations data. Please check your connection and try again.';
          this.isLoading = false;
          this.cdr.markForCheck();
        }
      });
      return;
    }

    forkJoin({
      staffMapping: staffMapping$,
      catalog: catalog$,
      operations: operations$
    }).subscribe({
      next: ({ staffMapping, catalog, operations }) => {
        this.staffAirlineMapping = staffMapping;
        this.airlines = catalog.airlines;
        this.airports = catalog.airports;
        this.flightDashboard = operations.dashboard;
        this.flights = operations.flights;
        this.managedBookings = operations.managedBookings;
        this.managedPassengers = operations.managedPassengers;
        this.seedDelayMeta();

        if (this.staffAirlineMapping?.airlineId) {
          this.flightForm.controls.airlineId.setValue(this.staffAirlineMapping.airlineId);
          this.flightForm.controls.airlineId.disable();
        }

        this.resetFlightForm();
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.errorMessage = 'Failed to load operations data. Please check your connection and try again.';
        this.isLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  private loadFlightOperationsData(): void {
    forkJoin({
      dashboard: this.adminApiService.getFlightDashboard().pipe(catchError(() => of(undefined))),
      flights: this.adminApiService.getManagedFlights().pipe(catchError(() => of([]))),
      managedBookings: this.adminApiService.getManagedBookings().pipe(catchError(() => of([]))),
      managedPassengers: this.adminApiService.getManagedPassengers().pipe(catchError(() => of([])))
    }).subscribe({
      next: ({ dashboard, flights, managedBookings, managedPassengers }) => {
        this.flightDashboard = dashboard;
        this.flights = flights;
        this.managedBookings = managedBookings;
        this.managedPassengers = managedPassengers;
        this.seedDelayMeta();
        this.clearFlightDetails();
        this.cdr.markForCheck();
      },
      error: () => {
        this.actionErrorMessage = 'Unable to refresh operations data right now.';
        this.cdr.markForCheck();
      }
    });
  }

  private seedDelayMeta(): void {
    this.delayMetaByFlightId.clear();
    this.flights.forEach((flight) => {
      if (String(flight.status).toUpperCase() === 'DELAYED') {
        this.delayMetaByFlightId.set(flight.flightId, flight.departureTime);
      }
    });
  }

  private toManagedFlightRequest(flight: ManagedFlight, patch?: Partial<ManagedFlightRequest>): ManagedFlightRequest {
    const next: ManagedFlightRequest = {
      flightNumber: String(flight.flightNumber ?? '').trim().toUpperCase(),
      airlineId: Number(flight.airlineId ?? 0),
      originAirportCode: String(flight.originAirportCode ?? '').trim().toUpperCase(),
      destinationAirportCode: String(flight.destinationAirportCode ?? '').trim().toUpperCase(),
      departureTime: String(flight.departureTime ?? ''),
      arrivalTime: String(flight.arrivalTime ?? ''),
      durationMinutes: Number(flight.durationMinutes ?? 0),
      numberOfStops: Number(flight.numberOfStops ?? 0),
      viaAirportCode: String(flight.viaAirportCode ?? '').trim().toUpperCase() || undefined,
      status: (String(flight.status ?? 'ON_TIME').toUpperCase() as ManagedFlightRequest['status']),
      aircraftType: String(flight.aircraftType ?? '').trim(),
      totalSeats: Number(flight.totalSeats ?? 0),
      availableSeats: Number(flight.availableSeats ?? 0),
      basePrice: Number(flight.basePrice ?? 0)
    };

    return {
      ...next,
      ...(patch ?? {})
    };
  }

  private paginate<T>(rows: T[], state: PaginationState): T[] {
    const start = (state.page - 1) * state.pageSize;
    return rows.slice(start, start + state.pageSize);
  }

  private runDebounced(task: () => void, key: 'flight' | 'schedule' | 'booking' | 'passenger'): void {
    const timers: Record<typeof key, ReturnType<typeof setTimeout> | null> = {
      flight: this.flightSearchTimer,
      schedule: this.scheduleSearchTimer,
      booking: this.bookingSearchTimer,
      passenger: this.passengerSearchTimer
    };

    const existing = timers[key];
    if (existing) {
      clearTimeout(existing);
    }

    const timer = setTimeout(() => {
      task();
      this.cdr.markForCheck();
    }, 300);

    if (key === 'flight') {
      this.flightSearchTimer = timer;
    } else if (key === 'schedule') {
      this.scheduleSearchTimer = timer;
    } else if (key === 'booking') {
      this.bookingSearchTimer = timer;
    } else {
      this.passengerSearchTimer = timer;
    }
  }

  private clearDebounceTimers(): void {
    if (this.flightSearchTimer) {
      clearTimeout(this.flightSearchTimer);
      this.flightSearchTimer = null;
    }
    if (this.scheduleSearchTimer) {
      clearTimeout(this.scheduleSearchTimer);
      this.scheduleSearchTimer = null;
    }
    if (this.bookingSearchTimer) {
      clearTimeout(this.bookingSearchTimer);
      this.bookingSearchTimer = null;
    }
    if (this.passengerSearchTimer) {
      clearTimeout(this.passengerSearchTimer);
      this.passengerSearchTimer = null;
    }
  }

  private escapeCsv(value: string): string {
    const normalized = String(value ?? '');
    if (normalized.includes(',') || normalized.includes('"') || normalized.includes('\n')) {
      return `"${normalized.replace(/"/g, '""')}"`;
    }
    return normalized;
  }

  private isAirlineActive(airline: AirlineRecord): boolean {
    return Boolean(airline.active ?? airline.isActive);
  }

  private toDateTimeLocalValue(value: string): string {
    if (!value) {
      return '';
    }
    return value.slice(0, 16);
  }

  private formatNumber(value: number): string {
    const numericValue = Number(value ?? 0);
    if (!Number.isFinite(numericValue)) {
      return '--';
    }
    return new Intl.NumberFormat('en-IN').format(numericValue);
  }

  private formatCurrencyCompact(value: number): string {
    const numericValue = Number(value ?? 0);
    if (!Number.isFinite(numericValue)) {
      return '--';
    }
    const formatter = new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 1,
      notation: 'compact'
    });
    return formatter.format(numericValue);
  }

  private toBookingRow(entry: Record<string, unknown>): DashboardBookingRow {
    const bookingId = String(entry['bookingId'] ?? '').trim();
    const pnrCode = String(entry['pnrCode'] ?? '').trim();
    const code = pnrCode || bookingId;

    const firstName = String(entry['firstName'] ?? '').trim();
    const lastName = String(entry['lastName'] ?? '').trim();
    const fullName = String(entry['fullName'] ?? entry['passengerName'] ?? '').trim();
    const passenger = fullName || `${firstName} ${lastName}`.trim() || `User #${entry['userId'] ?? '--'}`;

    const flightIdRaw = entry['flightId'];
    const flightId = Number(flightIdRaw ?? NaN);
    const flight = this.findFlightById(flightId);
    const route = flight
      ? `${flight.originAirportCode}-${flight.destinationAirportCode}`
      : String(entry['route'] ?? 'Route pending');

    const bookedAt = String(entry['bookedAt'] ?? entry['createdAt'] ?? '').trim();
    const date = bookedAt ? this.formatDate(bookedAt) : '--';
    const status = String(entry['status'] ?? 'PENDING').toUpperCase();

    return { code, passenger, route, date, status };
  }

  private findFlightById(flightId: number): ManagedFlight | undefined {
    if (!Number.isFinite(flightId) || flightId <= 0) {
      return undefined;
    }
    return this.flights.find((flight) => Number(flight.flightId) === flightId);
  }

  private formatDate(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return '--';
    }
    return new Intl.DateTimeFormat('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric'
    }).format(date);
  }

  private formatDateTime(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return '--';
    }
    return new Intl.DateTimeFormat('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    }).format(date);
  }

  private toSvgPoints(values: number[]): string {
    if (!values.length) {
      return '';
    }
    const width = 320;
    const height = 160;
    const paddingX = 24;
    const paddingY = 18;
    const maxValue = Math.max(...values, 1);
    const usableWidth = width - paddingX * 2;
    const usableHeight = height - paddingY * 2;

    return values
      .map((value, index) => {
        const x = paddingX + (values.length === 1 ? 0 : (usableWidth * index) / (values.length - 1));
        const y = height - paddingY - (Math.max(value, 0) / maxValue) * usableHeight;
        return `${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .join(' ');
  }

  private toSvgArea(values: number[]): string {
    if (!values.length) {
      return '';
    }
    const width = 320;
    const height = 160;
    const paddingX = 24;
    const baseline = height - 18;
    const points = this.toSvgPoints(values);
    const lastX = (width - paddingX).toFixed(1);
    const firstX = paddingX.toFixed(1);
    return `${firstX},${baseline} ${points} ${lastX},${baseline}`;
  }

  private captureOverlayTrigger(triggerEvent?: Event): void {
    const source = triggerEvent?.currentTarget;
    if (source instanceof HTMLElement) {
      this.overlayTriggerElement = source;
      return;
    }
    const active = document.activeElement;
    this.overlayTriggerElement = active instanceof HTMLElement ? active : null;
  }

  private restoreOverlayTriggerFocus(): void {
    const source = this.overlayTriggerElement;
    this.overlayTriggerElement = null;
    if (!source) {
      return;
    }
    setTimeout(() => source.focus(), 0);
  }

  private focusFirstInteractive(containerSelector: string): void {
    setTimeout(() => {
      const container = this.elementRef.nativeElement.querySelector(containerSelector);
      if (!(container instanceof HTMLElement)) {
        return;
      }
      const firstFocusable = container.querySelector<HTMLElement>(
        'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
      );
      if (firstFocusable) {
        firstFocusable.focus();
      } else {
        container.focus();
      }
    }, 0);
  }

  private trapFocusWithin(container: HTMLElement, event: KeyboardEvent): void {
    const focusableElements = Array.from(
      container.querySelectorAll<HTMLElement>(
        'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
      )
    ).filter((element) => !element.hasAttribute('hidden') && element.offsetParent !== null);

    if (!focusableElements.length) {
      return;
    }

    const first = focusableElements[0];
    const last = focusableElements[focusableElements.length - 1];
    const active = document.activeElement as HTMLElement | null;

    if (event.shiftKey && active === first) {
      event.preventDefault();
      last.focus();
      return;
    }

    if (!event.shiftKey && active === last) {
      event.preventDefault();
      first.focus();
    }
  }

  private readonly delayMetaByFlightId = new Map<number, string>();

  private applyRouteData(routeData: Record<string, unknown>): void {
    this.panel = routeData['panel'] === 'airline' ? 'airline' : 'admin';
    this.section = (routeData['section'] as typeof this.section) ?? 'dashboard';
    this.pageTitle = String(routeData['title'] ?? 'Dashboard');
    this.pageSubtitle = String(routeData['subtitle'] ?? '');
    this.statusMessage = '';
    this.errorMessage = '';
    this.actionErrorMessage = '';
  }
}
