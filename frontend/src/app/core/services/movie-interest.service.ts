import { HttpClient } from "@angular/common/http";
import { Injectable, inject } from "@angular/core";
import { Observable } from "rxjs";
import { environment } from "../../../environments/environment";
import { MovieInterestStatus } from "../models/catalog.model";

/**
 * "I'm interested" waitlist client. Used by the booking page when a movie has
 * no upcoming shows so the user can still signal demand. Writes are idempotent;
 * the server returns the post-action status so callers can drive the UI from
 * a single response.
 */
@Injectable({ providedIn: "root" })
export class MovieInterestService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/movies`;

  status(movieId: number): Observable<MovieInterestStatus> {
    return this.http.get<MovieInterestStatus>(`${this.base}/${movieId}/interest`);
  }

  mark(movieId: number): Observable<MovieInterestStatus> {
    return this.http.post<MovieInterestStatus>(`${this.base}/${movieId}/interest`, {});
  }

  unmark(movieId: number): Observable<MovieInterestStatus> {
    return this.http.delete<MovieInterestStatus>(`${this.base}/${movieId}/interest`);
  }
}
