import { HttpClient } from "@angular/common/http";
import { Injectable, computed, inject, signal } from "@angular/core";
import { Observable, tap } from "rxjs";
import { environment } from "../../../environments/environment";
import { Show, ShowPayload } from "../models/catalog.model";

/**
 * Show (screening) state + CRUD. Mirrors {@link MovieService}: the list lives in a
 * signal so the Manage Shows header counter, carousel and ledger table all update
 * reactively. The auth interceptor attaches the admin's JWT, and the backend scopes
 * every response to that admin's theater.
 */
@Injectable({ providedIn: "root" })
export class ShowService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/shows`;

  /** All non-deleted shows for the signed-in admin's theater. */
  readonly shows = signal<Show[]>([]);

  /** Total scheduled shows, used by the dashboard counter. */
  readonly count = computed(() => this.shows().length);

  /**
   * Upcoming shows (start time still in the future), soonest first. Drives the
   * carousel and the default table filter. Reads `Date.now()` lazily on each
   * recompute so the open/closed split stays current as time passes.
   */
  readonly upcoming = computed(() =>
    this.shows()
      .filter((show) => new Date(show.showTime).getTime() > Date.now())
      .sort((a, b) => new Date(a.showTime).getTime() - new Date(b.showTime).getTime())
  );

  /** Shows whose start time has passed (cannot be edited, only deleted). */
  readonly closed = computed(() =>
    this.shows().filter((show) => new Date(show.showTime).getTime() <= Date.now())
  );

  /** Fetch the theater's shows and seed the signal. */
  load(): Observable<Show[]> {
    return this.http
      .get<Show[]>(this.base)
      .pipe(tap((list) => this.shows.set(list)));
  }

  create(payload: ShowPayload): Observable<Show> {
    return this.http
      .post<Show>(this.base, payload)
      .pipe(tap((show) => this.shows.update((list) => [...list, show])));
  }

  update(id: number, payload: ShowPayload): Observable<Show> {
    return this.http
      .put<Show>(`${this.base}/${id}`, payload)
      .pipe(
        tap((updated) =>
          this.shows.update((list) =>
            list.map((show) => (show.id === id ? updated : show))
          )
        )
      );
  }

  remove(id: number): Observable<void> {
    return this.http
      .delete<void>(`${this.base}/${id}`)
      .pipe(
        tap(() =>
          this.shows.update((list) => list.filter((show) => show.id !== id))
        )
      );
  }
}
