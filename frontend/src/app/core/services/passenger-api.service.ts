import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../config/environment';
import { AssignSeatRequest, PassengerRequest, PassengerResponse } from '../models/passenger.models';

@Injectable({
  providedIn: 'root'
})
export class PassengerApiService {
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1/passengers`;

  constructor(private readonly httpClient: HttpClient) {}

  addPassenger(request: PassengerRequest): Observable<PassengerResponse> {
    return this.httpClient.post<PassengerResponse>(`${this.baseUrl}/add`, request);
  }

  getPassengersByBooking(bookingId: string): Observable<PassengerResponse[]> {
    return this.httpClient.get<PassengerResponse[]>(`${this.baseUrl}/booking/${bookingId}`);
  }

  updatePassenger(passengerId: number, request: PassengerRequest): Observable<PassengerResponse> {
    return this.httpClient.put<PassengerResponse>(`${this.baseUrl}/${passengerId}`, request);
  }

  assignSeat(passengerId: number, request: AssignSeatRequest): Observable<PassengerResponse> {
    return this.httpClient.put<PassengerResponse>(`${this.baseUrl}/${passengerId}/assign-seat`, request);
  }
}
