import { Component, OnInit, computed, inject, signal } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { Router } from "@angular/router";
import {
  LucideArrowRight,
  LucideClock,
  LucideFilm,
  LucideFlame,
  LucideLanguages,
  LucideMapPin,
  LucidePlay,
  LucideSearch,
  LucideStore,
  LucideTicket
} from "@lucide/angular";
import { forkJoin, of } from "rxjs";
import { catchError } from "rxjs/operators";
import {
  Movie,
  MovieRating,
  PublicShow,
  Theater
} from "../../../core/models/catalog.model";
import { LocationService } from "../../../core/services/location.service";
import { MovieService } from "../../../core/services/movie.service";
import { ReviewService } from "../../../core/services/review.service";
import { ShowService } from "../../../core/services/show.service";
import { TheaterService } from "../../../core/services/theater.service";
import { CarouselComponent } from "../../../shared/carousel/carousel";
import { TomatoFill, TomatoIconComponent } from "../../../shared/tomato-icon/tomato-icon";
import { TrailerModalComponent } from "../../../shared/trailer-modal/trailer-modal";

type Filter = string;

/**
 * User-facing movie browse page. Now wires the location (from navbar) and
 * theater dropdowns to real data: after the catalog loads, we fan out one
 * GET /api/shows?movieId={id} per movie to build a map of movie -> theaters
 * that actually show it. Both filters narrow the grid against that map.
 */
@Component({
  selector: "app-movies",
  standalone: true,
  imports: [
    FormsModule,
    CarouselComponent,
    TomatoIconComponent,
    TrailerModalComponent,
    LucideArrowRight,
    LucideClock,
    LucideFilm,
    LucideFlame,
    LucideLanguages,
    LucideMapPin,
    LucidePlay,
    LucideSearch,
    LucideStore,
    LucideTicket
  ],
  templateUrl: "./movies.html",
  styleUrl: "./movies.css"
})
export class MoviesComponent implements OnInit {
  private readonly movieService = inject(MovieService);
  private readonly reviewService = inject(ReviewService);
  private readonly showService = inject(ShowService);
  private readonly theaterService = inject(TheaterService);
  private readonly locationService = inject(LocationService);
  private readonly router = inject(Router);

  // ── Source data ────────────────────────────────────────────────────────────
  readonly movies = this.movieService.movies;
  readonly heroSlides = this.movieService.latestThree;
  readonly theaters = this.theaterService.theaters;

  /** Rating data keyed by movieId. */
  readonly ratings = signal<Map<number, MovieRating>>(new Map());

  /** movieId -> set of theaterIds that have at least one upcoming show. */
  readonly movieTheaterMap = signal<Map<number, Set<number>>>(new Map());

  /** theaterId -> location (kept for the "in this city" filter pipeline). */
  readonly theaterLocationMap = computed(() => {
    const m = new Map<number, string>();
    for (const t of this.theaters()) {
      if (t.location) m.set(t.id, t.location);
    }
    return m;
  });

  // ── Filter state ───────────────────────────────────────────────────────────
  readonly search = signal("");
  readonly selectedGenre = signal<Filter>("ALL");
  readonly selectedLanguage = signal<Filter>("ALL");
  readonly selectedTheater = signal<Filter>("ALL");

  /** Selected city — driven by the navbar dropdown via LocationService. */
  readonly selectedLocation = this.locationService.location;

  // ── Derived values ─────────────────────────────────────────────────────────

  readonly genres = computed(() => this.uniqueTokens((m) => m.genre));
  readonly languages = computed(() => this.uniqueTokens((m) => m.languages));
  readonly trendingIds = computed(() => new Set(this.heroSlides().map((m) => m.id)));

  /** Theaters in the currently selected city — populates the dropdown. */
  readonly theatersInLocation = computed<Theater[]>(() => {
    const loc = this.selectedLocation();
    if (!loc) return this.theaters();
    const lower = loc.toLowerCase();
    return this.theaters().filter((t) => (t.location ?? "").toLowerCase() === lower);
  });

  /** Theater ids that survive the location filter — used to gate movies. */
  private readonly allowedTheaterIds = computed(() => {
    const loc = this.selectedLocation();
    if (!loc) return null; // null = all theaters allowed
    return new Set(this.theatersInLocation().map((t) => t.id));
  });

  /**
   * Search + genre + language + theater + location pipeline.
   *
   * <p>Theater / city filters are lenient against movies that don't yet have
   * any upcoming shows scheduled — those movies stay visible because we have
   * no data to exclude them. Movies that <em>do</em> have shows are held to
   * the selected theater or city. This avoids the empty-grid trap when the
   * DB has no future shows yet while still working correctly once it does.
   */
  readonly filteredMovies = computed(() => {
    const query = this.search().trim().toLowerCase();
    const genre = this.selectedGenre();
    const lang = this.selectedLanguage();
    const theaterFilter = this.selectedTheater();
    const allowed = this.allowedTheaterIds();
    const movieMap = this.movieTheaterMap();

    return this.movies().filter((movie) => {
      if (query && !movie.title.toLowerCase().includes(query)) return false;
      if (genre !== "ALL" && !this.hasToken(movie.genre, genre)) return false;
      if (lang !== "ALL" && !this.hasToken(movie.languages, lang)) return false;

      const showsAt = movieMap.get(movie.id);
      const hasAnyShows = !!showsAt && showsAt.size > 0;

      if (theaterFilter !== "ALL" && hasAnyShows) {
        const tid = Number(theaterFilter);
        if (!showsAt!.has(tid)) return false;
      }
      if (allowed && hasAnyShows) {
        const hasAnyAllowed = [...showsAt!].some((id) => allowed.has(id));
        if (!hasAnyAllowed) return false;
      }
      return true;
    });
  });

  ngOnInit(): void {
    this.theaterService.load().subscribe({
      error: (err) => console.error("Failed to load theaters", err)
    });
    this.movieService.load().subscribe({
      next: (movies) => {
        this.loadRatings(movies);
        this.loadShowsPerMovie(movies);
      },
      error: (err) => console.error("Failed to load movies", err)
    });
  }

  /** Fan out one rating call per movie; per-movie failures fall back gracefully. */
  private loadRatings(movies: Movie[]): void {
    if (movies.length === 0) return;
    const lookups = movies.map((movie) =>
      this.reviewService
        .ratingForMovie(movie.id)
        .pipe(catchError(() => of(null as MovieRating | null)))
    );
    forkJoin(lookups).subscribe((results) => {
      const next = new Map<number, MovieRating>();
      results.forEach((rating, idx) => {
        if (rating) next.set(movies[idx].id, rating);
      });
      this.ratings.set(next);
    });
  }

  /** Build movieId -> theaterIds so location & theater filters can use real data. */
  private loadShowsPerMovie(movies: Movie[]): void {
    if (movies.length === 0) return;
    const lookups = movies.map((movie) =>
      this.showService
        .listForMovie(movie.id)
        .pipe(catchError(() => of([] as PublicShow[])))
    );
    forkJoin(lookups).subscribe((results) => {
      const next = new Map<number, Set<number>>();
      results.forEach((shows, idx) => {
        next.set(movies[idx].id, new Set(shows.map((s) => s.theaterId)));
      });
      this.movieTheaterMap.set(next);
    });
  }

  ratingFor(movieId: number): MovieRating | null {
    return this.ratings().get(movieId) ?? null;
  }

  /** Five tomato fill-states for an average rating (e.g. 3.4 → full,full,full,empty,empty). */
  tomatoFills(average: number): TomatoFill[] {
    return [1, 2, 3, 4, 5].map((i) => {
      if (average >= i) return "full";
      if (average >= i - 0.5) return "half";
      return "empty";
    });
  }

  // ── Actions ────────────────────────────────────────────────────────────────

  setGenre(genre: Filter): void {
    this.selectedGenre.set(genre);
  }

  setLanguage(lang: Filter): void {
    this.selectedLanguage.set(lang);
  }

  clearFilters(): void {
    this.search.set("");
    this.selectedGenre.set("ALL");
    this.selectedLanguage.set("ALL");
    this.selectedTheater.set("ALL");
  }

  /** Movie whose trailer is currently open in the modal; null when closed. */
  readonly trailerMovie = signal<Movie | null>(null);

  bookMovie(movie: Movie, event?: Event): void {
    event?.stopPropagation();
    this.router.navigate(["/movies", movie.id, "book"]);
  }

  openTrailer(movie: Movie, event?: Event): void {
    event?.stopPropagation();
    this.trailerMovie.set(movie);
  }

  closeTrailer(): void {
    this.trailerMovie.set(null);
  }

  toChips(value: string | null | undefined): string[] {
    if (!value) return [];
    return value
      .split(",")
      .map((s) => s.trim())
      .filter((s) => s.length > 0);
  }

  posterInitials(title: string): string {
    return title.slice(0, 2).toUpperCase();
  }

  /** Look up a theater's name from its id — used by the active-filter chip. */
  theaterNameLookup(idAsString: string): string {
    const id = Number(idAsString);
    return this.theaters().find((t) => t.id === id)?.name ?? "Theater";
  }

  // ── Internals ──────────────────────────────────────────────────────────────

  private hasToken(csv: string | null | undefined, token: string): boolean {
    return this.toChips(csv).some((t) => t.toLowerCase() === token.toLowerCase());
  }

  private uniqueTokens(pick: (m: Movie) => string | null | undefined): string[] {
    const seen = new Set<string>();
    for (const movie of this.movies()) {
      for (const token of this.toChips(pick(movie))) {
        seen.add(token);
      }
    }
    return Array.from(seen).sort((a, b) => a.localeCompare(b));
  }
}
