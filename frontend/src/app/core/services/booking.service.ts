import { HttpClient } from "@angular/common/http";
import { Injectable, inject, signal } from "@angular/core";
import { Observable, forkJoin, tap } from "rxjs";
import { environment } from "../../../environments/environment";
import { AdminBooking, MostBookedMovie } from "../models/catalog.model";

/**
 * Admin "All Bookings" state. Mirrors {@link ShowService}: the list lives in a
 * signal so the dashboard KPIs, carousel and ledger table all update reactively.
 * The auth interceptor attaches the admin's JWT, and the backend scopes every
 * response to that admin's theater.
 */
@Injectable({ providedIn: "root" })
export class BookingService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/admin/bookings`;

  /** All bookings for the signed-in admin's theater, newest first. */
  readonly bookings = signal<AdminBooking[]>([]);

  /** Top movies by summed seats_booked — feeds the "Most Booked" carousel ribbon. */
  readonly mostBooked = signal<MostBookedMovie[]>([]);

  /**
   * Fetch both the ledger and the leaderboard in parallel and seed both signals.
   * Returns the bookings list so callers can chain on `.subscribe(...)`.
   */
  load(): Observable<{ bookings: AdminBooking[]; mostBooked: MostBookedMovie[] }> {
    return forkJoin({
      bookings: this.http.get<AdminBooking[]>(this.base),
      mostBooked: this.http.get<MostBookedMovie[]>(`${this.base}/most-booked`)
    }).pipe(
      tap(({ bookings, mostBooked }) => {
        this.bookings.set(bookings);
        this.mostBooked.set(mostBooked);
      })
    );
  }
}
