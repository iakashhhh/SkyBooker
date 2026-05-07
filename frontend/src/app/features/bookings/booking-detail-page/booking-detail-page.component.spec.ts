import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';

import { BookingApiService } from '../../../core/services/booking-api.service';
import { PassengerApiService } from '../../../core/services/passenger-api.service';
import { PaymentApiService } from '../../../core/services/payment-api.service';
import { BookingDetailPageComponent } from './booking-detail-page.component';

describe('BookingDetailPageComponent', () => {
  let fixture: ComponentFixture<BookingDetailPageComponent>;
  let component: BookingDetailPageComponent;
  let bookingApiSpy: jasmine.SpyObj<BookingApiService>;
  let passengerApiSpy: jasmine.SpyObj<PassengerApiService>;
  let paymentApiSpy: jasmine.SpyObj<PaymentApiService>;

  beforeEach(async () => {
    bookingApiSpy = jasmine.createSpyObj<BookingApiService>('BookingApiService', ['getBookingById', 'downloadTicketPdf', 'getTicketQrUrl']);
    passengerApiSpy = jasmine.createSpyObj<PassengerApiService>('PassengerApiService', ['getPassengersByBooking']);
    paymentApiSpy = jasmine.createSpyObj<PaymentApiService>('PaymentApiService', ['getPaymentByBooking']);

    bookingApiSpy.getTicketQrUrl.and.returnValue('http://localhost:8080/ticket-qr');

    await TestBed.configureTestingModule({
      imports: [BookingDetailPageComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ id: 'BKG-33' })
            }
          }
        },
        { provide: BookingApiService, useValue: bookingApiSpy },
        { provide: PassengerApiService, useValue: passengerApiSpy },
        { provide: PaymentApiService, useValue: paymentApiSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(BookingDetailPageComponent);
    component = fixture.componentInstance;
  });

  it('loads booking, passengers, and payment details', () => {
    bookingApiSpy.getBookingById.and.returnValue(of({
      bookingId: 'BKG-33',
      pnrCode: 'PNR33',
      status: 'PENDING',
      totalFare: 4200,
      seatIds: [11],
      userId: 9,
      flightId: 88,
      tripType: 'ONE_WAY',
      baseFare: 3000,
      seatCharge: 400,
      baggageCharge: 200,
      mealCharge: 200,
      taxes: 400,
      luggageKg: 10,
      contactEmail: 'a@test.com',
      contactPhone: '9999999999',
      bookedAt: '2026-01-01T00:00:00Z'
    } as any));
    passengerApiSpy.getPassengersByBooking.and.returnValue(of([{ passengerId: 1 }] as any));
    paymentApiSpy.getPaymentByBooking.and.returnValue(of({ paymentId: 'PAY-1', status: 'PENDING' } as any));

    fixture.detectChanges();

    expect(bookingApiSpy.getBookingById).toHaveBeenCalledWith('BKG-33');
    expect(component.passengers.length).toBe(1);
    expect(component.payment?.paymentId).toBe('PAY-1');
    expect(component.qrImageUrl).toBe('http://localhost:8080/ticket-qr');
  });

  it('shows booking load error when API fails', () => {
    bookingApiSpy.getBookingById.and.returnValue(throwError(() => new Error('load failed')));

    fixture.detectChanges();

    expect(component.errorMessage).toBe('Unable to load booking details.');
    expect(component.isLoading).toBeFalse();
  });

  it('clicking download button calls ticket API', () => {
    const blob = new Blob(['ticket']);
    bookingApiSpy.getBookingById.and.returnValue(of({
      bookingId: 'BKG-33',
      pnrCode: 'PNR33',
      status: 'PENDING',
      totalFare: 4200,
      seatIds: [11],
      userId: 9,
      flightId: 88,
      tripType: 'ONE_WAY',
      baseFare: 3000,
      seatCharge: 400,
      baggageCharge: 200,
      mealCharge: 200,
      taxes: 400,
      luggageKg: 10,
      contactEmail: 'a@test.com',
      contactPhone: '9999999999',
      bookedAt: '2026-01-01T00:00:00Z'
    } as any));
    passengerApiSpy.getPassengersByBooking.and.returnValue(of([]));
    paymentApiSpy.getPaymentByBooking.and.returnValue(of(null as any));
    bookingApiSpy.downloadTicketPdf.and.returnValue(of(blob));

    const createUrlSpy = spyOn(URL, 'createObjectURL').and.returnValue('blob:ticket');
    const revokeSpy = spyOn(URL, 'revokeObjectURL');
    fixture.detectChanges();

    const clickSpy = spyOn(HTMLAnchorElement.prototype, 'click').and.callFake(() => {});
    const button = fixture.nativeElement.querySelector('button.cta-button') as HTMLButtonElement;
    button.click();

    expect(bookingApiSpy.downloadTicketPdf).toHaveBeenCalledWith('BKG-33');
    expect(createUrlSpy).toHaveBeenCalled();
    expect(clickSpy).toHaveBeenCalled();
    expect(revokeSpy).toHaveBeenCalledWith('blob:ticket');
  });

  it('shows missing id error when route param is empty', () => {
    const activatedRoute = TestBed.inject(ActivatedRoute) as ActivatedRoute & {
      snapshot: { paramMap: ReturnType<typeof convertToParamMap> };
    };
    activatedRoute.snapshot.paramMap = convertToParamMap({});

    fixture = TestBed.createComponent(BookingDetailPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.errorMessage).toBe('Booking ID is missing.');
    expect(bookingApiSpy.getBookingById).not.toHaveBeenCalled();
  });

  it('shows error when ticket download fails', () => {
    bookingApiSpy.getBookingById.and.returnValue(of({
      bookingId: 'BKG-33',
      pnrCode: 'PNR33',
      status: 'PENDING',
      totalFare: 4200,
      seatIds: [11],
      userId: 9,
      flightId: 88,
      tripType: 'ONE_WAY',
      baseFare: 3000,
      seatCharge: 400,
      baggageCharge: 200,
      mealCharge: 200,
      taxes: 400,
      luggageKg: 10,
      contactEmail: 'a@test.com',
      contactPhone: '9999999999',
      bookedAt: '2026-01-01T00:00:00Z'
    } as any));
    passengerApiSpy.getPassengersByBooking.and.returnValue(of([]));
    paymentApiSpy.getPaymentByBooking.and.returnValue(of(null as any));
    bookingApiSpy.downloadTicketPdf.and.returnValue(throwError(() => new Error('pdf fail')));

    fixture.detectChanges();
    component.downloadTicket();

    expect(component.errorMessage).toBe('Unable to download ticket PDF right now.');
  });
});
