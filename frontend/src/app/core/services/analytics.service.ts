import { HttpClient } from "@angular/common/http";
import { Injectable, inject, signal } from "@angular/core";
import { Observable, forkJoin, tap } from "rxjs";
import { environment } from "../../../environments/environment";
import {
  AdminBooking,
  Movie,
  MovieInterestStat,
  MovieRating,
  Show
} from "../models/catalog.model";

/**
 * Admin analytics state. Mirrors {@link BookingService}: every dataset lives in a
 * signal so the dashboard KPIs, charts and tables update reactively. The page
 * aggregates everything client-side, so this service simply fetches the five raw
 * feeds in parallel — bookings, shows and movies are reused from existing
 * endpoints, ratings and waitlist are the analytics-only aggregates. The auth
 * interceptor attaches the admin's JWT and the backend scopes every response to
 * that admin's theater.
 */
@Injectable({ providedIn: "root" })
export class AnalyticsService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  readonly bookings = signal<AdminBooking[]>([]);
  readonly shows = signal<Show[]>([]);
  readonly movies = signal<Movie[]>([]);
  readonly ratings = signal<MovieRating[]>([]);
  readonly interest = signal<MovieInterestStat[]>([]);

  /** Fetch every analytics feed in parallel and seed all signals. */
  load(): Observable<{
    bookings: AdminBooking[];
    shows: Show[];
    movies: Movie[];
    ratings: MovieRating[];
    interest: MovieInterestStat[];
  }> {
    return forkJoin({
      bookings: this.http.get<AdminBooking[]>(`${this.api}/admin/bookings`),
      shows: this.http.get<Show[]>(`${this.api}/shows`),
      movies: this.http.get<Movie[]>(`${this.api}/movies`),
      ratings: this.http.get<MovieRating[]>(`${this.api}/admin/analytics/movie-ratings`),
      interest: this.http.get<MovieInterestStat[]>(`${this.api}/admin/analytics/movie-interest`)
    }).pipe(
      tap(({ bookings, shows, movies, ratings, interest }) => {
        this.bookings.set(bookings);
        this.shows.set(shows);
        this.movies.set(movies);
        this.ratings.set(ratings);
        this.interest.set(interest);
      })
    );
  }
}
