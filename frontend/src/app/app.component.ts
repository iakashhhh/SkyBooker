import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Subscription, filter } from 'rxjs';

import { NotificationResponse } from './core/models/notification.models';
import { AuthApiService } from './core/services/auth-api.service';
import { NotificationApiService } from './core/services/notification-api.service';
import { TokenStorageService } from './core/services/token-storage.service';

/**
 * This is the root layout component for SkyBooker frontend.
 * It provides top navigation and renders auth pages via router outlet.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit, OnDestroy {
  isUserMenuOpen = false;
  isNotificationMenuOpen = false;
  notifications: NotificationResponse[] = [];
  currentUrl = '';
  private navigationSub?: Subscription;

  constructor(
    private readonly tokenStorageService: TokenStorageService,
    private readonly authApiService: AuthApiService,
    private readonly notificationApiService: NotificationApiService,
    private readonly router: Router
  ) {
    this.loadNotifications();
  }

  ngOnInit(): void {
    this.currentUrl = this.router.url;
    this.navigationSub = this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => {
        this.currentUrl = this.router.url;
        if (this.isLoggedIn) {
          this.loadNotifications();
        }
      });
  }

  ngOnDestroy(): void {
    this.navigationSub?.unsubscribe();
  }

  get isLoggedIn(): boolean {
    return !!this.tokenStorageService.getToken();
  }

  get roleLabel(): string {
    return this.tokenStorageService.getRole() ?? 'PASSENGER';
  }

  get unreadCount(): number {
    return this.notifications.filter((notification) => !notification.read).length;
  }

  get unreadSummary(): string {
    const count = this.unreadCount;
    if (count === 0) {
      return 'All caught up';
    }
    return `${count} unread`;
  }

  get isAdmin(): boolean {
    return this.roleLabel === 'ADMIN';
  }

  get isAirlineStaff(): boolean {
    return this.roleLabel === 'AIRLINE_STAFF';
  }

  get isOperationsUser(): boolean {
    return this.isAdmin || this.isAirlineStaff;
  }

  get isPassengerUser(): boolean {
    return this.isLoggedIn && !this.isOperationsUser;
  }

  get isOperationsRoute(): boolean {
    return this.currentUrl.startsWith('/admin')
      || this.currentUrl.startsWith('/airline')
      || this.currentUrl.startsWith('/operations');
  }

  get showPublicNav(): boolean {
    return !this.isOperationsUser && !this.isOperationsRoute;
  }

  get operationsLabel(): string {
    if (this.isAdmin) {
      return 'Admin';
    }
    if (this.isAirlineStaff) {
      return 'Airline Ops';
    }
    return 'Operations';
  }

  toggleNotifications(event: MouseEvent): void {
    event.stopPropagation();
    this.isNotificationMenuOpen = !this.isNotificationMenuOpen;
    if (this.isNotificationMenuOpen && !this.notifications.length) {
      this.loadNotifications();
    }
  }

  markRead(notificationId: number): void {
    this.notificationApiService.markRead(notificationId).subscribe({
      next: () => {
        this.notifications = this.notifications.map((notification) =>
          notification.notificationId === notificationId
            ? { ...notification, read: true }
            : notification
        );
      }
    });
  }

  goToMyBookings(): void {
    this.isUserMenuOpen = false;
    this.router.navigate(['/my-bookings']);
  }

  toggleUserMenu(event: MouseEvent): void {
    event.stopPropagation();
    this.isUserMenuOpen = !this.isUserMenuOpen;
  }

  goToProfile(): void {
    this.isUserMenuOpen = false;
    this.router.navigate(['/auth/profile']);
  }

  goToOperations(): void {
    this.isUserMenuOpen = false;
    this.router.navigate(['/operations']);
  }

  logout(): void {
    this.authApiService.logout();
    this.isUserMenuOpen = false;
    this.isNotificationMenuOpen = false;
    this.notifications = [];
    this.router.navigate(['/']);
  }

  @HostListener('document:click')
  closeUserMenu(): void {
    this.isUserMenuOpen = false;
    this.isNotificationMenuOpen = false;
  }

  getNotificationVariant(notification: NotificationResponse): 'success' | 'warning' | 'info' {
    const type = String(notification.type ?? '').toUpperCase();
    const message = String(notification.message ?? '').toLowerCase();
    if (type.includes('DELAY') || type.includes('FAILED') || message.includes('delay') || message.includes('failed') || message.includes('cancel')) {
      return 'warning';
    }
    if (type.includes('BOOKING') || type.includes('PAYMENT') || message.includes('confirmed') || message.includes('success')) {
      return 'success';
    }
    return 'info';
  }

  getNotificationTitle(notification: NotificationResponse): string {
    const type = String(notification.type ?? '').toUpperCase();
    if (type.includes('BOOKING')) {
      return 'Booking confirmed';
    }
    if (type.includes('CHECKIN')) {
      return 'Check-in reminder';
    }
    if (type.includes('DELAY')) {
      return 'Flight update';
    }
    if (type.includes('PAYMENT')) {
      return 'Payment update';
    }
    return 'Travel update';
  }

  getNotificationTimeAgo(createdAt?: string): string {
    if (!createdAt) {
      return 'Just now';
    }
    const created = new Date(createdAt).getTime();
    if (Number.isNaN(created)) {
      return 'Just now';
    }
    const diff = Date.now() - created;
    const minute = 60 * 1000;
    const hour = 60 * minute;
    const day = 24 * hour;
    if (diff < minute) {
      return 'Just now';
    }
    if (diff < hour) {
      return `${Math.floor(diff / minute)}m ago`;
    }
    if (diff < day) {
      return `${Math.floor(diff / hour)}h ago`;
    }
    return `${Math.floor(diff / day)}d ago`;
  }

  private loadNotifications(): void {
    const userId = this.tokenStorageService.getUserId();
    if (!userId) {
      return;
    }

    this.notificationApiService.getByUser(userId).subscribe({
      next: (response) => {
        this.notifications = response.map((notification) => ({
          ...notification,
          read: notification.read ?? notification.isRead ?? false
        }));
      },
      error: () => {
        this.notifications = [];
      }
    });
  }

}
