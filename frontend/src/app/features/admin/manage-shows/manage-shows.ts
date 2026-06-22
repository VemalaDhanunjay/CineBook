import { CurrencyPipe, DatePipe } from "@angular/common";
import { Component, OnInit, computed, inject, signal } from "@angular/core";
import { FormsModule } from "@angular/forms";
import {
  LucideArmchair,
  LucideCalendarClock,
  LucideCheck,
  LucideFilm,
  LucideLanguages,
  LucideLoader,
  LucidePencil,
  LucidePlus,
  LucideTrash2,
  LucideX
} from "@lucide/angular";
import { Movie, Show, ShowPayload } from "../../../core/models/catalog.model";
import { MovieService } from "../../../core/services/movie.service";
import { ShowService } from "../../../core/services/show.service";
import { CarouselComponent } from "../../../shared/carousel/carousel";

/** Table status filter options for the shows ledger. */
type ShowFilter = "upcoming" | "closed" | "all";

/** Shape of the schedule form's plain inputs (movie & language are tracked separately as signals). */
interface ShowForm {
  date: string; // yyyy-MM-dd (date picker)
  time: string; // HH:mm (time picker)
  ticketPrice: number | null;
  totalSeats: number | null;
}

const EMPTY_FORM: ShowForm = {
  date: "",
  time: "",
  ticketPrice: null,
  totalSeats: null
};

/** Largest theater capacity a single show can be scheduled for. */
const MAX_SEATS = 250;

/** One movie-wise carousel slide: a movie plus the dates of its upcoming shows. */
interface CarouselMovie {
  posterUrl: string;
  title: string;
  movie: Movie;
  dates: string[];
}

/**
 * Admin "Manage Shows" page. Lets a theater owner schedule screenings of catalog
 * movies and review/edit/delete them. Structured like Manage Movies: dashboard
 * header + auto carousel + schedule form + filterable ledger table. Movie metadata
 * (poster/title/genre/languages) is resolved from the shared MovieService cache.
 */
@Component({
  selector: "app-manage-shows",
  standalone: true,
  imports: [
    FormsModule,
    CurrencyPipe,
    DatePipe,
    CarouselComponent,
    LucideArmchair,
    LucideCalendarClock,
    LucideCheck,
    LucideFilm,
    LucideLanguages,
    LucideLoader,
    LucidePencil,
    LucidePlus,
    LucideTrash2,
    LucideX
  ],
  templateUrl: "./manage-shows.html",
  styleUrl: "./manage-shows.css"
})
export class ManageShowsComponent implements OnInit {
  private readonly showService = inject(ShowService);
  private readonly movieService = inject(MovieService);

  // ── Catalog + show state, sourced from the shared service signals ───────────
  readonly movies = this.movieService.movies;
  readonly shows = this.showService.shows;
  readonly upcoming = this.showService.upcoming;
  readonly closed = this.showService.closed;

  /** Fast movieId → Movie lookup for the carousel and ledger (rebuilt when the catalog changes). */
  private readonly movieIndex = computed(
    () => new Map<number, Movie>(this.movies().map((movie) => [movie.id, movie]))
  );

  // ── Form model ──────────────────────────────────────────────────────────────
  form: ShowForm = { ...EMPTY_FORM };

  /** Currently selected movie for the new show (null until a card is picked). */
  readonly selectedMovieId = signal<number | null>(null);

  /** Single chosen show language (must be one of the selected movie's languages). */
  readonly selectedLanguage = signal<string | null>(null);

  /** Non-null when editing an existing show; null in "Schedule" mode. */
  readonly editingId = signal<number | null>(null);
  readonly submitting = signal(false);
  readonly submitted = signal(false);
  readonly error = signal<string | null>(null);

  /** Show pending deletion confirmation; drives the modal. */
  readonly deleteTarget = signal<Show | null>(null);

  /** Active table filter. Defaults to upcoming shows. */
  readonly filter = signal<ShowFilter>("upcoming");

  /** Earliest selectable date for the picker — no scheduling in the past. */
  readonly minDate = signal<string>("");

  /** Maximum seats a show can hold (exposed to the template for the input's `max`). */
  readonly maxSeats = MAX_SEATS;

  ngOnInit(): void {
    // Both catalog and shows are needed: movies populate the picker/carousel/table.
    this.movieService.load().subscribe();
    this.showService.load().subscribe();
    this.minDate.set(this.todayIso());
  }

  // ── Derived values ───────────────────────────────────────────────────────────

  /** The movie object currently selected in the form, if any. */
  readonly selectedMovie = computed(() => {
    const id = this.selectedMovieId();
    return id == null ? undefined : this.movieIndex().get(id);
  });

  /** Languages offered by the selected movie, as chips (drives the single-select badges). */
  readonly availableLanguages = computed(() => this.toChips(this.selectedMovie()?.languages));

  /**
   * Carousel source: one slide per movie (not per show), each carrying that
   * movie's upcoming show dates sorted soonest-first. Built from the existing
   * `upcoming()` signal and `movieIndex()` lookup.
   */
  readonly carouselMovies = computed<CarouselMovie[]>(() => {
    const byMovie = new Map<number, CarouselMovie>();
    for (const show of this.upcoming()) {
      const movie = this.movieIndex().get(show.movieId);
      if (!movie) continue;
      const entry = byMovie.get(movie.id);
      if (entry) {
        entry.dates.push(show.showTime);
      } else {
        byMovie.set(movie.id, {
          posterUrl: movie.posterUrl,
          title: movie.title,
          movie,
          dates: [show.showTime]
        });
      }
    }
    return Array.from(byMovie.values()).map((entry) => ({
      ...entry,
      dates: [...entry.dates].sort()
    }));
  });

  /** Rows shown in the ledger, per the active filter. */
  readonly filteredShows = computed(() => {
    switch (this.filter()) {
      case "upcoming":
        return this.upcoming();
      case "closed":
        return this.closed();
      default:
        return this.shows();
    }
  });

  /** All compulsory fields filled — gates the Schedule button. */
  readonly canSubmit = computed(
    () =>
      this.selectedMovieId() != null &&
      !!this.selectedLanguage() &&
      !!this.form.date &&
      !!this.form.time &&
      this.form.ticketPrice != null &&
      this.form.ticketPrice >= 0 &&
      this.form.totalSeats != null &&
      this.form.totalSeats >= 1 &&
      this.form.totalSeats <= MAX_SEATS
  );

  /** Resolve a show's movie for display in the template. */
  movieFor(show: Show): Movie | undefined {
    return this.movieIndex().get(show.movieId);
  }

  /** True while a show's start time is still in the future (editable). */
  isUpcoming(show: Show): boolean {
    return new Date(show.showTime).getTime() > Date.now();
  }

  /** True while at least one seat is booked or held for payment. */
  hasActiveBookings(show: Show): boolean {
    return show.availableSeats < show.totalSeats;
  }

  /** Split a CSV string into trimmed, non-empty chips. */
  toChips(csv: string | null | undefined): string[] {
    return (csv ?? "")
      .split(",")
      .map((part) => part.trim())
      .filter((part) => part.length > 0);
  }

  /** Today's date as yyyy-MM-dd in local time, for the date picker's min. */
  private todayIso(): string {
    const now = new Date();
    const offsetMs = now.getTimezoneOffset() * 60_000;
    return new Date(now.getTime() - offsetMs).toISOString().slice(0, 10);
  }

  // ── Movie & language selection ────────────────────────────────────────────────

  selectMovie(movie: Movie): void {
    this.selectedMovieId.set(movie.id);
    // Drop a previously chosen language if the new movie doesn't offer it.
    const langs = this.toChips(movie.languages).map((l) => l.toLowerCase());
    const current = this.selectedLanguage();
    if (current && !langs.includes(current.toLowerCase())) {
      this.selectedLanguage.set(null);
    }
  }

  /** Clear the selected movie (and its dependent language) — the small "X" button. */
  clearMovie(): void {
    this.selectedMovieId.set(null);
    this.selectedLanguage.set(null);
  }

  isMovieSelected(movie: Movie): boolean {
    return this.selectedMovieId() === movie.id;
  }

  /** Toggle a single language badge (selecting one replaces any prior choice). */
  selectLanguage(language: string): void {
    this.selectedLanguage.update((current) =>
      current?.toLowerCase() === language.toLowerCase() ? null : language
    );
  }

  isLanguageSelected(language: string): boolean {
    return this.selectedLanguage()?.toLowerCase() === language.toLowerCase();
  }

  // ── Table filter ────────────────────────────────────────────────────────────

  setFilter(filter: ShowFilter): void {
    this.filter.set(filter);
  }

  // ── Form actions ──────────────────────────────────────────────────────────────

  submit(): void {
    this.submitted.set(true);
    if (!this.canSubmit()) {
      return;
    }

    const payload: ShowPayload = {
      movieId: this.selectedMovieId()!,
      // Combine the date + time pickers into the LocalDateTime the backend expects.
      showTime: `${this.form.date}T${this.form.time}`,
      language: this.selectedLanguage()!,
      ticketPrice: this.form.ticketPrice!,
      totalSeats: this.form.totalSeats!
    };

    this.error.set(null);
    this.submitting.set(true);

    const id = this.editingId();
    const request = id
      ? this.showService.update(id, payload)
      : this.showService.create(payload);

    request.subscribe({
      next: () => {
        this.submitting.set(false);
        this.resetForm();
      },
      error: (err) => {
        this.error.set(err?.error?.message ?? "Could not save the show");
        this.submitting.set(false);
      }
    });
  }

  editShow(show: Show): void {
    this.editingId.set(show.id);
    this.error.set(null);
    this.submitted.set(false);
    this.selectedMovieId.set(show.movieId);
    this.selectedLanguage.set(show.language);
    // showTime is an ISO string like "2026-06-12T18:30:00" → split into the pickers.
    this.form = {
      date: show.showTime.slice(0, 10),
      time: show.showTime.slice(11, 16),
      ticketPrice: show.ticketPrice,
      totalSeats: show.totalSeats
    };
  }

  /** Open the deletion confirmation modal for a show. */
  askDelete(show: Show): void {
    this.deleteTarget.set(show);
  }

  cancelDelete(): void {
    this.deleteTarget.set(null);
  }

  /** Confirm deletion (soft-delete via the API) and close the modal. */
  confirmDelete(): void {
    const show = this.deleteTarget();
    if (!show) {
      return;
    }
    // If we were editing the show being deleted, drop back to Schedule mode.
    if (this.editingId() === show.id) {
      this.resetForm();
    }
    this.showService.remove(show.id).subscribe({
      next: () => this.deleteTarget.set(null),
      error: (err) => {
        this.error.set(err?.error?.message ?? "Could not delete the show");
        this.deleteTarget.set(null);
      }
    });
  }

  cancelEdit(): void {
    this.resetForm();
  }

  /** Reset every field back to its empty state (Reset button / after save). */
  resetForm(): void {
    this.editingId.set(null);
    this.submitted.set(false);
    this.error.set(null);
    this.selectedMovieId.set(null);
    this.selectedLanguage.set(null);
    this.form = { ...EMPTY_FORM };
  }
}
