import { HttpClient } from "@angular/common/http";
import { Injectable, inject } from "@angular/core";
import { Observable } from "rxjs";
import { environment } from "../../../environments/environment";
import {
  MovieRating,
  Review,
  ReviewCreatePayload
} from "../models/catalog.model";

/**
 * User-facing review / rating endpoints. Reads are authenticated only (matches
 * the rest of /api/**); creating a review requires owning the linked booking.
 */
@Injectable({ providedIn: "root" })
export class ReviewService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/reviews`;

  submit(payload: ReviewCreatePayload): Observable<Review> {
    return this.http.post<Review>(this.base, payload);
  }

  listForMovie(movieId: number): Observable<Review[]> {
    return this.http.get<Review[]>(`${this.base}/movies/${movieId}`);
  }

  ratingForMovie(movieId: number): Observable<MovieRating> {
    return this.http.get<MovieRating>(`${this.base}/movies/${movieId}/rating`);
  }
}
