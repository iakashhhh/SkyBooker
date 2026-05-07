import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../config/environment';
import { BookingResponse, CreateBookingRequest, UpdateBookingFareRequest } from '../models/booking.models';

/**
 * Calls booking-service APIs through API gateway.
 */
@Injectable({
  providedIn: 'root'
})
export class BookingApiService {
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1/bookings`;

  constructor(private readonly httpClient: HttpClient) {}

  createBooking(request: CreateBookingRequest): Observable<BookingResponse> {
    return this.httpClient.post<BookingResponse>(this.baseUrl, request);
  }

  getBookingById(bookingId: string): Observable<BookingResponse> {
    return this.httpClient.get<BookingResponse>(`${this.baseUrl}/${bookingId}`);
  }

  getBookingByPnr(pnrCode: string): Observable<BookingResponse> {
    return this.httpClient.get<BookingResponse>(`${this.baseUrl}/pnr/${pnrCode}`);
  }

  getBookingsByUser(userId: number): Observable<BookingResponse[]> {
    return this.httpClient.get<BookingResponse[]>(`${this.baseUrl}/user/${userId}`);
  }

  getAllBookings(): Observable<BookingResponse[]> {
    return this.httpClient.get<BookingResponse[]>(this.baseUrl);
  }

  updateBookingFare(bookingId: string, request: UpdateBookingFareRequest): Observable<BookingResponse> {
    return this.httpClient.put<BookingResponse>(`${this.baseUrl}/${bookingId}/fare`, request);
  }

  cancelBooking(bookingId: string): Observable<BookingResponse> {
    return this.httpClient.put<BookingResponse>(`${this.baseUrl}/cancel`, { bookingId });
  }

  downloadTicketPdf(bookingId: string): Observable<Blob> {
    return this.httpClient.get(`${this.baseUrl}/${bookingId}/ticket/pdf`, { responseType: 'blob' });
  }

  getTicketQrUrl(bookingId: string): string {
    return `${this.baseUrl}/${bookingId}/ticket/qr`;
  }
}
