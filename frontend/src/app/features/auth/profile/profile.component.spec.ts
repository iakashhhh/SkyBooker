import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AdminApiService } from '../../../core/services/admin-api.service';
import { AuthApiService } from '../../../core/services/auth-api.service';
import { BookingApiService } from '../../../core/services/booking-api.service';
import { FlightApiService } from '../../../core/services/flight-api.service';
import { ProfileComponent } from './profile.component';

describe('ProfileComponent', () => {
  let fixture: ComponentFixture<ProfileComponent>;
  let component: ProfileComponent;
  let authApiSpy: jasmine.SpyObj<AuthApiService>;
  let bookingApiSpy: jasmine.SpyObj<BookingApiService>;
  let flightApiSpy: jasmine.SpyObj<FlightApiService>;
  let adminApiSpy: jasmine.SpyObj<AdminApiService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    authApiSpy = jasmine.createSpyObj<AuthApiService>('AuthApiService', [
      'getProfile',
      'logout',
      'updateProfile',
      'changePassword'
    ]);
    bookingApiSpy = jasmine.createSpyObj<BookingApiService>('BookingApiService', ['getBookingsByUser']);
    flightApiSpy = jasmine.createSpyObj<FlightApiService>('FlightApiService', ['getFlightById']);
    adminApiSpy = jasmine.createSpyObj<AdminApiService>('AdminApiService', [
      'getAnalytics',
      'getUsers',
      'getManagedFlights',
      'getFlightDashboard',
      'getStaffAirline',
      'getManagedBookings'
    ]);
    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [ProfileComponent],
      providers: [
        { provide: AuthApiService, useValue: authApiSpy },
        { provide: BookingApiService, useValue: bookingApiSpy },
        { provide: FlightApiService, useValue: flightApiSpy },
        { provide: AdminApiService, useValue: adminApiSpy },
        { provide: Router, useValue: routerSpy }
      ]
    })
      .overrideComponent(ProfileComponent, { set: { template: '' } })
      .compileComponents();

    fixture = TestBed.createComponent(ProfileComponent);
    component = fixture.componentInstance;
  });

  it('loads passenger profile and recent booking data successfully', () => {
    authApiSpy.getProfile.and.returnValue(of({
      userId: 7,
      fullName: 'Akash Sharma',
      email: 'akash@test.com',
      phone: '9999999999',
      passportNumber: 'P1234567',
      nationality: 'Indian',
      role: 'PASSENGER',
      provider: 'LOCAL',
      active: true
    } as any));
    bookingApiSpy.getBookingsByUser.and.returnValue(of([
      { bookingId: 'B1', bookedAt: '2026-05-02T10:00:00Z', flightId: 10, status: 'CONFIRMED', paymentId: 'PAY1234' },
      { bookingId: 'B2', bookedAt: '2026-04-01T10:00:00Z', flightId: 11, status: 'PENDING' }
    ] as any));
    flightApiSpy.getFlightById.and.returnValues(of({ destinationAirportCode: 'DEL', durationMinutes: 100 } as any), of({ destinationAirportCode: 'BLR', durationMinutes: 120 } as any));

    fixture.detectChanges();

    expect(component.profile?.fullName).toBe('Akash Sharma');
    expect(component.recentBookings.length).toBe(2);
    expect(component.hasPaymentPreview).toBeTrue();
    expect(component.isPassenger).toBeTrue();
  });

  it('redirects to login when profile fetch fails', () => {
    authApiSpy.getProfile.and.returnValue(throwError(() => new Error('expired')));

    fixture.detectChanges();

    expect(component.errorMessage).toContain('previous session is no longer valid');
    expect(authApiSpy.logout).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/auth/login'], {
      queryParams: { reason: 'session-expired' }
    });
  });

  it('blocks saveProfile when form is invalid and does not call API', () => {
    component.profile = { userId: 7, role: 'PASSENGER' } as any;
    component.profileForm.patchValue({ fullName: '', phone: 'bad' });

    component.saveProfile();

    expect(authApiSpy.updateProfile).not.toHaveBeenCalled();
  });

  it('updates profile successfully in edit flow', () => {
    component.profile = { userId: 7, role: 'PASSENGER' } as any;
    component.profileForm.patchValue({
      fullName: 'Akash Sharma',
      phone: '+919999999999',
      passportNumber: 'P1234567',
      nationality: 'Indian',
      dateOfBirth: '1990-01-01',
      profilePhotoUrl: ''
    });
    authApiSpy.updateProfile.and.returnValue(of({ fullName: 'Akash Sharma', role: 'PASSENGER' } as any));

    component.saveProfile();

    expect(authApiSpy.updateProfile).toHaveBeenCalled();
    expect(component.statusMessage).toContain('Profile updated successfully');
    expect(component.isEditMode).toBeFalse();
  });

  it('shows update profile API error and keeps save flow stable', () => {
    component.profile = { userId: 7, role: 'PASSENGER' } as any;
    component.profileForm.patchValue({
      fullName: 'Akash Sharma',
      phone: '+919999999999',
      passportNumber: 'P1234567',
      nationality: 'Indian'
    });
    authApiSpy.updateProfile.and.returnValue(throwError(() => ({ error: { message: 'update failed' } })));

    component.saveProfile();

    expect(component.errorMessage).toBe('update failed');
    expect(component.isSaving).toBeFalse();
  });

  it('handles password-update error flow', () => {
    component.passwordForm.patchValue({ currentPassword: 'Old@1234', newPassword: 'NewPass@123' });
    authApiSpy.changePassword.and.returnValue(throwError(() => ({ error: { message: 'bad current password' } })));

    component.updatePassword();

    expect(component.errorMessage).toBe('bad current password');
    expect(component.isSaving).toBeFalse();
  });

  it('updates password successfully and closes password form', () => {
    component.showPasswordForm = true;
    component.passwordForm.patchValue({ currentPassword: 'Old@1234', newPassword: 'NewPass@123' });
    authApiSpy.changePassword.and.returnValue(of({ message: 'Password changed' } as any));

    component.updatePassword();

    expect(component.statusMessage).toBe('Password changed');
    expect(component.showPasswordForm).toBeFalse();
    expect(component.isSaving).toBeFalse();
  });

  it('formats fallback route and invalid date safely', () => {
    expect(component.formatBookingRoute({ booking: { pnrCode: 'PNR777' } as any })).toBe('PNR PNR777');
    expect(component.formatBookingDate('not-a-date')).toBe('Date unavailable');
  });

  it('maps booking status labels and css classes', () => {
    expect(component.bookingStateLabel('CONFIRMED')).toBe('Completed');
    expect(component.bookingStateLabel('PENDING')).toBe('Upcoming');
    expect(component.bookingStateClass('CANCELLED')).toBe('badge-cancelled');
    expect(component.bookingStateClass('UNKNOWN')).toBe('badge-neutral');
  });

  it('toggles edit and password forms and can navigate to bookings', () => {
    component.toggleEditMode();
    component.togglePasswordForm();
    component.openMyBookings();

    expect(component.isEditMode).toBeTrue();
    expect(component.showPasswordForm).toBeTrue();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/bookings']);
  });

  it('resets password form when toggled closed and supports logout', () => {
    component.showPasswordForm = true;
    component.passwordForm.patchValue({ currentPassword: 'Old@1234', newPassword: 'NewPass@123' });

    component.togglePasswordForm();
    expect(component.showPasswordForm).toBeFalse();
    expect(component.passwordForm.value.currentPassword).toBeNull();

    component.logout();
    expect(authApiSpy.logout).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/auth/login']);
  });

  it('loads admin operations widgets when role is ADMIN', () => {
    authApiSpy.getProfile.and.returnValue(of({
      userId: 1,
      fullName: 'Admin User',
      email: 'admin@test.com',
      phone: '9999999999',
      passportNumber: 'P1234567',
      nationality: 'Indian',
      role: 'ADMIN',
      provider: 'LOCAL',
      active: true
    } as any));
    adminApiSpy.getAnalytics.and.returnValue(of({ airlinesCount: 14 } as any));
    adminApiSpy.getUsers.and.returnValue(of([{ userId: 1 }, { userId: 2 }] as any));
    adminApiSpy.getManagedFlights.and.returnValue(of([{ flightId: 10 }] as any));

    fixture.detectChanges();

    expect(component.isAdmin).toBeTrue();
    expect(component.managedAirlinesCount).toBe(14);
    expect(component.roleActivities.length).toBe(3);
  });

  it('loads airline staff operations widgets when role is AIRLINE_STAFF', () => {
    authApiSpy.getProfile.and.returnValue(of({
      userId: 9,
      fullName: 'Ops User',
      email: 'ops@test.com',
      phone: '9999999999',
      passportNumber: 'P1234567',
      nationality: 'Indian',
      role: 'AIRLINE_STAFF',
      provider: 'LOCAL',
      active: true
    } as any));
    adminApiSpy.getFlightDashboard.and.returnValue(of({ totalFlights: 28 } as any));
    adminApiSpy.getStaffAirline.and.returnValue(of({ airlineId: 44 } as any));
    adminApiSpy.getManagedBookings.and.returnValue(of([{ bookingId: 'B1' }] as any));

    fixture.detectChanges();

    expect(component.isAirlineStaff).toBeTrue();
    expect(component.staffAssignedFlights).toBe(28);
    expect(component.staffAirport).toBe('Airline #44');
  });

  it('exposes profile analytics getters for passenger records', () => {
    component.profile = { role: 'PASSENGER' } as any;
    component.recentBookings = [
      {
        booking: { status: 'CONFIRMED' } as any,
        flight: { destinationAirportCode: 'DXB', durationMinutes: 100 } as any
      },
      {
        booking: { status: 'PENDING' } as any,
        flight: { destinationAirportCode: 'DXB', durationMinutes: 80 } as any
      },
      {
        booking: { status: 'COMPLETED' } as any,
        flight: { destinationAirportCode: 'SIN', durationMinutes: 120 } as any
      }
    ];

    expect(component.profileSubtitle).toBe('Frequent Traveler');
    expect(component.totalBookings).toBe(3);
    expect(component.countriesVisited).toBe(2);
    expect(component.milesTraveled).toBe((100 + 120) * 8);
    expect(component.visibleBookings.length).toBe(3);
  });

  it('falls back safely when passenger content loading fails', () => {
    authApiSpy.getProfile.and.returnValue(of({
      userId: 17,
      fullName: 'Akash Sharma',
      email: 'akash@test.com',
      phone: '9999999999',
      passportNumber: 'P1234567',
      nationality: 'Indian',
      role: 'PASSENGER',
      provider: 'LOCAL',
      active: true
    } as any));
    bookingApiSpy.getBookingsByUser.and.returnValue(throwError(() => new Error('booking API down')));

    fixture.detectChanges();

    expect(component.recentBookings).toEqual([]);
  });

  it('rejects non-image uploads for profile photo', async () => {
    const file = new File(['x'], 'notes.txt', { type: 'text/plain' });

    await component.onPhotoSelected({ target: { files: [file] } } as any);

    expect(component.errorMessage).toBe('Please upload a valid image file.');
  });

  it('removes selected photo from profile form', () => {
    component.profileForm.controls.profilePhotoUrl.setValue('data:image/png;base64,abc');
    component.selectedPhotoName = 'photo.png';

    component.removePhoto();

    expect(component.profileForm.controls.profilePhotoUrl.value).toBe('');
    expect(component.selectedPhotoName).toBe('');
    expect(component.statusMessage).toContain('removed');
  });

  it('ignores photo upload event when no file is selected', async () => {
    component.errorMessage = '';

    await component.onPhotoSelected({ target: { files: [] } } as any);

    expect(component.errorMessage).toBe('');
    expect(component.selectedPhotoName).toBe('');
  });

  it('updates profile photo field when image optimization succeeds', async () => {
    const imageFile = new File(['photo'], 'avatar.png', { type: 'image/png' });
    spyOn<any>(component, 'optimizeProfilePhoto').and.returnValue(Promise.resolve('data:image/webp;base64,abc'));

    await component.onPhotoSelected({ target: { files: [imageFile] } } as any);

    expect(component.profileForm.controls.profilePhotoUrl.value).toBe('data:image/webp;base64,abc');
    expect(component.statusMessage).toContain('Profile photo selected');
    expect(component.errorMessage).toBe('');
  });

  it('shows error when image optimization fails for a valid image', async () => {
    const imageFile = new File(['photo'], 'avatar.png', { type: 'image/png' });
    spyOn<any>(component, 'optimizeProfilePhoto').and.returnValue(Promise.reject(new Error('fail')));

    await component.onPhotoSelected({ target: { files: [imageFile] } } as any);

    expect(component.errorMessage).toBe('Could not process this image. Please try another image.');
  });

  it('resets edit/password states when edit mode is turned off', () => {
    component.isEditMode = true;
    component.showPasswordForm = true;
    component.passwordForm.patchValue({ currentPassword: 'Old@1234', newPassword: 'NewPass@123' });

    component.toggleEditMode();

    expect(component.isEditMode).toBeFalse();
    expect(component.showPasswordForm).toBeFalse();
    expect(component.passwordForm.value.currentPassword).toBeNull();
  });
});
