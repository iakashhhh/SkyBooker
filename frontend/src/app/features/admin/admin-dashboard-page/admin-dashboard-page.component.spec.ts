import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { AdminApiService } from '../../../core/services/admin-api.service';
import { AirlineAirportApiService } from '../../../core/services/airline-airport-api.service';
import { TokenStorageService } from '../../../core/services/token-storage.service';
import { AdminDashboardPageComponent } from './admin-dashboard-page.component';

describe('AdminDashboardPageComponent', () => {
  let fixture: ComponentFixture<AdminDashboardPageComponent>;
  let component: AdminDashboardPageComponent;

  let adminApiSpy: jasmine.SpyObj<AdminApiService>;
  let airlineAirportApiSpy: jasmine.SpyObj<AirlineAirportApiService>;
  let tokenStorageSpy: jasmine.SpyObj<TokenStorageService>;

  const routeData$ = new BehaviorSubject<Record<string, unknown>>({
    panel: 'admin',
    section: 'dashboard',
    title: 'Dashboard'
  });

  beforeEach(async () => {
    adminApiSpy = jasmine.createSpyObj<AdminApiService>('AdminApiService', [
      'getAnalytics',
      'getUsers',
      'getBookings',
      'getPayments',
      'getFlightDashboard',
      'getManagedFlights',
      'getManagedBookings',
      'getManagedPassengers',
      'getStaffAirline'
    ]);

    airlineAirportApiSpy = jasmine.createSpyObj<AirlineAirportApiService>('AirlineAirportApiService', [
      'getAirlines',
      'getAirports'
    ]);

    tokenStorageSpy = jasmine.createSpyObj<TokenStorageService>('TokenStorageService', ['getRole', 'getUserId']);

    tokenStorageSpy.getRole.and.returnValue('ADMIN');
    tokenStorageSpy.getUserId.and.returnValue(1);

    adminApiSpy.getAnalytics.and.returnValue(of({ revenue: 0 } as any));
    adminApiSpy.getUsers.and.returnValue(of([]));
    adminApiSpy.getBookings.and.returnValue(of([]));
    adminApiSpy.getPayments.and.returnValue(of([]));
    adminApiSpy.getFlightDashboard.and.returnValue(of({ totalFlights: 0, todayFlights: 0, bookingsCount: 0 }));
    adminApiSpy.getManagedFlights.and.returnValue(of([]));
    adminApiSpy.getManagedBookings.and.returnValue(of([]));
    adminApiSpy.getManagedPassengers.and.returnValue(of([]));
    adminApiSpy.getStaffAirline.and.returnValue(of({ userId: 1, airlineId: 10 }));

    airlineAirportApiSpy.getAirlines.and.returnValue(of([]));
    airlineAirportApiSpy.getAirports.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [AdminDashboardPageComponent],
      providers: [
        { provide: AdminApiService, useValue: adminApiSpy },
        { provide: AirlineAirportApiService, useValue: airlineAirportApiSpy },
        { provide: TokenStorageService, useValue: tokenStorageSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { data: routeData$.value },
            data: routeData$.asObservable()
          }
        }
      ]
    })
      .overrideComponent(AdminDashboardPageComponent, { set: { template: '' } })
      .compileComponents();

    fixture = TestBed.createComponent(AdminDashboardPageComponent);
    component = fixture.componentInstance;
  });

  it('loads admin dashboard data on init for admin users', () => {
    component.ngOnInit();

    expect(adminApiSpy.getAnalytics).toHaveBeenCalled();
    expect(adminApiSpy.getManagedBookings).toHaveBeenCalled();
    expect(adminApiSpy.getManagedPassengers).toHaveBeenCalled();
    expect(component.errorMessage).toBe('');
    expect(component.isLoading).toBeFalse();
  });

  it('falls back to empty managed passengers when managed passengers API fails', () => {
    adminApiSpy.getManagedPassengers.and.returnValue(throwError(() => new Error('service unavailable')));

    component.ngOnInit();

    expect(adminApiSpy.getManagedPassengers).toHaveBeenCalled();
    expect(component.managedPassengers).toEqual([]);
    expect(component.isLoading).toBeFalse();
  });
});
