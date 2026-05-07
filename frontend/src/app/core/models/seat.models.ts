export type SeatClass = 'ECONOMY' | 'BUSINESS' | 'FIRST';

export type SeatStatus = 'AVAILABLE' | 'HELD' | 'CONFIRMED' | 'BLOCKED';

export interface Seat {
  seatId: number;
  flightId: number;
  seatNumber: string;
  seatClass: SeatClass;
  row: number;
  column: string;
  isWindow: boolean;
  isAisle: boolean;
  hasExtraLegroom: boolean;
  status: SeatStatus;
  priceMultiplier: number;
  holdExpiresAt?: string | null;
}

export interface SeatMapResponse {
  flightId: number;
  seats: Seat[];
  availableSeatsByClass: Record<SeatClass, number>;
}

export interface SeatActionRequest {
  flightId: number;
  seatIds: number[];
}

export interface SeatActionResponse {
  message: string;
  flightId: number;
  seatIds: number[];
  holdExpiresAt?: string | null;
}
