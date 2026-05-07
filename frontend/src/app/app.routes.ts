import { Routes } from '@angular/router';

import { LoginComponent } from './features/auth/login/login.component';
import { RegisterComponent } from './features/auth/register/register.component';
import { ProfileComponent } from './features/auth/profile/profile.component';
import { FlightSearchComponent } from './features/flights/search/flight-search.component';
import { PnrStatusPageComponent } from './features/bookings/pnr-status-page/pnr-status-page.component';
import { FlightResultsComponent } from './features/flights/results/flight-results.component';
import { LandingPageComponent } from './features/home/landing/landing-page.component';
import { SeatSelectionComponent } from './features/seats/seat-selection/seat-selection.component';
import { BookingSummaryPageComponent } from './features/bookings/booking-summary-page/booking-summary-page.component';
import { PassengerFormPageComponent } from './features/passengers/passenger-form-page/passenger-form-page.component';
import { PaymentPageComponent } from './features/payments/payment-page/payment-page.component';
import { PaymentSuccessPageComponent } from './features/payments/payment-success-page/payment-success-page.component';
import { PaymentFailedPageComponent } from './features/payments/payment-failed-page/payment-failed-page.component';
import { BookingSuccessPageComponent } from './features/bookings/booking-success-page/booking-success-page.component';
import { MyBookingsPageComponent } from './features/bookings/my-bookings-page/my-bookings-page.component';
import { PublicTicketPageComponent } from './features/bookings/public-ticket-page/public-ticket-page.component';
import { AdminDashboardPageComponent } from './features/admin/admin-dashboard-page/admin-dashboard-page.component';
import { OperationsLayoutComponent } from './features/operations/operations-layout/operations-layout.component';
import { OperationsRedirectComponent } from './features/operations/operations-redirect/operations-redirect.component';
import { authGuard } from './core/guards/auth.guard';
import { adminGuard } from './core/guards/admin.guard';
import { airlineStaffGuard } from './core/guards/airline-staff.guard';
import { guestGuard } from './core/guards/guest.guard';
import { operationsGuard } from './core/guards/operations.guard';

/**
 * This route list defines auth page navigation in a simple modular structure.
 */
export const appRoutes: Routes = [
  { path: '', component: LandingPageComponent },
  { path: 'home', component: LandingPageComponent },
  { path: 'auth/login', component: LoginComponent, canActivate: [guestGuard] },
  { path: 'auth/register', component: RegisterComponent, canActivate: [guestGuard] },
  { path: 'auth/profile', component: ProfileComponent, canActivate: [authGuard] },
  { path: 'flights/search', component: FlightSearchComponent },
  { path: 'pnr-status', component: PnrStatusPageComponent },
  { path: 'flights/results', component: FlightResultsComponent },
  { path: 'seats/select', component: SeatSelectionComponent },
  { path: 'booking-summary', component: BookingSummaryPageComponent, canActivate: [authGuard] },
  { path: 'bookings/summary', redirectTo: 'booking-summary', pathMatch: 'full' },
  { path: 'review', redirectTo: 'booking-summary', pathMatch: 'full' },
  { path: 'passenger-details', component: PassengerFormPageComponent, canActivate: [authGuard] },
  { path: 'passengers/form', redirectTo: 'passenger-details', pathMatch: 'full' },
  { path: 'passenger', redirectTo: 'passenger-details', pathMatch: 'full' },
  { path: 'payments/checkout', redirectTo: 'payment', pathMatch: 'full' },
  { path: 'payment', component: PaymentPageComponent, canActivate: [authGuard] },
  { path: 'payment-success', component: PaymentSuccessPageComponent, canActivate: [authGuard] },
  { path: 'payment-failed', component: PaymentFailedPageComponent, canActivate: [authGuard] },
  { path: 'booking-success', component: BookingSuccessPageComponent, canActivate: [authGuard] },
  { path: 'my-bookings', component: MyBookingsPageComponent, canActivate: [authGuard] },
  { path: 'ticket/:id', component: PublicTicketPageComponent },
  { path: 'operations', component: OperationsRedirectComponent, canActivate: [operationsGuard] },
  {
    path: 'admin',
    component: OperationsLayoutComponent,
    canActivate: [adminGuard],
    children: [
      { path: 'dashboard', component: AdminDashboardPageComponent, data: { panel: 'admin', section: 'dashboard', title: 'Dashboard', subtitle: 'Platform overview and live operational metrics' } },
      { path: 'airlines', component: AdminDashboardPageComponent, data: { panel: 'admin', section: 'airlines', title: 'Airlines', subtitle: 'Manage airline catalog across the network' } },
      { path: 'flights', component: AdminDashboardPageComponent, data: { panel: 'admin', section: 'flights', title: 'Flights', subtitle: 'Manage all flights across airlines' } },
      { path: 'airports', component: AdminDashboardPageComponent, data: { panel: 'admin', section: 'airports', title: 'Airports', subtitle: 'Manage airport data and global hubs' } },
      { path: 'bookings', component: AdminDashboardPageComponent, data: { panel: 'admin', section: 'bookings', title: 'Bookings', subtitle: 'Track all booking lifecycle activity' } },
      { path: 'payments', component: AdminDashboardPageComponent, data: { panel: 'admin', section: 'payments', title: 'Payments', subtitle: 'Monitor payment status and revenue health' } },
      { path: 'users', component: AdminDashboardPageComponent, data: { panel: 'admin', section: 'users', title: 'Users', subtitle: 'Review registered accounts and roles' } },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' }
    ]
  },
  {
    path: 'airline',
    component: OperationsLayoutComponent,
    canActivate: [airlineStaffGuard],
    children: [
      { path: 'dashboard', component: AdminDashboardPageComponent, data: { panel: 'airline', section: 'dashboard', title: 'Dashboard', subtitle: 'Airline-specific operations and performance overview' } },
      { path: 'flights', component: AdminDashboardPageComponent, data: { panel: 'airline', section: 'flights', title: 'Flights', subtitle: 'Create and manage flights for your airline' } },
      { path: 'schedules', component: AdminDashboardPageComponent, data: { panel: 'airline', section: 'schedules', title: 'Schedules', subtitle: 'Review departure and arrival schedules' } },
      { path: 'bookings', component: AdminDashboardPageComponent, data: { panel: 'airline', section: 'bookings', title: 'Bookings', subtitle: 'View bookings tied to your managed flights' } },
      { path: 'passengers', component: AdminDashboardPageComponent, data: { panel: 'airline', section: 'passengers', title: 'Passengers', subtitle: 'Passenger list for your managed flights' } },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' }
    ]
  },
  { path: '**', redirectTo: '' }
];
