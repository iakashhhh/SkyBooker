import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../config/environment';

export interface AirlineRecord {
  airlineId?: number;
  name: string;
  iataCode: string;
  country: string;
  active?: boolean;
  isActive?: boolean;
}

export interface AirportRecord {
  airportId?: number;
  name: string;
  iataCode: string;
  city: string;
  country: string;
  timezone: string;
  latitude: number;
  longitude: number;
}

@Injectable({
  providedIn: 'root'
})
export class AirlineAirportApiService {
  private readonly apiBaseUrl = environment.apiBaseUrl;

  constructor(private readonly httpClient: HttpClient) {}

  getAirlines(): Observable<AirlineRecord[]> {
    return this.httpClient.get<AirlineRecord[]>(`${this.apiBaseUrl}/airlines`);
  }

  createAirline(request: AirlineRecord): Observable<AirlineRecord> {
    return this.httpClient.post<AirlineRecord>(`${this.apiBaseUrl}/airlines`, request);
  }

  updateAirline(airlineId: number, request: AirlineRecord): Observable<AirlineRecord> {
    return this.httpClient.put<AirlineRecord>(`${this.apiBaseUrl}/airlines/${airlineId}`, request);
  }

  deleteAirline(airlineId: number): Observable<void> {
    return this.httpClient.delete<void>(`${this.apiBaseUrl}/airlines/${airlineId}`);
  }

  getAirports(): Observable<AirportRecord[]> {
    return this.httpClient.get<AirportRecord[]>(`${this.apiBaseUrl}/airports`);
  }

  searchAirports(query: string): Observable<AirportRecord[]> {
    return this.httpClient.get<AirportRecord[]>(`${this.apiBaseUrl}/airports/search`, {
      params: { query }
    });
  }

  createAirport(request: AirportRecord): Observable<AirportRecord> {
    return this.httpClient.post<AirportRecord>(`${this.apiBaseUrl}/airports`, request);
  }

  updateAirport(airportId: number, request: AirportRecord): Observable<AirportRecord> {
    return this.httpClient.put<AirportRecord>(`${this.apiBaseUrl}/airports/${airportId}`, request);
  }

  deleteAirport(airportId: number): Observable<void> {
    return this.httpClient.delete<void>(`${this.apiBaseUrl}/airports/${airportId}`);
  }
}
