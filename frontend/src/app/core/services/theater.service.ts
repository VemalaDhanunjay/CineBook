import { HttpClient } from "@angular/common/http";
import { Injectable, computed, inject, signal } from "@angular/core";
import { Observable, tap } from "rxjs";
import { environment } from "../../../environments/environment";
import { Theater } from "../models/catalog.model";

/**
 * Public theater directory state. Drives the user-facing Theaters page and the
 * navbar's location picker (whose city list is derived from {@link cities} —
 * only the locations that actually have a registered theater are exposed).
 */
@Injectable({ providedIn: "root" })
export class TheaterService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/theaters`;

  readonly theaters = signal<Theater[]>([]);
  readonly count = computed(() => this.theaters().length);

  /** Distinct, alphabetized theater locations — drives the navbar city picker. */
  readonly cities = computed(() => {
    const set = new Set<string>();
    for (const t of this.theaters()) {
      const loc = t.location?.trim();
      if (loc) set.add(loc);
    }
    return Array.from(set).sort((a, b) => a.localeCompare(b));
  });

  /** True once the catalog has been fetched at least once. Lets callers no-op double loads. */
  readonly loaded = computed(() => this.theaters().length > 0);

  load(): Observable<Theater[]> {
    return this.http.get<Theater[]>(this.base).pipe(tap((list) => this.theaters.set(list)));
  }

  loadByLocation(location: string): Observable<Theater[]> {
    return this.http
      .get<Theater[]>(this.base, { params: { location } })
      .pipe(tap((list) => this.theaters.set(list)));
  }
}
