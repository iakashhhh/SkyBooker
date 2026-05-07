import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { AirlineAirportApiService } from '../../../core/services/airline-airport-api.service';
import { FlightApiService } from '../../../core/services/flight-api.service';
import { FlightResultsComponent } from './flight-results.component';

describe('FlightResultsComponent', () => {
  let fixture: ComponentFixture<FlightResultsComponent>;
  let component: FlightResultsComponent;
  let routerSpy: jasmine.SpyObj<Router>;
  let flightApiSpy: jasmine.SpyObj<FlightApiService>;
  let airlineAirportSpy: jasmine.SpyObj<AirlineAirportApiService>;

  const baseParams: Record<string, string> = {
    tripType: 'ONE_WAY',
    origin: 'DEL',
    destination: 'BLR',
    journeyDate: '2099-01-10',
    seatClass: 'ECONOMY',
    sortBy: 'price_asc',
    maxStops: '0',
    minPrice: '0',
    maxPrice: '5000'
  };

  const queryParamMap$ = new BehaviorSubject(convertToParamMap(baseParams));
  const routeMock: any = {
    snapshot: { queryParams: { ...baseParams } },
    queryParamMap: queryParamMap$.asObservable()
  };

  const oneWayFlights = [
    {
      flightId: 1,
      flightNumber: 'SB101',
      originAirportCode: 'DEL',
      destinationAirportCode: 'BLR',
      departureTime: '2099-01-10T10:00:00Z',
      arrivalTime: '2099-01-10T12:00:00Z',
      displayedPrice: 4500,
      availableSeats: 5,
      totalSeats: 180,
      durationMinutes: 120,
      airlineId: 1,
      aircraftType: 'A320'
    }
  ] as any;

  beforeEach(async () => {
    routeMock.snapshot.queryParams = { ...baseParams };
    queryParamMap$.next(convertToParamMap(baseParams));

    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);
    Object.defineProperty(routerSpy, 'url', { value: '/flights/results?x=1' });

    flightApiSpy = jasmine.createSpyObj<FlightApiService>('FlightApiService', ['searchOneWay', 'searchRoundTrip']);
    airlineAirportSpy = jasmine.createSpyObj<AirlineAirportApiService>('AirlineAirportApiService', ['getAirports', 'getAirlines']);

    flightApiSpy.searchOneWay.and.returnValue(of(oneWayFlights));
    flightApiSpy.searchRoundTrip.and.returnValue(of({ outboundFlights: oneWayFlights, returnFlights: [] } as any));
    airlineAirportSpy.getAirports.and.returnValue(of([
      { iataCode: 'DEL', city: 'Delhi', name: 'Indira Gandhi' },
      { iataCode: 'BLR', city: 'Bengaluru', name: 'Kempegowda' }
    ] as any));
    airlineAirportSpy.getAirlines.and.returnValue(of([
      { airlineId: 1, name: 'Sky Air', active: true },
      { airlineId: 2, name: 'Blue Air', active: true }
    ] as any));

    await TestBed.configureTestingModule({
      imports: [FlightResultsComponent],
      providers: [
        { provide: ActivatedRoute, useValue: routeMock },
        { provide: Router, useValue: routerSpy },
        { provide: FlightApiService, useValue: flightApiSpy },
        { provide: AirlineAirportApiService, useValue: airlineAirportSpy }
      ]
    })
      .overrideComponent(FlightResultsComponent, { set: { template: '<button id="apply" (click)="applyFilters()">Apply</button>' } })
      .compileComponents();

    fixture = TestBed.createComponent(FlightResultsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads one-way results on init and maps filter state', () => {
    expect(flightApiSpy.searchOneWay).toHaveBeenCalled();
    expect(component.outboundFlights.length).toBe(1);
    expect(component.filterOrigin).toBe('DEL');
    expect(component.errorMessage).toBe('');
  });

  it('shows API error when one-way search fails', () => {
    routeMock.snapshot.queryParams = { ...baseParams, journeyDate: '2099-02-01' };
    flightApiSpy.searchOneWay.and.returnValue(throwError(() => ({ error: { message: 'search failed' } })));

    queryParamMap$.next(convertToParamMap(routeMock.snapshot.queryParams));

    expect(component.errorMessage).toBe('search failed');
    expect(component.isLoading).toBeFalse();
  });

  it('validates filters and blocks when origin/destination are same', () => {
    component.airportOptions = [
      { code: 'DEL', city: 'Delhi', airport: 'Delhi Airport' },
      { code: 'BLR', city: 'Bengaluru', airport: 'Bengaluru Airport' }
    ];
    component.filterOrigin = 'DEL';
    component.filterDestination = 'DEL';
    component.filterJourneyDate = '2099-01-05';
    component.filterTripType = 'ONE_WAY';

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('#apply')?.click();

    expect(component.errorMessage).toContain('cannot be the same');
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('blocks filters when round-trip return date is not after journey date', () => {
    component.filterTripType = 'ROUND_TRIP';
    component.filterJourneyDate = '2099-01-10';
    component.filterReturnDate = '2099-01-10';

    component.applyFilters();

    expect(component.errorMessage).toContain('Return date must be after the departure date');
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('applies valid filters and navigates with resolved airport codes', () => {
    component.airportOptions = [
      { code: 'DEL', city: 'Delhi', airport: 'Delhi Airport' },
      { code: 'BLR', city: 'Bengaluru', airport: 'Bengaluru Airport' }
    ];
    component.filterOrigin = 'Delhi';
    component.filterDestination = 'Bengaluru';
    component.filterJourneyDate = '2099-01-05';
    component.filterReturnDate = '2099-01-06';
    component.filterTripType = 'ROUND_TRIP';
    component.filterMinPrice = 5000;
    component.filterMaxPrice = 2000;

    component.applyFilters();

    expect(routerSpy.navigate).toHaveBeenCalledWith([], {
      relativeTo: jasmine.anything(),
      queryParams: jasmine.objectContaining({
        origin: 'DEL',
        destination: 'BLR',
        minPrice: 2000,
        maxPrice: 5000,
        tripType: 'ROUND_TRIP'
      })
    });
  });

  it('blocks filters when airport values cannot be resolved', () => {
    component.airportOptions = [{ code: 'DEL', city: 'Delhi', airport: 'Delhi Airport' }];
    component.filterOrigin = 'Unknown City';
    component.filterDestination = 'Also Unknown';
    component.filterJourneyDate = '2099-01-05';

    component.applyFilters();

    expect(component.errorMessage).toContain('Please enter supported city names');
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('builds compact pagination buttons with ellipsis', () => {
    component.selectedPageSize = 10;
    component.outboundFlights = Array.from({ length: 400 }, (_, index) => ({ ...oneWayFlights[0], flightId: index + 1 }));
    component.outboundCurrentPage = 20;

    const buttons = component.getVisiblePageButtons('OUTBOUND');

    expect(buttons).toEqual([1, '...', 18, 19, 20, 21, 22, '...', 40]);
  });

  it('builds page buttons near start and end without duplicates', () => {
    component.selectedPageSize = 10;
    component.outboundFlights = Array.from({ length: 400 }, (_, index) => ({ ...oneWayFlights[0], flightId: index + 1 }));

    component.outboundCurrentPage = 2;
    expect(component.getVisiblePageButtons('OUTBOUND')).toEqual([1, 2, 3, 4, '...', 40]);

    component.outboundCurrentPage = 39;
    expect(component.getVisiblePageButtons('OUTBOUND')).toEqual([1, '...', 37, 38, 39, 40]);
  });

  it('changes page size and showing text correctly', () => {
    component.outboundFlights = Array.from({ length: 55 }, (_, index) => ({ ...oneWayFlights[0], flightId: index + 1 }));
    component.onPageSizeChange('25');

    expect(component.selectedPageSize).toBe(25);
    expect(component.getShowingText('OUTBOUND')).toBe('Showing 1-25 of 55 flights');

    component.goToPage('OUTBOUND', 3);
    expect(component.getShowingText('OUTBOUND')).toBe('Showing 51-55 of 55 flights');
  });

  it('falls back to default page size for invalid input', () => {
    component.onPageSizeChange('999');
    expect(component.selectedPageSize).toBe(10);
  });

  it('disables previous and next at bounds', () => {
    component.outboundFlights = Array.from({ length: 12 }, (_, index) => ({ ...oneWayFlights[0], flightId: index + 1 }));
    component.selectedPageSize = 10;
    component.outboundCurrentPage = 1;

    expect(component.isPreviousDisabled('OUTBOUND')).toBeTrue();

    component.goToNextPage('OUTBOUND');
    expect(component.outboundCurrentPage).toBe(2);
    expect(component.isNextDisabled('OUTBOUND')).toBeTrue();
  });

  it('ignores ellipsis clicks in pagination', () => {
    component.outboundCurrentPage = 4;
    component.goToPage('OUTBOUND', '...');
    expect(component.outboundCurrentPage).toBe(4);
  });

  it('routes to seat selection with selected flight details', () => {
    component.selectedFlight = oneWayFlights[0];

    component.goToSeatSelection();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/seats/select'], {
      queryParams: jasmine.objectContaining({
        flightId: 1,
        flightNumber: 'SB101',
        origin: 'DEL',
        destination: 'BLR'
      })
    });
  });

  it('selects flexible one-way date and updates outbound results', fakeAsync(() => {
    (component as any).flexibleOneWayResults = {
      '2099-03-01': [{ ...oneWayFlights[0], flightId: 9 }]
    };
    component.tripType = 'ONE_WAY';

    component.selectFlexibleDate('2099-03-01');
    tick();

    expect(component.outboundFlights[0].flightId).toBe(9);
    expect(component.outboundCurrentPage).toBe(1);
  }));

  it('shows round-trip API failure message', () => {
    routeMock.snapshot.queryParams = {
      ...baseParams,
      tripType: 'ROUND_TRIP',
      returnDate: '2099-01-15'
    };
    flightApiSpy.searchRoundTrip.and.returnValue(throwError(() => ({ error: { message: 'round trip failed' } })));

    queryParamMap$.next(convertToParamMap(routeMock.snapshot.queryParams));

    expect(component.errorMessage).toBe('round trip failed');
    expect(component.isLoading).toBeFalse();
  });

  it('handles flexible search without month as validation error', async () => {
    await (component as any).loadFlexibleOneWayResults({
      origin: 'DEL',
      destination: 'BLR',
      flexibleDate: 'true'
    });

    expect(component.errorMessage).toContain('without a departure month');
    expect(component.isLoading).toBeFalse();
  });

  it('validates past journey date and stops loading results', () => {
    routeMock.snapshot.queryParams = {
      ...baseParams,
      journeyDate: '2000-01-01'
    };

    (component as any).loadResults();

    expect(component.errorMessage).toContain('cannot be in the past');
    expect(component.isLoading).toBeFalse();
  });

  it('returns empty pagination and showing text for empty results', () => {
    component.outboundFlights = [];
    component.outboundCurrentPage = 1;

    expect(component.getVisiblePageButtons('OUTBOUND')).toEqual([]);
    expect(component.getShowingText('OUTBOUND')).toBe('Showing 0-0 of 0 flights');
  });

  it('opens and closes flight details and ignores seat selection when no flight selected', () => {
    expect(component.isDetailsOpen).toBeFalse();
    component.openFlightDetails(oneWayFlights[0], 'OUTBOUND');
    expect(component.isDetailsOpen).toBeTrue();
    expect(component.selectedFlight?.flightId).toBe(1);

    component.closeFlightDetails();
    expect(component.isDetailsOpen).toBeFalse();
    expect(component.selectedFlight).toBeUndefined();

    routerSpy.navigate.calls.reset();
    component.goToSeatSelection();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('resolves airport names, flight segments and seat fallbacks', () => {
    component.airportOptions = [
      { code: 'DEL', city: 'Delhi', airport: 'Indira Gandhi' },
      { code: 'BLR', city: 'Bengaluru', airport: 'Kempegowda' }
    ];
    component.airlineNameById = { 1: 'Sky Air' };

    expect(component.getAirportName('DEL')).toBe('Indira Gandhi');
    expect(component.getAirportName('CCU')).toBe('CCU Airport');
    expect(component.getAirlineName({ airlineId: 1 } as any)).toBe('Sky Air');
    expect(component.getAirlineName({ airlineId: 99 } as any)).toBe('Airline 99');
    expect(component.getFlightSegments(undefined)).toEqual([]);
    expect(component.getSeatsLeft({ availableSeats: -3 } as any)).toBe(0);
    expect(component.formatFlightDuration(135)).toBe('2h 15m');
  });

  it('handles filter city suggestions and hides dropdown on blur', fakeAsync(() => {
    component.airportOptions = [
      { code: 'DEL', city: 'Delhi', airport: 'Indira Gandhi' },
      { code: 'BLR', city: 'Bengaluru', airport: 'Kempegowda' }
    ];

    component.onFilterCityFocus('origin');
    expect(component.showFilterCityDropdown('origin')).toBeTrue();

    component.onFilterCityInput('origin', 'Ben');
    expect(component.filterOrigin).toBe('Ben');
    expect(component.filterCitySuggestions.origin.length).toBe(1);

    component.selectFilterCity('origin', component.filterCitySuggestions.origin[0]);
    expect(component.filterOrigin).toBe('Bengaluru (BLR)');
    expect(component.showFilterCityDropdown('origin')).toBeFalse();

    component.onFilterCityFocus('destination');
    component.onFilterCityBlur('destination');
    tick(130);
    expect(component.showFilterCityDropdown('destination')).toBeFalse();
  }));

  it('resets filters and applies defaults via navigation', () => {
    component.filterOrigin = 'DEL';
    component.filterDestination = 'BLR';
    component.filterJourneyDate = '2099-01-10';

    component.resetFilters();

    expect(routerSpy.navigate).toHaveBeenCalled();
    const navigationArg = routerSpy.navigate.calls.mostRecent().args[1]?.queryParams ?? {};
    expect(navigationArg['maxStops']).toBe('0');
    expect(navigationArg['sortBy']).toBe('price_asc');
  });

  it('maps flexible round-trip date selection into outbound and return lists', () => {
    component.tripType = 'ROUND_TRIP';
    (component as any).flexibleRoundTripResults = {
      '2099-06-01': {
        outboundFlights: [{ ...oneWayFlights[0], flightId: 41 }],
        returnFlights: [{ ...oneWayFlights[0], flightId: 42 }]
      }
    };

    component.selectFlexibleDate('2099-06-01');

    expect(component.outboundFlights[0].flightId).toBe(41);
    expect(component.returnFlights[0].flightId).toBe(42);
    expect(component.outboundCurrentPage).toBe(1);
    expect(component.returnCurrentPage).toBe(1);
  });

  it('handles flexible round-trip search with no month and sets validation error', async () => {
    await (component as any).loadFlexibleRoundTripResults({
      origin: 'DEL',
      destination: 'BLR',
      flexibleDate: 'true'
    });

    expect(component.errorMessage).toContain('without a departure month');
    expect(component.isLoading).toBeFalse();
  });

  it('sends undefined seatClass and departureWindow for invalid query values', () => {
    routeMock.snapshot.queryParams = {
      ...baseParams,
      seatClass: 'INVALID_CLASS',
      departureWindow: 'MIDNIGHT'
    };

    (component as any).loadResults();

    const request = flightApiSpy.searchOneWay.calls.mostRecent().args[0];
    expect(request.seatClass).toBeUndefined();
    expect(request.departureWindow).toBeUndefined();
  });
});
