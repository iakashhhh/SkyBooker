import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../config/environment';

export interface AdminAnalytics {
  bookingsCount: number;
  paymentsCount: number;
  revenue: number;
  airlinesCount: number;
  airportsCount: number;
}

export interface ManagedFlight {
  flightId: number;
  flightNumber: string;
  airlineId: number;
  originAirportCode: string;
  destinationAirportCode: string;
  departureTime: string;
  arrivalTime: string;
  durationMinutes: number;
  numberOfStops: number;
  viaAirportCode?: string;
  status: string;
  aircraftType: string;
  totalSeats: number;
  availableSeats: number;
  basePrice: number;
}

export interface ManagedFlightRequest {
  flightNumber: string;
  airlineId: number;
  originAirportCode: string;
  destinationAirportCode: string;
  departureTime: string;
  arrivalTime: string;
  durationMinutes: number;
  numberOfStops: number;
  viaAirportCode?: string;
  status: 'ON_TIME' | 'DELAYED' | 'CANCELLED' | 'DEPARTED' | 'ARRIVED';
  aircraftType: string;
  totalSeats: number;
  availableSeats: number;
  basePrice: number;
}

export interface DelayManagedFlightRequest {
  newEstimatedDepartureTime: string;
  delayReason: string;
  internalNotes?: string;
}

export interface CancelManagedBookingRequest {
  bookingId: string;
  reason?: string;
}

export interface StaffFlightDashboard {
  totalFlights: number;
  todayFlights: number;
  bookingsCount: number;
}

export interface StaffAirlineMapping {
  userId: number;
  airlineId: number;
}

@Injectable({
  providedIn: 'root'
})
export class AdminApiService {
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1/admin`;

  constructor(private readonly httpClient: HttpClient) {}

  getUsers(): Observable<Array<Record<string, unknown>>> {
    return this.httpClient.get<Array<Record<string, unknown>>>(`${this.baseUrl}/users`);
  }

  getBookings(): Observable<Array<Record<string, unknown>>> {
    return this.httpClient.get<Array<Record<string, unknown>>>(`${this.baseUrl}/bookings`);
  }

  getManagedBookings(): Observable<Array<Record<string, unknown>>> {
    return this.httpClient.get<Array<Record<string, unknown>>>(`${this.baseUrl}/operations/bookings`);
  }

  getManagedPassengers(): Observable<Array<Record<string, unknown>>> {
    return this.httpClient.get<Array<Record<string, unknown>>>(`${this.baseUrl}/operations/passengers`);
  }

  getPayments(): Observable<Array<Record<string, unknown>>> {
    return this.httpClient.get<Array<Record<string, unknown>>>(`${this.baseUrl}/payments`);
  }

  getAnalytics(): Observable<AdminAnalytics> {
    return this.httpClient.get<AdminAnalytics>(`${this.baseUrl}/analytics`);
  }

  getManagedFlights(date?: string): Observable<ManagedFlight[]> {
    return this.httpClient.get<ManagedFlight[]>(`${this.baseUrl}/flights`, {
      params: date ? { date } : {}
    });
  }

  createManagedFlight(request: ManagedFlightRequest): Observable<ManagedFlight> {
    return this.httpClient.post<ManagedFlight>(`${this.baseUrl}/flights`, request);
  }

  updateManagedFlight(flightId: number, request: ManagedFlightRequest): Observable<ManagedFlight> {
    return this.httpClient.put<ManagedFlight>(`${this.baseUrl}/flights/${flightId}`, request);
  }

  deleteManagedFlight(flightId: number): Observable<void> {
    return this.httpClient.delete<void>(`${this.baseUrl}/flights/${flightId}`);
  }

  cancelManagedFlight(flightId: number): Observable<ManagedFlight> {
    return this.httpClient.put<ManagedFlight>(`${this.baseUrl}/flights/${flightId}/cancel`, {});
  }

  delayManagedFlight(flightId: number, request: DelayManagedFlightRequest): Observable<ManagedFlight> {
    return this.httpClient.put<ManagedFlight>(`${this.baseUrl}/flights/${flightId}/delay`, request);
  }

  cancelManagedBooking(request: CancelManagedBookingRequest): Observable<Record<string, unknown>> {
    return this.httpClient.put<Record<string, unknown>>(`${this.baseUrl}/bookings/${request.bookingId}/cancel`, request);
  }

  getFlightDashboard(): Observable<StaffFlightDashboard> {
    return this.httpClient.get<StaffFlightDashboard>(`${this.baseUrl}/flights/dashboard`);
  }

  getBookingsByFlight(flightId: number): Observable<Array<Record<string, unknown>>> {
    return this.httpClient.get<Array<Record<string, unknown>>>(`${this.baseUrl}/flights/${flightId}/bookings`);
  }

  getPassengersByFlight(flightId: number): Observable<Array<Record<string, unknown>>> {
    return this.httpClient.get<Array<Record<string, unknown>>>(`${this.baseUrl}/flights/${flightId}/passengers`);
  }

  assignStaffAirline(userId: number, airlineId: number): Observable<StaffAirlineMapping> {
    return this.httpClient.put<StaffAirlineMapping>(`${this.baseUrl}/staff-airlines/${userId}`, { airlineId });
  }

  getStaffAirline(userId: number): Observable<StaffAirlineMapping> {
    return this.httpClient.get<StaffAirlineMapping>(`${this.baseUrl}/staff-airlines/${userId}`);
  }
}
