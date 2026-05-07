import { ChangeDetectorRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';

import { BookingApiService } from '../../../core/services/booking-api.service';
import { PaymentFailedPageComponent } from './payment-failed-page.component';

describe('PaymentFailedPageComponent', () => {
  let fixture: ComponentFixture<PaymentFailedPageComponent>;
  let component: PaymentFailedPageComponent;
  let bookingApiSpy: jasmine.SpyObj<BookingApiService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    bookingApiSpy = jasmine.createSpyObj<BookingApiService>('BookingApiService', ['getBookingById', 'getBookingByPnr']);
    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [PaymentFailedPageComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap({ bookingId: 'BKG-1', pnr: 'PNR-1', amount: '2300' })
            }
          }
        },
        { provide: Router, useValue: routerSpy },
        { provide: BookingApiService, useValue: bookingApiSpy },
        { provide: ChangeDetectorRef, useValue: { markForCheck: () => {} } }
      ]
    })
      .overrideComponent(PaymentFailedPageComponent, { set: { template: '' } })
      .compileComponents();

    fixture = TestBed.createComponent(PaymentFailedPageComponent);
    component = fixture.componentInstance;
  });

  it('loads booking details on init when booking id exists', () => {
    bookingApiSpy.getBookingById.and.returnValue(of({ bookingId: 'BKG-1', pnrCode: 'PNR-1', totalFare: 2600, status: 'FAILED' } as any));

    fixture.detectChanges();

    expect(bookingApiSpy.getBookingById).toHaveBeenCalledWith('BKG-1');
    expect(component.amount).toBe(2600);
    expect(component.pnr).toBe('PNR-1');
  });

  it('falls back to booking-by-pnr when booking id lookup fails', () => {
    bookingApiSpy.getBookingById.and.returnValue(throwError(() => new Error('id failed')));
    bookingApiSpy.getBookingByPnr.and.returnValue(of({ bookingId: 'BKG-2', pnrCode: 'PNR-1', totalFare: 3000, status: 'PENDING' } as any));

    fixture.detectChanges();

    expect(bookingApiSpy.getBookingByPnr).toHaveBeenCalledWith('PNR-1');
    expect(component.booking?.bookingId).toBe('BKG-2');
  });

  it('retryPayment navigates to payment route with booking context', () => {
    component.bookingId = 'BKG-9';
    component.pnr = 'PNR-9';
    component.amount = 5000;

    component.retryPayment();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/payment'], {
      queryParams: { bookingId: 'BKG-9', pnr: 'PNR-9', amount: 5000 }
    });
  });

  it('navigateIfNoReference routes to bookings when no references are available', () => {
    component.bookingId = '';
    component.pnr = '';

    component.navigateIfNoReference();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/my-bookings']);
  });
});
