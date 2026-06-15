import { HttpClient } from "@angular/common/http";
import { Injectable, computed, inject, signal } from "@angular/core";
import { Observable, tap } from "rxjs";
import { environment } from "../../../environments/environment";
import { Movie, MoviePayload } from "../models/catalog.model";

/**
 * Movie catalog state + CRUD. Holds the list in a signal so the Manage Movies
 * header counter, carousel and ledger table all update reactively. The auth
 * interceptor attaches the JWT, so write calls carry the admin's credentials.
 */
@Injectable({ providedIn: "root" })
export class MovieService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/movies`;

  /** All non-deleted movies, newest last (backend insertion order). */
  readonly movies = signal<Movie[]>([]);

  /** Total movies in the catalog, used by the dashboard counter. */
  readonly count = computed(() => this.movies().length);

  /** The last 3 added movies, newest first — drives the top carousel. */
  readonly latestThree = computed(() => this.movies().slice(-3).reverse());

  /** Fetch the catalog and seed the signal. */
  load(): Observable<Movie[]> {
    return this.http
      .get<Movie[]>(this.base)
      .pipe(tap((list) => this.movies.set(list)));
  }

  /** Single movie by id — used by the user booking page when the catalog is empty. */
  getOne(id: number): Observable<Movie> {
    return this.http.get<Movie>(`${this.base}/${id}`);
  }

  create(payload: MoviePayload): Observable<Movie> {
    return this.http
      .post<Movie>(this.base, payload)
      .pipe(tap((movie) => this.movies.update((list) => [...list, movie])));
  }

  update(id: number, payload: MoviePayload): Observable<Movie> {
    return this.http
      .put<Movie>(`${this.base}/${id}`, payload)
      .pipe(
        tap((updated) =>
          this.movies.update((list) =>
            list.map((movie) => (movie.id === id ? updated : movie))
          )
        )
      );
  }

  remove(id: number): Observable<void> {
    return this.http
      .delete<void>(`${this.base}/${id}`)
      .pipe(
        tap(() =>
          this.movies.update((list) => list.filter((movie) => movie.id !== id))
        )
      );
  }
}
