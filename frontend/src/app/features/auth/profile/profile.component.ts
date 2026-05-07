import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { AuthApiService } from '../../../core/services/auth-api.service';
import { ProfileResponse } from '../../../core/models/auth.models';
import { BookingResponse } from '../../../core/models/booking.models';
import { FlightResponse } from '../../../core/models/flight.models';
import { BookingApiService } from '../../../core/services/booking-api.service';
import { FlightApiService } from '../../../core/services/flight-api.service';
import { AdminApiService } from '../../../core/services/admin-api.service';

interface BookingViewItem {
  booking: BookingResponse;
  flight?: FlightResponse;
}

interface ActivityItem {
  title: string;
  time: string;
}

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.css'
})
export class ProfileComponent implements OnInit {
  profile?: ProfileResponse;
  errorMessage = '';
  statusMessage = '';
  isSaving = false;
  isEditMode = false;
  showPasswordForm = false;
  selectedPhotoName = '';

  recentBookings: BookingViewItem[] = [];
  roleActivities: ActivityItem[] = [];

  savedPaymentPreview = '';
  managedAirlinesCount = 0;
  staffAssignedFlights = 0;
  staffDepartment = 'Operations';
  staffAirport = 'Not assigned';
  staffSupervisor = 'Not assigned';

  readonly visibleBookingsCount = 3;

  readonly profileForm = this.formBuilder.group({
    fullName: ['', [Validators.required, Validators.minLength(2)]],
    phone: ['', [Validators.required, Validators.pattern(/^\+?[0-9]{10,15}$/)]],
    passportNumber: ['', [Validators.required, Validators.pattern(/^[A-Z0-9]{6,12}$/)]],
    nationality: ['', [Validators.required, Validators.pattern(/^[A-Za-z ]{2,60}$/)]],
    dateOfBirth: [''],
    profilePhotoUrl: ['']
  });

  readonly passwordForm = this.formBuilder.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,20}$/)]]
  });

  constructor(
    private readonly authApiService: AuthApiService,
    private readonly bookingApiService: BookingApiService,
    private readonly flightApiService: FlightApiService,
    private readonly adminApiService: AdminApiService,
    private readonly formBuilder: FormBuilder,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.fetchProfile();
  }

  get isPassenger(): boolean {
    return this.profile?.role === 'PASSENGER';
  }

  get isAdmin(): boolean {
    return this.profile?.role === 'ADMIN';
  }

  get isAirlineStaff(): boolean {
    return this.profile?.role === 'AIRLINE_STAFF';
  }

  get profileSubtitle(): string {
    if (this.isAdmin) {
      return 'Platform Administrator';
    }
    if (this.isAirlineStaff) {
      return 'Airline Operations Staff';
    }
    return 'Frequent Traveler';
  }

  get totalBookings(): number {
    return this.recentBookings.length;
  }

  get countriesVisited(): number {
    const countryCodes = new Set(
      this.recentBookings
        .map((entry) => entry.flight?.destinationAirportCode)
        .filter((code): code is string => Boolean(code))
    );
    return countryCodes.size;
  }

  get milesTraveled(): number {
    const confirmedMinutes = this.recentBookings
      .filter((entry) => entry.booking.status === 'CONFIRMED' || entry.booking.status === 'COMPLETED')
      .reduce((sum, entry) => sum + Number(entry.flight?.durationMinutes ?? 0), 0);
    return Math.max(0, Math.round(confirmedMinutes * 8));
  }

  get visibleBookings(): BookingViewItem[] {
    return this.recentBookings.slice(0, this.visibleBookingsCount);
  }

  get hasPaymentPreview(): boolean {
    return Boolean(this.savedPaymentPreview);
  }

  saveProfile(): void {
    if (!this.profile || this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      return;
    }

    this.isSaving = true;
    this.errorMessage = '';
    this.statusMessage = '';

    const raw = this.profileForm.getRawValue();
    const request = {
      fullName: raw.fullName?.trim() || undefined,
      phone: raw.phone?.trim() || undefined,
      passportNumber: raw.passportNumber?.trim().toUpperCase() || undefined,
      nationality: raw.nationality?.trim() || undefined,
      dateOfBirth: raw.dateOfBirth?.trim() || undefined,
      profilePhotoUrl: raw.profilePhotoUrl?.trim() || undefined
    };

    this.authApiService.updateProfile(request).subscribe({
      next: (updated) => {
        this.profile = updated;
        this.isSaving = false;
        this.isEditMode = false;
        this.showPasswordForm = false;
        this.passwordForm.reset();
        this.statusMessage = 'Profile updated successfully.';
      },
      error: (error) => {
        this.isSaving = false;
        this.errorMessage = error?.error?.message ?? 'Unable to update profile right now.';
      }
    });
  }

  updatePassword(): void {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }

    this.isSaving = true;
    this.errorMessage = '';
    this.statusMessage = '';

    const value = this.passwordForm.getRawValue();
    this.authApiService.changePassword(String(value.currentPassword ?? ''), String(value.newPassword ?? '')).subscribe({
      next: (response) => {
        this.isSaving = false;
        this.passwordForm.reset();
        this.showPasswordForm = false;
        this.statusMessage = response.message ?? 'Password updated successfully.';
      },
      error: (error) => {
        this.isSaving = false;
        this.errorMessage = error?.error?.message ?? 'Unable to update password.';
      }
    });
  }

  async onPhotoSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }

    this.selectedPhotoName = file.name;

    if (!file.type.startsWith('image/')) {
      this.errorMessage = 'Please upload a valid image file.';
      return;
    }

    this.errorMessage = '';

    try {
      const optimizedPhoto = await this.optimizeProfilePhoto(file);
      this.profileForm.controls.profilePhotoUrl.setValue(optimizedPhoto);
      this.statusMessage = 'Profile photo selected. Save profile to apply.';
    } catch {
      this.errorMessage = 'Could not process this image. Please try another image.';
    }
  }

  removePhoto(): void {
    this.profileForm.controls.profilePhotoUrl.setValue('');
    this.selectedPhotoName = '';
    this.statusMessage = 'Profile photo removed. Save profile to apply.';
  }

  togglePasswordForm(): void {
    this.showPasswordForm = !this.showPasswordForm;
    this.errorMessage = '';
    this.statusMessage = '';
    if (!this.showPasswordForm) {
      this.passwordForm.reset();
    }
  }

  toggleEditMode(): void {
    this.isEditMode = !this.isEditMode;
    this.errorMessage = '';
    this.statusMessage = '';
    if (!this.isEditMode) {
      this.showPasswordForm = false;
      this.passwordForm.reset();
    }
  }

  logout(): void {
    this.authApiService.logout();
    this.router.navigate(['/auth/login']);
  }

  openMyBookings(): void {
    this.router.navigate(['/bookings']);
  }

  formatBookingRoute(entry: BookingViewItem): string {
    const origin = entry.flight?.originAirportCode;
    const destination = entry.flight?.destinationAirportCode;
    if (origin && destination) {
      return `${origin} → ${destination}`;
    }
    return `PNR ${entry.booking.pnrCode}`;
  }

  formatBookingDate(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return 'Date unavailable';
    }
    return date.toLocaleDateString('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric'
    });
  }

  bookingStateLabel(status: string): string {
    const normalized = String(status ?? '').toUpperCase();
    if (normalized === 'CONFIRMED' || normalized === 'COMPLETED') {
      return 'Completed';
    }
    if (normalized === 'PENDING') {
      return 'Upcoming';
    }
    if (normalized === 'CANCELLED') {
      return 'Cancelled';
    }
    return normalized || 'Unknown';
  }

  bookingStateClass(status: string): string {
    const normalized = String(status ?? '').toUpperCase();
    if (normalized === 'CONFIRMED' || normalized === 'COMPLETED') {
      return 'badge-completed';
    }
    if (normalized === 'PENDING') {
      return 'badge-upcoming';
    }
    if (normalized === 'CANCELLED') {
      return 'badge-cancelled';
    }
    return 'badge-neutral';
  }

  private fetchProfile(): void {
    this.authApiService.getProfile().subscribe({
      next: (profile) => {
        this.profile = profile;
        this.profileForm.patchValue({
          fullName: profile.fullName,
          phone: profile.phone,
          passportNumber: profile.passportNumber,
          nationality: profile.nationality,
          dateOfBirth: profile.dateOfBirth ?? '',
          profilePhotoUrl: profile.profilePhotoUrl ?? ''
        });
        this.selectedPhotoName = '';
        this.loadRoleContent(profile);
      },
      error: () => {
        this.errorMessage = 'Your previous session is no longer valid. Please login again.';
        this.authApiService.logout();
        this.router.navigate(['/auth/login'], {
          queryParams: { reason: 'session-expired' }
        });
      }
    });
  }

  private loadRoleContent(profile: ProfileResponse): void {
    this.recentBookings = [];
    this.roleActivities = [];
    this.savedPaymentPreview = '';
    this.managedAirlinesCount = 0;
    this.staffAssignedFlights = 0;

    if (profile.role === 'PASSENGER') {
      this.loadPassengerContent(profile.userId);
      return;
    }

    this.loadOperationsContent(profile);
  }

  private loadPassengerContent(userId: number): void {
    this.bookingApiService.getBookingsByUser(userId).subscribe({
      next: (bookings) => {
        const sorted = [...bookings].sort((a, b) => new Date(b.bookedAt).getTime() - new Date(a.bookedAt).getTime());
        if (!sorted.length) {
          this.recentBookings = [];
          this.savedPaymentPreview = '';
          return;
        }

        const flightRequests = sorted.map((booking) =>
          this.flightApiService.getFlightById(booking.flightId).pipe(catchError(() => of(undefined)))
        );

        forkJoin(flightRequests).subscribe({
          next: (flights) => {
            this.recentBookings = sorted.map((booking, index) => ({
              booking,
              flight: flights[index]
            }));

            const withPaymentId = this.recentBookings.find((entry) => Boolean(entry.booking.paymentId));
            this.savedPaymentPreview = withPaymentId?.booking.paymentId
              ? `•••• •••• •••• ${withPaymentId.booking.paymentId.slice(-4)}`
              : '';
          },
          error: () => {
            this.recentBookings = sorted.map((booking) => ({ booking }));
          }
        });
      },
      error: () => {
        this.recentBookings = [];
      }
    });
  }

  private loadOperationsContent(profile: ProfileResponse): void {
    if (profile.role === 'ADMIN') {
      forkJoin({
        analytics: this.adminApiService.getAnalytics().pipe(catchError(() => of(undefined))),
        users: this.adminApiService.getUsers().pipe(catchError(() => of([] as Array<Record<string, unknown>>))),
        flights: this.adminApiService.getManagedFlights().pipe(catchError(() => of([])))
      }).subscribe({
        next: ({ analytics, users, flights }) => {
          this.managedAirlinesCount = Number(analytics?.airlinesCount ?? 0);
          this.roleActivities = [
            { title: 'Created airline', time: users.length > 0 ? 'Recent update' : 'No recent action' },
            { title: 'Updated flight schedule', time: flights.length > 0 ? 'Recent update' : 'No recent action' },
            { title: 'Managed user account', time: users.length > 0 ? 'Recent update' : 'No recent action' }
          ];
        },
        error: () => {
          this.roleActivities = [
            { title: 'Created airline', time: 'No recent action' },
            { title: 'Updated flight schedule', time: 'No recent action' },
            { title: 'Managed user account', time: 'No recent action' }
          ];
        }
      });
      return;
    }

    forkJoin({
      dashboard: this.adminApiService.getFlightDashboard().pipe(catchError(() => of(undefined))),
      staffAirline: this.adminApiService.getStaffAirline(profile.userId).pipe(catchError(() => of(undefined))),
      managedBookings: this.adminApiService.getManagedBookings().pipe(catchError(() => of([] as Array<Record<string, unknown>>)))
    }).subscribe({
      next: ({ dashboard, staffAirline, managedBookings }) => {
        this.staffAssignedFlights = Number(dashboard?.totalFlights ?? 0);
        this.staffDepartment = 'Operations';
        this.staffAirport = staffAirline?.airlineId ? `Airline #${staffAirline.airlineId}` : 'Not assigned';
        this.staffSupervisor = managedBookings.length > 0 ? 'Operations Lead' : 'Not assigned';
        this.roleActivities = [
          { title: 'Flight check-in handling', time: 'Assigned task' },
          { title: 'Boarding management', time: 'Assigned task' },
          { title: 'Passenger assistance', time: 'Assigned task' }
        ];
      },
      error: () => {
        this.roleActivities = [
          { title: 'Flight check-in handling', time: 'Assigned task' },
          { title: 'Boarding management', time: 'Assigned task' },
          { title: 'Passenger assistance', time: 'Assigned task' }
        ];
      }
    });
  }

  private optimizeProfilePhoto(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => {
        const image = new Image();
        image.onload = () => {
          const canvas = document.createElement('canvas');
          const context = canvas.getContext('2d');
          if (!context) {
            reject();
            return;
          }

          const maxLength = 980;
          const sizes = [48, 40, 32, 24, 20, 16];
          const qualities = [0.7, 0.55, 0.4, 0.28, 0.2];

          for (const maxSize of sizes) {
            const scale = Math.min(maxSize / image.width, maxSize / image.height, 1);
            const width = Math.max(1, Math.round(image.width * scale));
            const height = Math.max(1, Math.round(image.height * scale));
            canvas.width = width;
            canvas.height = height;
            context.clearRect(0, 0, width, height);
            context.drawImage(image, 0, 0, width, height);

            for (const quality of qualities) {
              const webpData = canvas.toDataURL('image/webp', quality);
              if (webpData.length <= maxLength) {
                resolve(webpData);
                return;
              }
              const jpegData = canvas.toDataURL('image/jpeg', quality);
              if (jpegData.length <= maxLength) {
                resolve(jpegData);
                return;
              }
            }
          }

          canvas.width = 12;
          canvas.height = 12;
          context.clearRect(0, 0, 12, 12);
          context.drawImage(image, 0, 0, 12, 12);
          const tinyFallback = canvas.toDataURL('image/jpeg', 0.1);
          if (tinyFallback.length <= maxLength) {
            resolve(tinyFallback);
            return;
          }
          reject();
        };
        image.onerror = () => reject();
        image.src = String(reader.result ?? '');
      };
      reader.onerror = () => reject();
      reader.readAsDataURL(file);
    });
  }
}
