import { HttpClient } from "@angular/common/http";
import { Injectable, inject, signal } from "@angular/core";
import { Observable, tap } from "rxjs";
import { environment } from "../../../environments/environment";
import {
  BookingCreatePayload,
  RefundQuote,
  SeatAvailability,
  UserBooking
} from "../models/catalog.model";

/**
 * User-facing booking state and HTTP client. Sister of the admin {@code BookingService}
 * which is theater-scoped against {@code /api/admin/bookings}; this one talks to
 * the user endpoints at {@code /api/bookings}.
 */
@Injectable({ providedIn: "root" })
export class UserBookingService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/bookings`;

  /** Caller's own bookings, newest first. Seeded by {@link loadMine}. */
  readonly bookings = signal<UserBooking[]>([]);

  loadMine(): Observable<UserBooking[]> {
    return this.http
      .get<UserBooking[]>(`${this.base}/me`)
      .pipe(tap((list) => this.bookings.set(list)));
  }

  getMine(id: number): Observable<UserBooking> {
    return this.http.get<UserBooking>(`${this.base}/me/${id}`);
  }

  create(payload: BookingCreatePayload): Observable<UserBooking> {
    return this.http
      .post<UserBooking>(this.base, payload)
      .pipe(tap((created) => this.bookings.update((list) => [created, ...list])));
  }

  cancel(id: number): Observable<UserBooking> {
    return this.http
      .delete<UserBooking>(`${this.base}/me/${id}`)
      .pipe(
        tap((updated) =>
          this.bookings.update((list) =>
            list.map((booking) => (booking.id === id ? updated : booking))
          )
        )
      );
  }

  cancelSeats(id: number, seatLabels: string[]): Observable<UserBooking> {
    return this.http
      .request<UserBooking>("DELETE", `${this.base}/me/${id}/seats`, {
        body: { seatLabels }
      })
      .pipe(
        tap((updated) =>
          this.bookings.update((list) =>
            list.map((booking) => (booking.id === id ? updated : booking))
          )
        )
      );
  }

  seatAvailability(showId: number): Observable<SeatAvailability> {
    return this.http.get<SeatAvailability>(`${this.base}/shows/${showId}/seats`);
  }

  /** Refund preview for the cancel modal — refund % and per-seat amount by time-to-show. */
  refundQuote(id: number): Observable<RefundQuote> {
    return this.http.get<RefundQuote>(`${this.base}/me/${id}/refund-quote`);
  }
}
