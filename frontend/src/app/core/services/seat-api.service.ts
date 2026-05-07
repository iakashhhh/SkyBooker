import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../config/environment';
import { SeatActionRequest, SeatActionResponse, SeatMapResponse } from '../models/seat.models';

/**
 * Calls backend seat-service APIs through API gateway.
 */
@Injectable({
  providedIn: 'root'
})
export class SeatApiService {
  private readonly baseUrl = `${environment.apiBaseUrl}/seats`;

  constructor(private readonly httpClient: HttpClient) {}

  getSeatMap(flightId: number, totalSeats?: number): Observable<SeatMapResponse> {
    if (!totalSeats) {
      return this.httpClient.get<SeatMapResponse>(`${this.baseUrl}/${flightId}`);
    }

    return this.httpClient.get<SeatMapResponse>(`${this.baseUrl}/${flightId}`, {
      params: { totalSeats: String(totalSeats) }
    });
  }

  holdSeats(request: SeatActionRequest): Observable<SeatActionResponse> {
    return this.httpClient.post<SeatActionResponse>(`${this.baseUrl}/hold`, request);
  }

  releaseSeats(request: SeatActionRequest): Observable<SeatActionResponse> {
    return this.httpClient.post<SeatActionResponse>(`${this.baseUrl}/release`, request);
  }

  confirmSeats(request: SeatActionRequest): Observable<SeatActionResponse> {
    return this.httpClient.post<SeatActionResponse>(`${this.baseUrl}/confirm`, request);
  }
}
