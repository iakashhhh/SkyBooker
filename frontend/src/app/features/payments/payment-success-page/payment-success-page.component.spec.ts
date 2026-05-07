import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';

import { BookingApiService } from '../../../core/services/booking-api.service';
import { PaymentApiService } from '../../../core/services/payment-api.service';
import { PaymentSuccessPageComponent } from './payment-success-page.component';

describe('PaymentSuccessPageComponent', () => {
  let fixture: ComponentFixture<PaymentSuccessPageComponent>;
  let component: PaymentSuccessPageComponent;
  let bookingApiSpy: jasmine.SpyObj<BookingApiService>;
  let paymentApiSpy: jasmine.SpyObj<PaymentApiService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    bookingApiSpy = jasmine.createSpyObj<BookingApiService>('BookingApiService', ['getBookingById']);
    paymentApiSpy = jasmine.createSpyObj<PaymentApiService>('PaymentApiService', ['getPaymentByBooking']);
    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [PaymentSuccessPageComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap({ bookingId: 'BKG-1', pnr: 'PNR1', paymentId: '', status: 'SUCCESS' })
            }
          }
        },
        { provide: Router, useValue: routerSpy },
        { provide: BookingApiService, useValue: bookingApiSpy },
        { provide: PaymentApiService, useValue: paymentApiSpy }
      ]
    })
      .overrideComponent(PaymentSuccessPageComponent, { set: { template: '' } })
      .compileComponents();

    fixture = TestBed.createComponent(PaymentSuccessPageComponent);
    component = fixture.componentInstance;
  });

  it('loads booking and payment receipt on success flow', () => {
    bookingApiSpy.getBookingById.and.returnValue(of({ bookingId: 'BKG-1', pnrCode: 'PNR-1', paymentId: 'PAY-1' } as any));
    paymentApiSpy.getPaymentByBooking.and.returnValue(of({ paymentId: 'PAY-1', status: 'PAID' } as any));

    fixture.detectChanges();

    expect(bookingApiSpy.getBookingById).toHaveBeenCalledWith('BKG-1');
    expect(paymentApiSpy.getPaymentByBooking).toHaveBeenCalledWith('BKG-1');
    expect(component.pnr).toBe('PNR-1');
    expect(component.paymentId).toBe('PAY-1');
    expect(component.status).toBe('PAID');
  });

  it('shows error when booking id is missing', () => {
    const route = TestBed.inject(ActivatedRoute) as ActivatedRoute & {
      snapshot: { queryParamMap: ReturnType<typeof convertToParamMap> };
    };
    route.snapshot.queryParamMap = convertToParamMap({});

    fixture = TestBed.createComponent(PaymentSuccessPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.errorMessage).toContain('Booking reference is missing');
    expect(bookingApiSpy.getBookingById).not.toHaveBeenCalled();
  });

  it('shows receipt error when booking API fails', () => {
    bookingApiSpy.getBookingById.and.returnValue(throwError(() => new Error('down')));

    fixture.detectChanges();

    expect(component.errorMessage).toBe('Unable to load payment receipt details.');
    expect(component.isLoading).toBeFalse();
  });

  it('continueToConfirmation navigates when booking id exists', () => {
    component.bookingId = 'BKG-1';
    component.pnr = 'PNR-1';

    component.continueToConfirmation();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/booking-success'], {
      queryParams: { bookingId: 'BKG-1', pnr: 'PNR-1' }
    });
  });
});
