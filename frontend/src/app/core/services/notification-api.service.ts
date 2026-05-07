import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../config/environment';
import { NotificationResponse } from '../models/notification.models';

@Injectable({
  providedIn: 'root'
})
export class NotificationApiService {
  private readonly baseUrl = `${environment.apiBaseUrl}/notifications`;

  constructor(private readonly httpClient: HttpClient) {}

  getByUser(userId: number): Observable<NotificationResponse[]> {
    return this.httpClient.get<NotificationResponse[]>(`${this.baseUrl}/user/${userId}`);
  }

  markRead(notificationId: number): Observable<NotificationResponse> {
    return this.httpClient.put<NotificationResponse>(`${this.baseUrl}/read/${notificationId}`, {});
  }
}
