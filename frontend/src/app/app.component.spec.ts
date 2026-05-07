import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NavigationEnd, Router } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';

import { AppComponent } from './app.component';
import { AuthApiService } from './core/services/auth-api.service';
import { NotificationApiService } from './core/services/notification-api.service';
import { TokenStorageService } from './core/services/token-storage.service';

describe('AppComponent', () => {
  let authApiServiceSpy: jasmine.SpyObj<AuthApiService>;
  let notificationApiServiceSpy: jasmine.SpyObj<NotificationApiService>;
  let tokenStorageServiceSpy: jasmine.SpyObj<TokenStorageService>;
  let router: Router;
  let events$: Subject<NavigationEnd>;
  let currentUrl: string;

  beforeEach(async () => {
    authApiServiceSpy = jasmine.createSpyObj<AuthApiService>('AuthApiService', ['logout']);
    notificationApiServiceSpy = jasmine.createSpyObj<NotificationApiService>('NotificationApiService', ['getByUser', 'markRead']);
    tokenStorageServiceSpy = jasmine.createSpyObj<TokenStorageService>('TokenStorageService', ['getToken', 'getRole', 'getUserId']);
    events$ = new Subject<NavigationEnd>();
    currentUrl = '/';

    tokenStorageServiceSpy.getToken.and.returnValue(null);
    tokenStorageServiceSpy.getRole.and.returnValue(null);
    tokenStorageServiceSpy.getUserId.and.returnValue(null);
    notificationApiServiceSpy.getByUser.and.returnValue(of([]));
    notificationApiServiceSpy.markRead.and.returnValue(of({
      notificationId: 1,
      recipientId: 1,
      type: 'BOOKING_CONFIRMED',
      message: 'ok',
      channel: 'APP',
      createdAt: new Date().toISOString()
    }));

    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([]),
        { provide: AuthApiService, useValue: authApiServiceSpy },
        { provide: NotificationApiService, useValue: notificationApiServiceSpy },
        { provide: TokenStorageService, useValue: tokenStorageServiceSpy }
      ]
    })
      .overrideComponent(AppComponent, { set: { template: '' } })
      .compileComponents();

    router = TestBed.inject(Router);
    spyOnProperty(router, 'url', 'get').and.callFake(() => currentUrl);
    Object.defineProperty(router, 'events', { value: events$.asObservable() });
    spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));
  });

  it('creates the root component', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('loads notifications in constructor when user id exists and maps read fallback', () => {
    tokenStorageServiceSpy.getUserId.and.returnValue(44);
    notificationApiServiceSpy.getByUser.and.returnValue(of([
      { notificationId: 1, read: true },
      { notificationId: 2, isRead: true } as any,
      { notificationId: 3 } as any
    ]));

    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;

    expect(notificationApiServiceSpy.getByUser).toHaveBeenCalledWith(44);
    expect(app.notifications.map((item) => item.read)).toEqual([true, true, false]);
  });

  it('sets notification list to empty when notification API fails', () => {
    tokenStorageServiceSpy.getUserId.and.returnValue(44);
    notificationApiServiceSpy.getByUser.and.returnValue(throwError(() => new Error('down')));

    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;

    expect(app.notifications).toEqual([]);
  });

  it('updates url and reloads notifications on navigation end when logged in', () => {
    tokenStorageServiceSpy.getToken.and.returnValue('jwt');
    tokenStorageServiceSpy.getUserId.and.returnValue(77);
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    fixture.detectChanges();

    currentUrl = '/my-bookings';
    events$.next(new NavigationEnd(1, '/my-bookings', '/my-bookings'));

    expect(app.currentUrl).toBe('/my-bookings');
    expect(notificationApiServiceSpy.getByUser).toHaveBeenCalledWith(77);
  });

  it('does not reload notifications on navigation when logged out', () => {
    tokenStorageServiceSpy.getToken.and.returnValue(null);
    tokenStorageServiceSpy.getUserId.and.returnValue(77);
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    fixture.detectChanges();
    notificationApiServiceSpy.getByUser.calls.reset();

    currentUrl = '/flights';
    events$.next(new NavigationEnd(1, '/flights', '/flights'));

    expect(app.currentUrl).toBe('/flights');
    expect(notificationApiServiceSpy.getByUser).not.toHaveBeenCalled();
  });

  it('computes role and route based getters', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;

    tokenStorageServiceSpy.getRole.and.returnValue('ADMIN');
    expect(app.isAdmin).toBeTrue();
    expect(app.isOperationsUser).toBeTrue();
    expect(app.operationsLabel).toBe('Admin');

    tokenStorageServiceSpy.getRole.and.returnValue('AIRLINE_STAFF');
    expect(app.isAirlineStaff).toBeTrue();
    expect(app.operationsLabel).toBe('Airline Ops');

    tokenStorageServiceSpy.getRole.and.returnValue('PASSENGER');
    tokenStorageServiceSpy.getToken.and.returnValue('jwt');
    expect(app.isPassengerUser).toBeTrue();

    app.currentUrl = '/admin/users';
    expect(app.isOperationsRoute).toBeTrue();
    expect(app.showPublicNav).toBeFalse();

    app.currentUrl = '/flights';
    expect(app.isOperationsRoute).toBeFalse();
    expect(app.showPublicNav).toBeTrue();
  });

  it('computes unread summary and unread count', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    app.notifications = [
      { notificationId: 1, read: false } as any,
      { notificationId: 2, read: true } as any,
      { notificationId: 3, read: false } as any
    ];

    expect(app.unreadCount).toBe(2);
    expect(app.unreadSummary).toBe('2 unread');

    app.notifications = [{ notificationId: 4, read: true } as any];
    expect(app.unreadSummary).toBe('All caught up');
  });

  it('opens notifications and lazily loads when list is empty', () => {
    tokenStorageServiceSpy.getUserId.and.returnValue(9);
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    app.notifications = [];
    const stopSpy = jasmine.createSpy('stopPropagation');

    app.toggleNotifications({ stopPropagation: stopSpy } as any);

    expect(stopSpy).toHaveBeenCalled();
    expect(app.isNotificationMenuOpen).toBeTrue();
    expect(notificationApiServiceSpy.getByUser).toHaveBeenCalledWith(9);
  });

  it('toggles user menu and notification menu close on document click', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;

    app.toggleUserMenu({ stopPropagation: jasmine.createSpy('stopPropagation') } as any);
    app.toggleNotifications({ stopPropagation: jasmine.createSpy('stopPropagation') } as any);
    expect(app.isUserMenuOpen).toBeTrue();
    expect(app.isNotificationMenuOpen).toBeTrue();

    app.closeUserMenu();

    expect(app.isUserMenuOpen).toBeFalse();
    expect(app.isNotificationMenuOpen).toBeFalse();
  });

  it('marks notifications as read by id', () => {
    notificationApiServiceSpy.markRead.and.returnValue(of({
      notificationId: 10,
      recipientId: 7,
      type: 'BOOKING_CONFIRMED',
      message: 'ok',
      channel: 'APP',
      createdAt: new Date().toISOString()
    }));
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    app.notifications = [
      { notificationId: 10, read: false } as any,
      { notificationId: 11, read: false } as any
    ];

    app.markRead(10);

    expect(notificationApiServiceSpy.markRead).toHaveBeenCalledWith(10);
    expect(app.notifications.find((item) => item.notificationId === 10)?.read).toBeTrue();
    expect(app.notifications.find((item) => item.notificationId === 11)?.read).toBeFalse();
  });

  it('navigates through menu actions and logs out', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    app.isUserMenuOpen = true;
    app.isNotificationMenuOpen = true;

    app.goToMyBookings();
    app.goToProfile();
    app.goToOperations();
    app.logout();

    expect(router.navigate).toHaveBeenCalledWith(['/my-bookings']);
    expect(router.navigate).toHaveBeenCalledWith(['/auth/profile']);
    expect(router.navigate).toHaveBeenCalledWith(['/operations']);
    expect(authApiServiceSpy.logout).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/']);
    expect(app.notifications).toEqual([]);
  });

  it('maps notification variant and title across message types', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;

    expect(app.getNotificationVariant({ type: 'FLIGHT_DELAYED', message: '' } as any)).toBe('warning');
    expect(app.getNotificationVariant({ type: 'PAYMENT_SUCCESS', message: '' } as any)).toBe('success');
    expect(app.getNotificationVariant({ type: 'INFO', message: 'neutral' } as any)).toBe('info');

    expect(app.getNotificationTitle({ type: 'BOOKING_CONFIRMED' } as any)).toBe('Booking confirmed');
    expect(app.getNotificationTitle({ type: 'CHECKIN_REMINDER' } as any)).toBe('Check-in reminder');
    expect(app.getNotificationTitle({ type: 'FLIGHT_DELAYED' } as any)).toBe('Flight update');
    expect(app.getNotificationTitle({ type: 'PAYMENT_SUCCESS' } as any)).toBe('Payment update');
    expect(app.getNotificationTitle({ type: 'OTHER' } as any)).toBe('Travel update');
  });

  it('formats notification time ago for null, invalid, minute, hour and day ranges', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    const now = Date.now();
    spyOn(Date, 'now').and.returnValue(now);

    expect(app.getNotificationTimeAgo()).toBe('Just now');
    expect(app.getNotificationTimeAgo('invalid')).toBe('Just now');
    expect(app.getNotificationTimeAgo(new Date(now - 20_000).toISOString())).toBe('Just now');
    expect(app.getNotificationTimeAgo(new Date(now - 3 * 60_000).toISOString())).toBe('3m ago');
    expect(app.getNotificationTimeAgo(new Date(now - 2 * 60 * 60_000).toISOString())).toBe('2h ago');
    expect(app.getNotificationTimeAgo(new Date(now - 3 * 24 * 60 * 60_000).toISOString())).toBe('3d ago');
  });

  it('unsubscribes safely on destroy', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    fixture.detectChanges();

    app.ngOnDestroy();

    events$.next(new NavigationEnd(1, '/after', '/after'));
    expect(app.currentUrl).not.toBe('/after');
  });
});
