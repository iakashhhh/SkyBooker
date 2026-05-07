import { HttpErrorResponse } from '@angular/common/http';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { NotificationApiService } from './notification-api.service';

describe('NotificationApiService', () => {
  let service: NotificationApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [NotificationApiService, provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(NotificationApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('fetches notifications by user id', () => {
    const notifications = [{ notificationId: 11, title: 'Payment received' }] as any;

    service.getByUser(42).subscribe((response) => {
      expect(response).toEqual(notifications);
    });

    const req = httpMock.expectOne('http://localhost:8080/notifications/user/42');
    expect(req.request.method).toBe('GET');
    req.flush(notifications);
  });

  it('marks a notification as read', () => {
    service.markRead(15).subscribe((response) => {
      expect(response.notificationId).toBe(15);
      expect(response.read).toBeTrue();
    });

    const req = httpMock.expectOne('http://localhost:8080/notifications/read/15');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({});
    req.flush({
      notificationId: 15,
      recipientId: 42,
      type: 'BOOKING_CONFIRMED',
      message: 'Ticket issued',
      channel: 'APP',
      read: true,
      createdAt: '2026-05-02T12:00:00Z'
    });
  });

  it('propagates read failure to caller', () => {
    let capturedError: HttpErrorResponse | undefined;

    service.markRead(999).subscribe({
      next: () => fail('expected error'),
      error: (error) => {
        capturedError = error;
      }
    });

    const req = httpMock.expectOne('http://localhost:8080/notifications/read/999');
    req.flush({ message: 'missing' }, { status: 404, statusText: 'Not Found' });

    expect(capturedError?.status).toBe(404);
  });
});
