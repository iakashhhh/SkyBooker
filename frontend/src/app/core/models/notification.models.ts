export type NotificationType = 'BOOKING_CONFIRMED' | 'FLIGHT_DELAY' | 'CHECKIN_REMINDER';

export type NotificationChannel = 'EMAIL' | 'SMS' | 'APP';

export interface NotificationResponse {
  notificationId: number;
  recipientId: number;
  type: NotificationType;
  message: string;
  channel: NotificationChannel;
  relatedBookingId?: string;
  read?: boolean;
  isRead?: boolean;
  createdAt: string;
}
