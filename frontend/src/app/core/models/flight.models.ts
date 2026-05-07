export type SeatClass = 'ECONOMY' | 'PREMIUM_ECONOMY' | 'BUSINESS' | 'FIRST';

export type DepartureWindow = 'MORNING' | 'AFTERNOON' | 'EVENING' | 'NIGHT';

export interface FlightResponse {
  flightId: number;
  flightNumber: string;
  airlineId: number;
  originAirportCode: string;
  destinationAirportCode: string;
  viaAirportCode?: string;
  departureTime: string;
  arrivalTime: string;
  durationMinutes: number;
  status: string;
  aircraftType: string;
  totalSeats: number;
  availableSeats: number;
  basePrice: number;
  seatClass: SeatClass;
  displayedPrice: number;
  numberOfStops: number;
}

export interface RoundTripSearchResponse {
  outboundFlights: FlightResponse[];
  returnFlights: FlightResponse[];
}

export interface OneWaySearchParams {
  origin: string;
  destination: string;
  journeyDate: string;
  minPrice?: number;
  maxPrice?: number;
  airlineId?: number;
  departureWindow?: DepartureWindow;
  maxStops?: number;
  seatClass?: SeatClass;
  sortBy?: string;
}

export interface RoundTripSearchParams {
  origin: string;
  destination: string;
  onwardDate: string;
  returnDate: string;
  minPrice?: number;
  maxPrice?: number;
  airlineId?: number;
  departureWindow?: DepartureWindow;
  maxStops?: number;
  seatClass?: SeatClass;
  sortBy?: string;
}
