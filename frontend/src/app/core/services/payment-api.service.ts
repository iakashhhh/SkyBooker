import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../config/environment';
import {
  InitiatePaymentRequest,
  PaymentKeyResponse,
  PaymentResponse,
  ProcessPaymentRequest,
  RefundPaymentRequest,
  VerifyPaymentRequest
} from '../models/payment.models';

@Injectable({
  providedIn: 'root'
})
export class PaymentApiService {
  private readonly baseUrl = `${environment.apiBaseUrl}/payments`;

  constructor(private readonly httpClient: HttpClient) {}

  initiatePayment(request: InitiatePaymentRequest): Observable<PaymentResponse> {
    return this.httpClient.post<PaymentResponse>(`${this.baseUrl}/initiate`, request);
  }

  processPayment(request: ProcessPaymentRequest): Observable<PaymentResponse> {
    return this.httpClient.post<PaymentResponse>(`${this.baseUrl}/process`, request);
  }

  verifyPayment(request: VerifyPaymentRequest): Observable<PaymentResponse> {
    return this.httpClient.post<PaymentResponse>(`${this.baseUrl}/verify`, request);
  }

  refundPayment(request: RefundPaymentRequest): Observable<PaymentResponse> {
    return this.httpClient.post<PaymentResponse>(`${this.baseUrl}/refund`, request);
  }

  getPaymentKey(): Observable<PaymentKeyResponse> {
    return this.httpClient.get<PaymentKeyResponse>(`${this.baseUrl}/key`);
  }

  getPaymentByBooking(bookingId: string): Observable<PaymentResponse> {
    return this.httpClient.get<PaymentResponse>(`${this.baseUrl}/booking/${bookingId}`);
  }
}
