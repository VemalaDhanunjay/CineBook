// Domain models matching the backend schema. Used from Day 2 onward.

export interface Movie {
  id: number;
  title: string;
  genre: string;
  durationMins: number;
  languages: string; // CSV
  price: number;
  posterUrl: string;
  trailerUrl: string;
  deleted?: boolean;
}

/** Create/update payload — the backend assigns `id` and owns the soft-delete flag. */
export type MoviePayload = Omit<Movie, "id" | "deleted">;

export interface Theater {
  id: number;
  name: string;
  location: string;
  ownerUserId?: number;
}

export interface Show {
  id: number;
  movieId: number;
  theaterId: number;
  showTime: string;
  language: string;
  ticketPrice: number;
  totalSeats: number;
  availableSeats: number;
}

/**
 * Create/update payload for a show. The backend assigns `id`, derives `theaterId`
 * from the admin's JWT, and computes `availableSeats`, so none of those are sent.
 */
export type ShowPayload = Pick<
  Show,
  "movieId" | "showTime" | "language" | "ticketPrice" | "totalSeats"
>;

export type BookingStatus = "CONFIRMED" | "PARTIALLY_CANCELLED" | "CANCELLED";

export type SeatStatus = "BOOKED" | "CANCELLED";

export interface Booking {
  id: number;
  showId: number;
  userId: number;
  seats: string; // immutable snapshot of originally booked seats (CSV)
  seatsBooked: number;
  subtotal: number;
  taxAmount: number;
  totalAmount: number;
  refundAmount: number;
  status: BookingStatus;
  bookingDate: string;
  cancelledAt: string | null;
}

/** One booked seat within a booking — authoritative per-seat state for partial cancellation. */
export interface BookingSeat {
  id: number;
  bookingId: number;
  seatLabel: string;
  price: number;
  status: SeatStatus;
  cancelledAt: string | null;
}

/**
 * Pre-joined booking row served by `/api/admin/bookings` for the admin ledger.
 * The server stitches movie + show + theater + user context on so the table can
 * render without secondary lookups.
 */
export interface AdminBooking {
  id: number;
  userId: number;
  username: string;
  movieId: number | null;
  movieTitle: string;
  moviePosterUrl: string | null;
  showId: number;
  showTime: string | null;
  theaterName: string | null;
  theaterLocation: string | null;
  seatsBooked: number;
  seatNumbers: string;
  subtotal: number;
  taxAmount: number;
  totalAmount: number;
  refundAmount: number;
  status: BookingStatus;
  bookingDate: string;
  cancelledAt: string | null;
}

/** One row of the "Most Booked" leaderboard — drives the admin bookings carousel. */
export interface MostBookedMovie {
  movieId: number;
  title: string;
  posterUrl: string;
  totalSeatsBooked: number;
  totalBookings: number;
}
