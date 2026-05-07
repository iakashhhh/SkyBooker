import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../config/environment';
import {
  FlightResponse,
  OneWaySearchParams,
  RoundTripSearchParams,
  RoundTripSearchResponse
} from '../models/flight.models';

/**
 * This service calls flight search APIs through API gateway.
 */
@Injectable({
  providedIn: 'root'
})
export class FlightApiService {
  private readonly baseUrl = `${environment.apiBaseUrl}/flights`;

  constructor(private readonly httpClient: HttpClient) {}

  searchOneWay(params: OneWaySearchParams): Observable<FlightResponse[]> {
    return this.httpClient.get<FlightResponse[]>(`${this.baseUrl}/search`, {
      params: this.toHttpParams(params)
    });
  }

  searchRoundTrip(params: RoundTripSearchParams): Observable<RoundTripSearchResponse> {
    return this.httpClient.get<RoundTripSearchResponse>(`${this.baseUrl}/search/round-trip`, {
      params: this.toHttpParams(params)
    });
  }

  getFlightById(flightId: number): Observable<FlightResponse> {
    return this.httpClient.get<FlightResponse>(`${this.baseUrl}/${flightId}`);
  }

  private toHttpParams(params: object): HttpParams {
    let httpParams = new HttpParams();

    Object.entries(params as Record<string, unknown>).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        httpParams = httpParams.set(key, String(value));
      }
    });

    return httpParams;
  }
}
