import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../config/environment';

export interface SupportInquiryRequest {
  fullName: string;
  email: string;
  phone?: string;
  bookingId?: string;
  category: string;
  subject: string;
  message: string;
}

export interface SupportInquiryResponse {
  message: string;
  ticketRef: string;
}

@Injectable({
  providedIn: 'root'
})
export class SupportApiService {
  private readonly baseUrl = `${environment.apiBaseUrl}/notifications/support`;

  constructor(private readonly httpClient: HttpClient) {}

  submitInquiry(request: SupportInquiryRequest): Observable<SupportInquiryResponse> {
    return this.httpClient.post<SupportInquiryResponse>(this.baseUrl, request);
  }
}
