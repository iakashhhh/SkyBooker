import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { AirlineAirportApiService } from '../../../core/services/airline-airport-api.service';
import { BookingApiService } from '../../../core/services/booking-api.service';
import { FlightSearchComponent } from './flight-search.component';

describe('FlightSearchComponent', () => {
  let fixture: ComponentFixture<FlightSearchComponent>;
  let component: FlightSearchComponent;
  let routerSpy: jasmine.SpyObj<Router>;
  let bookingApiSpy: jasmine.SpyObj<BookingApiService>;
  let airlineAirportSpy: jasmine.SpyObj<AirlineAirportApiService>;

  const queryParams$ = new BehaviorSubject<Record<string, string>>({});

  beforeEach(async () => {
    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);
    bookingApiSpy = jasmine.createSpyObj<BookingApiService>('BookingApiService', ['getBookingByPnr']);
    airlineAirportSpy = jasmine.createSpyObj<AirlineAirportApiService>('AirlineAirportApiService', ['getAirports', 'getAirlines']);

    airlineAirportSpy.getAirports.and.returnValue(of([
      { iataCode: 'DEL', city: 'Delhi' },
      { iataCode: 'BLR', city: 'Bengaluru' },
      { iataCode: '', city: 'Invalid' }
    ] as any));
    airlineAirportSpy.getAirlines.and.returnValue(of([
      { airlineId: 2, name: 'Zulu Air', active: true },
      { airlineId: 1, name: 'Alpha Air', active: true },
      { airlineId: 3, name: 'Inactive', active: false }
    ] as any));

    await TestBed.configureTestingModule({
      imports: [FlightSearchComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            queryParams: queryParams$.asObservable(),
            snapshot: {
              routeConfig: { path: 'flights/search' }
            }
          }
        },
        { provide: Router, useValue: routerSpy },
        { provide: AirlineAirportApiService, useValue: airlineAirportSpy },
        { provide: BookingApiService, useValue: bookingApiSpy }
      ]
    })
      .overrideComponent(FlightSearchComponent, {
        set: { template: '<button id="submit" (click)="submit()">Submit</button>' }
      })
      .compileComponents();

    fixture = TestBed.createComponent(FlightSearchComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads active airports and airlines sorted by city/name', () => {
    expect(component.airportOptions.map((it) => it.iataCode)).toEqual(['BLR', 'DEL']);
    expect(component.airlineOptions.map((it) => it.name)).toEqual(['Alpha Air', 'Zulu Air']);
  });

  it('blocks submit for invalid form when button is clicked', () => {
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('#submit')?.click();

    expect(routerSpy.navigate).not.toHaveBeenCalled();
    expect(component.form.controls.origin.touched).toBeTrue();
  });

  it('blocks round trip submit if return date is missing', () => {
    component.form.patchValue({
      tripType: 'ROUND_TRIP',
      origin: 'DEL',
      destination: 'BLR',
      journeyDate: '2099-01-01',
      returnDate: ''
    });

    component.submit();

    expect(component.form.controls.returnDate.hasError('required')).toBeTrue();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('blocks submit when return date is before journey date', () => {
    component.form.patchValue({
      tripType: 'ROUND_TRIP',
      origin: 'DEL',
      destination: 'BLR',
      journeyDate: '2099-01-10',
      returnDate: '2099-01-09'
    });

    component.submit();

    expect(component.form.controls.returnDate.hasError('dateOrder')).toBeTrue();
  });

  it('blocks submit when journey date is in the past', () => {
    component.form.patchValue({
      tripType: 'ONE_WAY',
      origin: 'DEL',
      destination: 'BLR',
      journeyDate: '2000-01-01'
    });

    component.submit();

    expect(component.form.controls.journeyDate.hasError('pastDate')).toBeTrue();
  });

  it('blocks submit when origin and destination are same', () => {
    component.form.patchValue({
      tripType: 'ONE_WAY',
      origin: 'DEL',
      destination: 'DEL',
      journeyDate: '2099-01-01'
    });

    component.submit();

    expect(component.form.controls.destination.hasError('sameAsOrigin')).toBeTrue();
  });

  it('submits valid request and navigates with uppercase codes', () => {
    component.form.patchValue({
      tripType: 'ONE_WAY',
      origin: 'DEL',
      destination: 'BLR',
      journeyDate: '2099-01-01',
      returnDate: '',
      minPrice: '1000',
      maxPrice: '5000',
      airlineId: '1',
      departureWindow: 'MORNING',
      maxStops: '1',
      seatClass: 'ECONOMY',
      sortBy: 'price_asc'
    });

    component.submit();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/flights/results'], {
      queryParams: jasmine.objectContaining({
        origin: 'DEL',
        destination: 'BLR',
        journeyDate: '2099-01-01'
      })
    });
  });

  it('shows validation error for empty PNR search', () => {
    component.pnrCode = '   ';

    component.findBookingByPnr();

    expect(component.pnrError).toContain('Enter your PNR');
    expect(bookingApiSpy.getBookingByPnr).not.toHaveBeenCalled();
  });

  it('navigates to ticket page when PNR lookup succeeds', () => {
    bookingApiSpy.getBookingByPnr.and.returnValue(of({ bookingId: 'BKG-101' } as any));
    component.pnrCode = 'pnr10';

    component.findBookingByPnr();

    expect(bookingApiSpy.getBookingByPnr).toHaveBeenCalledWith('PNR10');
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/ticket', 'BKG-101']);
    expect(component.isPnrLoading).toBeFalse();
  });

  it('shows error when PNR lookup fails', () => {
    bookingApiSpy.getBookingByPnr.and.returnValue(throwError(() => new Error('not found')));
    component.pnrCode = 'BAD1';

    component.findBookingByPnr();

    expect(component.pnrError).toBe('No booking found for this PNR.');
    expect(component.isPnrLoading).toBeFalse();
  });
});
