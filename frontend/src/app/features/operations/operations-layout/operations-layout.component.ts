import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Subscription, filter } from 'rxjs';

import { TokenStorageService } from '../../../core/services/token-storage.service';

type NavLink = { label: string; route: string; icon: string };

@Component({
  selector: 'app-operations-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './operations-layout.component.html',
  styleUrl: './operations-layout.component.scss'
})
export class OperationsLayoutComponent implements OnInit, OnDestroy {
  sidebarOpen = false;
  sidebarCollapsed = false;
  currentPageTitle = 'Dashboard';
  currentPageSubtitle = 'Operations overview';
  private navigationSub?: Subscription;

  readonly adminLinks: NavLink[] = [
    { label: 'Dashboard', route: '/admin/dashboard', icon: 'dashboard' },
    { label: 'Airlines', route: '/admin/airlines', icon: 'airline' },
    { label: 'Flights', route: '/admin/flights', icon: 'flight' },
    { label: 'Airports', route: '/admin/airports', icon: 'airport' },
    { label: 'Bookings', route: '/admin/bookings', icon: 'booking' },
    { label: 'Payments', route: '/admin/payments', icon: 'payment' },
    { label: 'Users', route: '/admin/users', icon: 'users' }
  ];

  readonly staffLinks: NavLink[] = [
    { label: 'Dashboard', route: '/airline/dashboard', icon: 'dashboard' },
    { label: 'Flights', route: '/airline/flights', icon: 'flight' },
    { label: 'Schedules', route: '/airline/schedules', icon: 'schedule' },
    { label: 'Bookings', route: '/airline/bookings', icon: 'booking' },
    { label: 'Passengers', route: '/airline/passengers', icon: 'passenger' }
  ];

  constructor(
    private readonly tokenStorageService: TokenStorageService,
    private readonly router: Router,
    private readonly activatedRoute: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.navigationSub = this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => {
        this.sidebarOpen = false;
        this.syncPageContext();
      });
    this.syncPageContext();
  }

  ngOnDestroy(): void {
    this.navigationSub?.unsubscribe();
  }

  get isAdmin(): boolean {
    return this.tokenStorageService.getRole() === 'ADMIN';
  }

  get isStaff(): boolean {
    return this.tokenStorageService.getRole() === 'AIRLINE_STAFF';
  }

  get roleLabel(): string {
    return this.isAdmin ? 'Admin' : 'Airline Staff';
  }

  get links(): NavLink[] {
    return this.isAdmin ? this.adminLinks : this.staffLinks;
  }

  toggleSidebar(): void {
    this.sidebarOpen = !this.sidebarOpen;
  }

  toggleSidebarCollapse(): void {
    this.sidebarCollapsed = !this.sidebarCollapsed;
  }

  private syncPageContext(): void {
    let route: ActivatedRoute | null = this.activatedRoute;
    while (route?.firstChild) {
      route = route.firstChild;
    }

    const data = route?.snapshot?.data ?? {};
    const routeTitle = data['title'];
    const routeSubtitle = data['subtitle'];
    if (routeTitle && routeSubtitle) {
      this.currentPageTitle = String(routeTitle);
      this.currentPageSubtitle = String(routeSubtitle);
      return;
    }

    const path = this.router.url;
    const isAdminPath = path.startsWith('/admin/');
    const section = (path.split('/')[2] ?? 'dashboard').toLowerCase();
    const defaults: Record<string, { title: string; subtitle: string }> = isAdminPath
      ? {
          dashboard: { title: 'Dashboard', subtitle: 'Platform overview and live operational metrics' },
          airlines: { title: 'Airlines', subtitle: 'Manage airline catalog across the network' },
          flights: { title: 'Flights', subtitle: 'Manage all flights across airlines' },
          airports: { title: 'Airports', subtitle: 'Manage airport data and global hubs' },
          bookings: { title: 'Bookings', subtitle: 'Track all booking lifecycle activity' },
          payments: { title: 'Payments', subtitle: 'Monitor payment status and revenue health' },
          users: { title: 'Users', subtitle: 'Review registered accounts and roles' }
        }
      : {
          dashboard: { title: 'Dashboard', subtitle: 'Airline-specific operations and performance overview' },
          flights: { title: 'Flights', subtitle: 'Create and manage flights for your airline' },
          schedules: { title: 'Schedules', subtitle: 'Review departure and arrival schedules' },
          bookings: { title: 'Bookings', subtitle: 'View bookings tied to your managed flights' },
          passengers: { title: 'Passengers', subtitle: 'Passenger list for your managed flights' }
        };

    const resolved = defaults[section] ?? defaults['dashboard'];
    this.currentPageTitle = resolved.title;
    this.currentPageSubtitle = resolved.subtitle;
  }
}
