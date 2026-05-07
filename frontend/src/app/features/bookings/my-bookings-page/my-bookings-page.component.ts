import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, finalize, of } from 'rxjs';

import { BookingResponse } from '../../../core/models/booking.models';
import { FlightResponse } from '../../../core/models/flight.models';
import { PassengerResponse } from '../../../core/models/passenger.models';
import { BookingApiService } from '../../../core/services/booking-api.service';
import { FlightApiService } from '../../../core/services/flight-api.service';
import { PassengerApiService } from '../../../core/services/passenger-api.service';
import { TokenStorageService } from '../../../core/services/token-storage.service';
import { FinalTicketComponent } from '../final-ticket/final-ticket.component';
import { TicketCardComponent } from '../ticket-card/ticket-card.component';

interface BookingTicketViewModel {
  booking: BookingResponse;
  passengers: PassengerResponse[];
  flight: FlightResponse | null;
}

@Component({
  selector: 'app-my-bookings-page',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TicketCardComponent, FinalTicketComponent],
  templateUrl: './my-bookings-page.component.html',
  styleUrl: './my-bookings-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MyBookingsPageComponent implements OnInit {
  private static readonly ALL_FILTER = 'ALL';
  private static readonly STALE_PENDING_MINUTES = 20;
  bookingTickets: BookingTicketViewModel[] = [];
  selectedStatus = MyBookingsPageComponent.ALL_FILTER;
  selectedMonth = MyBookingsPageComponent.ALL_FILTER;
  statusFilters: string[] = [MyBookingsPageComponent.ALL_FILTER];
  monthFilters: Array<{ value: string; label: string }> = [{ value: MyBookingsPageComponent.ALL_FILTER, label: 'All months' }];
  selectedBooking: BookingResponse | null = null;
  selectedPassengers: PassengerResponse[] = [];
  selectedPassenger: PassengerResponse | null = null;
  selectedFlight: FlightResponse | null = null;
  isTicketModalOpen = false;
  isDownloadingPdf = false;
  isLoading = false;
  errorMessage = '';
  hiddenBookingIds = new Set<string>();
  private userId: number | null = null;
  @ViewChild('ticketPrintable') ticketPrintableRef?: ElementRef<HTMLElement>;

  constructor(
    private readonly bookingApiService: BookingApiService,
    private readonly flightApiService: FlightApiService,
    private readonly passengerApiService: PassengerApiService,
    private readonly tokenStorageService: TokenStorageService,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    const userId = this.tokenStorageService.getUserId();
    if (!userId) {
      this.errorMessage = 'Login required to view your bookings.';
      return;
    }
    this.userId = userId;
    this.hiddenBookingIds = this.readHiddenBookingIds(userId);

    this.isLoading = true;
    this.bookingApiService.getBookingsByUser(userId)
      .pipe(
        finalize(() => {
          this.isLoading = false;
          this.cdr.markForCheck();
        })
      )
      .subscribe({
        next: (bookings) => {
          this.bookingTickets = bookings
            .map((booking) => ({
              booking,
              passengers: [],
              flight: null
            }))
            .sort((left, right) => this.toDate(right.booking.bookedAt).getTime() - this.toDate(left.booking.bookedAt).getTime());
          this.statusFilters = this.buildStatusFilters(this.bookingTickets);
          this.monthFilters = this.buildMonthFilters(this.bookingTickets);
          this.loadTicketDetailsInBackground();
        },
        error: () => {
          this.errorMessage = 'Unable to fetch your bookings right now.';
        }
      });
  }

  get visibleBookingTickets(): BookingTicketViewModel[] {
    return this.bookingTickets.filter((item) => {
      if (this.hiddenBookingIds.has(item.booking.bookingId)) {
        return false;
      }

      if (this.isGarbagePendingBooking(item)) {
        return false;
      }

      if (this.selectedStatus !== MyBookingsPageComponent.ALL_FILTER && item.booking.status !== this.selectedStatus) {
        return false;
      }

      if (this.selectedMonth !== MyBookingsPageComponent.ALL_FILTER) {
        const monthValue = this.monthValue(item.booking.bookedAt);
        if (monthValue !== this.selectedMonth) {
          return false;
        }
      }

      return true;
    });
  }

  openTicketModal(booking: BookingResponse, passengers: PassengerResponse[], flight: FlightResponse | null): void {
    this.selectedBooking = booking;
    this.selectedPassengers = passengers;
    this.selectedPassenger = passengers[0] ?? null;
    this.selectedFlight = flight;
    this.isTicketModalOpen = true;
  }

  closeTicketModal(): void {
    this.isTicketModalOpen = false;
    this.selectedBooking = null;
    this.selectedPassengers = [];
    this.selectedPassenger = null;
    this.selectedFlight = null;
    this.isDownloadingPdf = false;
  }

  removeBookingFromView(bookingId: string): void {
    this.hiddenBookingIds.add(bookingId);
    this.persistHiddenBookingIds();
  }

  resetFilters(): void {
    this.selectedStatus = MyBookingsPageComponent.ALL_FILTER;
    this.selectedMonth = MyBookingsPageComponent.ALL_FILTER;
    this.hiddenBookingIds.clear();
    this.persistHiddenBookingIds();
  }

  async downloadTicket(bookingId: string): Promise<void> {
    if (!bookingId || this.isDownloadingPdf || !this.selectedBooking) {
      return;
    }

    const ticketRoot = this.ticketPrintableRef?.nativeElement;
    if (!ticketRoot) {
      this.errorMessage = 'Ticket layout is not ready yet. Please try again.';
      this.cdr.markForCheck();
      return;
    }

    this.isDownloadingPdf = true;
    this.cdr.markForCheck();

    try {
      const [{ default: html2canvas }, { jsPDF }] = await Promise.all([
        import('html2canvas'),
        import('jspdf')
      ]);

      const canvas = await html2canvas(ticketRoot, {
        scale: 2,
        useCORS: true,
        backgroundColor: '#ffffff'
      });
      const imageData = canvas.toDataURL('image/png');

      const pdf = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });
      const pageWidth = pdf.internal.pageSize.getWidth();
      const pageHeight = pdf.internal.pageSize.getHeight();
      const margin = 10;
      const contentWidth = pageWidth - margin * 2;
      const contentHeight = (canvas.height * contentWidth) / canvas.width;

      const drawHeight = Math.min(contentHeight, pageHeight - margin * 2);
      pdf.addImage(imageData, 'PNG', margin, margin, contentWidth, drawHeight, undefined, 'FAST');
      pdf.save(`ticket-${bookingId}.pdf`);
    } catch {
      this.errorMessage = 'Unable to generate ticket PDF right now.';
    } finally {
      this.isDownloadingPdf = false;
      this.cdr.markForCheck();
    }
  }

  getPublicTicketUrl(bookingId: string): string {
    if (!bookingId) {
      return '';
    }

    const ticket = this.bookingTickets.find((item) => item.booking.bookingId === bookingId);
    const booking = ticket?.booking ?? this.selectedBooking;
    const flight = ticket?.flight ?? this.selectedFlight;
    const passenger = ticket?.passengers?.[0] ?? this.selectedPassenger;

    if (typeof window === 'undefined' || this.isPrivateOrLocalHost(window.location.hostname)) {
      return [
        'SkyBooker Boarding Pass',
        `PNR: ${String(booking?.pnrCode ?? 'N/A').trim() || 'N/A'}`,
        `Booking ID: ${bookingId}`,
        `Passenger: ${passenger ? `${passenger.title} ${passenger.firstName} ${passenger.lastName}`.trim() : 'Passenger details pending'}`,
        `Route: ${flight?.originAirportCode?.trim().toUpperCase() || '---'} -> ${flight?.destinationAirportCode?.trim().toUpperCase() || '---'}`,
        `Date: ${this.formatDate(flight?.departureTime || booking?.bookedAt)}`,
        `Time: ${this.formatTime(flight?.departureTime || booking?.bookedAt)}`,
        `Seat: ${passenger?.seatNumber || (booking?.seatIds?.length ? booking.seatIds.map((seatId) => `Seat ${seatId}`).join(', ') : 'Pending')}`,
        `Status: ${String(booking?.status ?? 'PENDING').trim().toUpperCase()}`,
        `Total Fare: INR ${Number(booking?.totalFare ?? 0).toFixed(2)}`
      ].join('\n');
    }

    return `${window.location.origin}/ticket/${bookingId}`;
  }

  private isPrivateOrLocalHost(hostname: string): boolean {
    const host = String(hostname ?? '').trim().toLowerCase();
    if (!host) {
      return true;
    }

    if (host === 'localhost' || host === '127.0.0.1' || host === '0.0.0.0' || host === '::1' || host.endsWith('.local')) {
      return true;
    }

    if (/^10\./.test(host) || /^192\.168\./.test(host)) {
      return true;
    }

    const match172 = host.match(/^172\.(\d{1,3})\./);
    if (match172) {
      const secondOctet = Number(match172[1]);
      if (secondOctet >= 16 && secondOctet <= 31) {
        return true;
      }
    }

    return false;
  }

  private formatDate(raw?: string): string {
    const date = this.toDate(raw ?? '');
    if (Number.isNaN(date.getTime())) {
      return 'Date pending';
    }
    return date.toLocaleDateString([], { day: 'numeric', month: 'short', year: 'numeric' });
  }

  private formatTime(raw?: string): string {
    const date = this.toDate(raw ?? '');
    if (Number.isNaN(date.getTime())) {
      return 'Time pending';
    }
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  private buildStatusFilters(items: BookingTicketViewModel[]): string[] {
    const statuses = new Set<string>();
    for (const item of items) {
      const status = String(item.booking.status ?? '').trim().toUpperCase();
      statuses.add(status || 'UNKNOWN');
    }
    return [MyBookingsPageComponent.ALL_FILTER, ...Array.from(statuses)];
  }

  private buildMonthFilters(items: BookingTicketViewModel[]): Array<{ value: string; label: string }> {
    const values = new Set<string>();
    for (const item of items) {
      values.add(this.monthValue(item.booking.bookedAt));
    }

    const monthValues = Array.from(values)
      .filter((value) => value !== 'unknown')
      .sort((left, right) => right.localeCompare(left));

    return [
      { value: MyBookingsPageComponent.ALL_FILTER, label: 'All months' },
      ...monthValues.map((value) => ({
        value,
        label: this.monthLabel(value)
      }))
    ];
  }

  private monthValue(raw: string): string {
    const date = this.toDate(raw);
    if (Number.isNaN(date.getTime())) {
      return 'unknown';
    }
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    return `${year}-${month}`;
  }

  private monthLabel(value: string): string {
    const [yearStr, monthStr] = value.split('-');
    const year = Number(yearStr);
    const month = Number(monthStr) - 1;
    if (!Number.isFinite(year) || !Number.isFinite(month) || month < 0 || month > 11) {
      return value;
    }
    return new Date(year, month, 1).toLocaleDateString([], { month: 'long', year: 'numeric' });
  }

  private toDate(raw: string): Date {
    if (!raw) {
      return new Date('');
    }
    if (/z$/i.test(raw) || /[+-]\d{2}:\d{2}$/.test(raw)) {
      return new Date(raw);
    }
    return new Date(`${raw}Z`);
  }

  private readHiddenBookingIds(userId: number): Set<string> {
    try {
      const raw = localStorage.getItem(this.hiddenStorageKey(userId));
      if (!raw) {
        return new Set<string>();
      }
      const parsed = JSON.parse(raw) as string[];
      return new Set(Array.isArray(parsed) ? parsed.filter((value) => !!value) : []);
    } catch {
      return new Set<string>();
    }
  }

  private persistHiddenBookingIds(): void {
    if (!this.userId) {
      return;
    }
    try {
      localStorage.setItem(this.hiddenStorageKey(this.userId), JSON.stringify(Array.from(this.hiddenBookingIds)));
    } catch {
      // Ignore persistence failure and keep runtime state.
    }
  }

  private hiddenStorageKey(userId: number): string {
    return `skybooker.hiddenBookings.${userId}`;
  }

  private loadTicketDetailsInBackground(): void {
    if (!this.bookingTickets.length) {
      return;
    }

    this.bookingTickets.forEach((item) => {
      const bookingId = item.booking.bookingId;

      this.passengerApiService.getPassengersByBooking(bookingId)
        .pipe(catchError(() => of([] as PassengerResponse[])))
        .subscribe((passengers) => {
          this.patchTicket(bookingId, { passengers });
          this.cleanupGarbagePendingBooking(bookingId);
        });

      this.flightApiService.getFlightById(item.booking.flightId)
        .pipe(catchError(() => of(null)))
        .subscribe((flight) => {
          this.patchTicket(bookingId, { flight: flight ?? null });
        });
    });
  }

  private patchTicket(bookingId: string, partial: Partial<BookingTicketViewModel>): void {
    const index = this.bookingTickets.findIndex((ticket) => ticket.booking.bookingId === bookingId);
    if (index < 0) {
      return;
    }

    const updated = {
      ...this.bookingTickets[index],
      ...partial
    };

    this.bookingTickets = [
      ...this.bookingTickets.slice(0, index),
      updated,
      ...this.bookingTickets.slice(index + 1)
    ];
    this.cdr.markForCheck();
  }

  private isGarbagePendingBooking(item: BookingTicketViewModel): boolean {
    const status = String(item.booking.status ?? '').trim().toUpperCase();
    if (status !== 'PENDING') {
      return false;
    }

    if (item.passengers.length > 0) {
      return false;
    }

    const bookedAt = this.toDate(item.booking.bookedAt);
    if (Number.isNaN(bookedAt.getTime())) {
      return true;
    }

    const ageMs = Date.now() - bookedAt.getTime();
    return ageMs >= MyBookingsPageComponent.STALE_PENDING_MINUTES * 60 * 1000;
  }

  private cleanupGarbagePendingBooking(bookingId: string): void {
    const ticket = this.bookingTickets.find((item) => item.booking.bookingId === bookingId);
    if (!ticket || !this.isGarbagePendingBooking(ticket)) {
      return;
    }

    this.bookingApiService.cancelBooking(bookingId)
      .pipe(catchError(() => of(null)))
      .subscribe((cancelled) => {
        if (cancelled?.bookingId) {
          this.patchTicket(bookingId, { booking: cancelled });
        }
        this.hiddenBookingIds.add(bookingId);
        this.persistHiddenBookingIds();
        this.cdr.markForCheck();
      });
  }
}
