export type TripType = 'ONE_WAY' | 'ROUND_TRIP';

export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW';

export interface CreateBookingRequest {
  userId: number;
  flightId: number;
  tripType: TripType;
  baseFare: number;
  seatIds: number[];
  mealPreference?: string;
  luggageKg: number;
  contactEmail: string;
  contactPhone: string;
}

export interface UpdateBookingFareRequest {
  luggageKg: number;
  mealSelections?: Array<{
    passengerIndex: number;
    mealId: string;
    mealName: string;
    mealPrice: number;
  }>;
}

export interface BookingResponse {
  bookingId: string;
  userId: number;
  flightId: number;
  seatIds: number[];
  pnrCode: string;
  tripType: TripType;
  status: BookingStatus;
  totalFare: number;
  baseFare: number;
  seatCharge: number;
  baggageCharge: number;
  mealCharge: number;
  taxes: number;
  mealPreference?: string;
  luggageKg: number;
  contactEmail: string;
  contactPhone: string;
  bookedAt: string;
  paymentId?: string;
}
