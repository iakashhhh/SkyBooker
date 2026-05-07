import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';

import { BookingApiService } from '../../../core/services/booking-api.service';
import { PassengerApiService } from '../../../core/services/passenger-api.service';
import { BookingSuccessPageComponent } from './booking-success-page.component';

describe('BookingSuccessPageComponent', () => {
  let fixture: ComponentFixture<BookingSuccessPageComponent>;
  let component: BookingSuccessPageComponent;
  let bookingApiSpy: jasmine.SpyObj<BookingApiService>;
  let passengerApiSpy: jasmine.SpyObj<PassengerApiService>;

  beforeEach(async () => {
    bookingApiSpy = jasmine.createSpyObj<BookingApiService>('BookingApiService', ['getBookingById', 'getBookingByPnr', 'downloadTicketPdf', 'getTicketQrUrl']);
    passengerApiSpy = jasmine.createSpyObj<PassengerApiService>('PassengerApiService', ['getPassengersByBooking']);

    bookingApiSpy.getTicketQrUrl.and.returnValue('http://localhost:8080/qr/BKG-1');

    await TestBed.configureTestingModule({
      imports: [BookingSuccessPageComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap({ bookingId: 'BKG-1', pnr: 'PNR-1' })
            }
          }
        },
        { provide: BookingApiService, useValue: bookingApiSpy },
        { provide: PassengerApiService, useValue: passengerApiSpy }
      ]
    })
      .overrideComponent(BookingSuccessPageComponent, { set: { template: '' } })
      .compileComponents();

    fixture = TestBed.createComponent(BookingSuccessPageComponent);
    component = fixture.componentInstance;
  });

  it('loads booking and passengers from booking id', () => {
    bookingApiSpy.getBookingById.and.returnValue(of({ bookingId: 'BKG-1', pnrCode: 'PNR-1' } as any));
    passengerApiSpy.getPassengersByBooking.and.returnValue(of([{ passengerId: 1 }] as any));

    fixture.detectChanges();

    expect(bookingApiSpy.getBookingById).toHaveBeenCalledWith('BKG-1');
    expect(passengerApiSpy.getPassengersByBooking).toHaveBeenCalledWith('BKG-1');
    expect(component.passengers.length).toBe(1);
    expect(component.qrImageUrl).toContain('/qr/BKG-1');
  });

  it('shows error when no booking reference exists', () => {
    const route = TestBed.inject(ActivatedRoute) as ActivatedRoute & {
      snapshot: { queryParamMap: ReturnType<typeof convertToParamMap> };
    };
    route.snapshot.queryParamMap = convertToParamMap({});

    fixture = TestBed.createComponent(BookingSuccessPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.errorMessage).toBe('No booking reference found.');
    expect(bookingApiSpy.getBookingById).not.toHaveBeenCalled();
  });

  it('shows API error when booking confirmation load fails', () => {
    bookingApiSpy.getBookingById.and.returnValue(throwError(() => new Error('down')));

    fixture.detectChanges();

    expect(component.errorMessage).toBe('Unable to load booking confirmation details.');
    expect(component.isLoading).toBeFalse();
  });

  it('downloadTicket handles API failure by setting error message', () => {
    component.bookingId = 'BKG-1';
    bookingApiSpy.downloadTicketPdf.and.returnValue(throwError(() => new Error('pdf failed')));

    component.downloadTicket();

    expect(component.errorMessage).toBe('Unable to download ticket PDF right now.');
  });
});
