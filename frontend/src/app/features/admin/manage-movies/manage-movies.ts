import { CurrencyPipe } from "@angular/common";
import { Component, OnInit, ViewChild, inject, signal } from "@angular/core";
import { FormsModule, NgForm } from "@angular/forms";
import { DomSanitizer, SafeResourceUrl } from "@angular/platform-browser";
import {
  LucideChevronLeft,
  LucideChevronRight,
  LucideCirclePlay,
  LucideClapperboard,
  LucideClock,
  LucideFilm,
  LucideImageOff,
  LucideLanguages,
  LucideLoader,
  LucidePencil,
  LucidePlus,
  LucideTrash2
} from "@lucide/angular";
import { Movie, MoviePayload } from "../../../core/models/catalog.model";
import { MovieService } from "../../../core/services/movie.service";
import { CarouselComponent } from "../../../shared/carousel/carousel";

/** Shape of the add/edit form. Empty values use null so number inputs start blank. */
interface MovieForm {
  title: string;
  genre: string;
  durationMins: number | null;
  languages: string;
  posterUrl: string;
  trailerUrl: string;
  price: number | null;
}

const EMPTY_FORM: MovieForm = {
  title: "",
  genre: "",
  durationMins: null,
  languages: "",
  posterUrl: "",
  trailerUrl: "",
  price: null
};

/** Preset language chips that append to the comma-separated `languages` field. */
const PRESET_LANGUAGES = ["Telugu", "Hindi", "English", "Tamil", "Kannada", "Malayalam"];

@Component({
  selector: "app-manage-movies",
  standalone: true,
  imports: [
    FormsModule,
    CurrencyPipe,
    CarouselComponent,
    LucideChevronLeft,
    LucideChevronRight,
    LucideCirclePlay,
    LucideClapperboard,
    LucideClock,
    LucideFilm,
    LucideImageOff,
    LucideLanguages,
    LucideLoader,
    LucidePencil,
    LucidePlus,
    LucideTrash2
  ],
  templateUrl: "./manage-movies.html",
  styleUrl: "./manage-movies.css"
})
export class ManageMoviesComponent implements OnInit {
  private readonly movieService = inject(MovieService);
  private readonly sanitizer = inject(DomSanitizer);

  /** Catalog state, sourced from the shared service signals. */
  readonly movies = this.movieService.movies;
  readonly count = this.movieService.count;
  readonly latestThree = this.movieService.latestThree;

  readonly presetLanguages = PRESET_LANGUAGES;

  /** Add/edit form model, bound via ngModel. */
  form: MovieForm = { ...EMPTY_FORM };

  /** Non-null when editing an existing movie; null in "Add" mode. */
  readonly editingId = signal<number | null>(null);
  readonly submitting = signal(false);
  readonly submitted = signal(false);
  readonly error = signal<string | null>(null);

  /** Movie pending deletion confirmation; drives the archival modal. */
  readonly deleteTarget = signal<Movie | null>(null);

  @ViewChild("movieForm") private movieForm?: NgForm;

  ngOnInit(): void {
    this.movieService.load().subscribe();
  }

  // ── Derived preview values ────────────────────────────────────────────────

  /** Split a CSV string into trimmed, non-empty chips. */
  toChips(csv: string | null | undefined): string[] {
    return (csv ?? "")
      .split(",")
      .map((part) => part.trim())
      .filter((part) => part.length > 0);
  }

  /** Extract an 11-char YouTube video id from common URL shapes, else null. */
  private youTubeId(url: string | null | undefined): string | null {
    if (!url) {
      return null;
    }
    const match = url.match(
      /(?:youtube\.com\/(?:watch\?v=|embed\/|shorts\/)|youtu\.be\/)([\w-]{11})/
    );
    return match ? match[1] : null;
  }

  /** Safe embeddable player URL for the trailer, or null when not a YouTube link. */
  embedUrl(url: string | null | undefined): SafeResourceUrl | null {
    const id = this.youTubeId(url);
    return id
      ? this.sanitizer.bypassSecurityTrustResourceUrl(`https://www.youtube.com/embed/${id}`)
      : null;
  }

  /** True when a trailer URL is present but isn't a recognizable YouTube link. */
  isInvalidTrailer(url: string | null | undefined): boolean {
    return !!url && !this.youTubeId(url);
  }

  isLanguageSelected(language: string): boolean {
    return this.toChips(this.form.languages).some(
      (lang) => lang.toLowerCase() === language.toLowerCase()
    );
  }

  /** Toggle a preset language chip into/out of the comma-separated input. */
  toggleLanguage(language: string): void {
    const chips = this.toChips(this.form.languages);
    const exists = chips.some((lang) => lang.toLowerCase() === language.toLowerCase());
    const next = exists
      ? chips.filter((lang) => lang.toLowerCase() !== language.toLowerCase())
      : [...chips, language];
    this.form.languages = next.join(", ");
  }

  // ── Form actions ──────────────────────────────────────────────────────────

  submit(form: NgForm): void {
    this.submitted.set(true);
    if (form.invalid) {
      return;
    }

    const payload: MoviePayload = {
      title: this.form.title.trim(),
      genre: this.form.genre.trim(),
      durationMins: this.form.durationMins!,
      languages: this.form.languages.trim(),
      posterUrl: this.form.posterUrl.trim(),
      trailerUrl: this.form.trailerUrl.trim(),
      price: this.form.price ?? 0
    };

    this.error.set(null);
    this.submitting.set(true);

    const id = this.editingId();
    const request = id
      ? this.movieService.update(id, payload)
      : this.movieService.create(payload);

    request.subscribe({
      next: () => {
        this.submitting.set(false);
        this.resetForm();
      },
      error: (err) => {
        this.error.set(err?.error?.message ?? "Could not save the movie");
        this.submitting.set(false);
      }
    });
  }

  editMovie(movie: Movie): void {
    this.editingId.set(movie.id);
    this.error.set(null);
    this.submitted.set(false);
    this.form = {
      title: movie.title,
      genre: movie.genre,
      durationMins: movie.durationMins,
      languages: movie.languages,
      posterUrl: movie.posterUrl,
      trailerUrl: movie.trailerUrl,
      price: movie.price
    };
    this.movieForm?.resetForm(this.form);
  }

  /** Open the archival confirmation modal for a movie. */
  askDelete(movie: Movie): void {
    this.deleteTarget.set(movie);
  }

  cancelDelete(): void {
    this.deleteTarget.set(null);
  }

  /** Confirm deletion (soft-delete via the API) and close the modal. */
  confirmDelete(): void {
    const movie = this.deleteTarget();
    if (!movie) {
      return;
    }
    // If we were editing the movie being deleted, drop back to Add mode.
    if (this.editingId() === movie.id) {
      this.resetForm();
    }
    this.movieService.remove(movie.id).subscribe({
      next: () => this.deleteTarget.set(null),
      error: (err) => {
        this.error.set(err?.error?.message ?? "Could not delete the movie");
        this.deleteTarget.set(null);
      }
    });
  }

  cancelEdit(): void {
    this.resetForm();
  }

  private resetForm(): void {
    this.editingId.set(null);
    this.submitted.set(false);
    this.error.set(null);
    this.form = { ...EMPTY_FORM };
    this.movieForm?.resetForm(this.form);
  }
}
