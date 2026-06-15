import { CurrencyPipe, DatePipe } from "@angular/common";
import { Component, OnInit, computed, inject, signal } from "@angular/core";
import { ActivatedRoute, Router } from "@angular/router";
import {
  LucideArrowLeft,
  LucideCalendar,
  LucideClock,
  LucideMapPin,
  LucideStore,
  LucideTicket
} from "@lucide/angular";
import { Movie, PublicShow, Theater } from "../../../core/models/catalog.model";
import { MovieService } from "../../../core/services/movie.service";
import { ShowService } from "../../../core/services/show.service";
import { TheaterService } from "../../../core/services/theater.service";

/** Shows for one movie at this theater — drives a grouped card in the template. */
interface MovieShows {
  movieId: number;
  movie: Movie | null;
  shows: PublicShow[];
}

/**
 * Theater detail page at `/theaters/:id`. Lists the venue's upcoming shows
 * grouped by movie; each showtime links straight into the booking page with the
 * show pre-selected (`/movies/:movieId/book?showId=...`). Movie title/poster are
 * joined client-side from the catalog so the public show DTO stays unchanged.
 */
@Component({
  selector: "app-theater-detail",
  standalone: true,
  imports: [
    CurrencyPipe,
    DatePipe,
    LucideArrowLeft,
    LucideCalendar,
    LucideClock,
    LucideMapPin,
    LucideStore,
    LucideTicket
  ],
  templateUrl: "./theater-detail.html",
  styleUrl: "./theater-detail.css"
})
export class TheaterDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly showService = inject(ShowService);
  private readonly theaterService = inject(TheaterService);
  private readonly movieService = inject(MovieService);

  readonly theater = signal<Theater | null>(null);
  readonly shows = signal<PublicShow[]>([]);
  readonly loading = signal(true);
  private readonly movieMap = signal<Map<number, Movie>>(new Map());

  /** Upcoming shows grouped by movie, preserving soonest-first show order. */
  readonly groups = computed<MovieShows[]>(() => {
    const byMovie = new Map<number, PublicShow[]>();
    for (const show of this.shows()) {
      if (!byMovie.has(show.movieId)) byMovie.set(show.movieId, []);
      byMovie.get(show.movieId)!.push(show);
    }
    const movies = this.movieMap();
    return [...byMovie.entries()].map(([movieId, shows]) => ({
      movieId,
      movie: movies.get(movieId) ?? null,
      shows
    }));
  });

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get("id"));

    // Resolve the theater from the cached directory, loading it if needed.
    const cached = this.theaterService.theaters().find((t) => t.id === id);
    if (cached) {
      this.theater.set(cached);
    } else {
      this.theaterService.load().subscribe({
        next: () =>
          this.theater.set(this.theaterService.theaters().find((t) => t.id === id) ?? null),
        error: (err) => console.error("Failed to load theater", err)
      });
    }

    // Movie catalog → title/poster lookup for each show's movie.
    if (this.movieService.movies().length) {
      this.buildMovieMap();
    } else {
      this.movieService.load().subscribe({
        next: () => this.buildMovieMap(),
        error: (err) => console.error("Failed to load movies", err)
      });
    }

    this.showService.listForTheater(id).subscribe({
      next: (shows) => {
        this.shows.set(shows);
        this.loading.set(false);
      },
      error: (err) => {
        console.error("Failed to load shows", err);
        this.loading.set(false);
      }
    });
  }

  private buildMovieMap(): void {
    const map = new Map<number, Movie>();
    for (const movie of this.movieService.movies()) {
      map.set(movie.id, movie);
    }
    this.movieMap.set(map);
  }

  book(show: PublicShow): void {
    this.router.navigate(["/movies", show.movieId, "book"], {
      queryParams: { showId: show.id }
    });
  }

  goBack(): void {
    this.router.navigate(["/theaters"]);
  }

  posterInitials(title: string): string {
    return title.slice(0, 2).toUpperCase();
  }
}
