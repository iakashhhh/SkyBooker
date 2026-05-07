export type PassengerType = 'ADULT' | 'CHILD' | 'INFANT';

export interface PassengerRequest {
  bookingId: string;
  title: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: string;
  passengerType: PassengerType;
  passportNumber: string;
  nationality: string;
  passportExpiry: string;
  mealPreference?: string;
  extraBaggageKg?: number;
}

export interface AssignSeatRequest {
  seatId: number;
  seatNumber: string;
}

export interface PassengerResponse {
  passengerId: number;
  bookingId: string;
  title: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: string;
  passengerType: PassengerType;
  passportNumber: string;
  nationality: string;
  passportExpiry: string;
  mealPreference?: string;
  extraBaggageKg?: number;
  seatId?: number;
  seatNumber?: string;
  ticketNumber: string;
  createdAt?: string;
}
