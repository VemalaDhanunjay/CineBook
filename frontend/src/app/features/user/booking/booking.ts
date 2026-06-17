import { CurrencyPipe, DatePipe } from "@angular/common";
import { Component, OnInit, computed, inject, signal } from "@angular/core";
import { ActivatedRoute, Router } from "@angular/router";
import {
  LucideArmchair,
  LucideArrowLeft,
  LucideArrowRight,
  LucideCalendar,
  LucideClock,
  LucideHeart,
  LucideMapPin,
  LucidePlay,
  LucideTicket
} from "@lucide/angular";
import { switchMap, tap } from "rxjs";
import { Movie, MovieInterestStatus, PublicShow } from "../../../core/models/catalog.model";
import { MovieInterestService } from "../../../core/services/movie-interest.service";
import { MovieService } from "../../../core/services/movie.service";
import { PaymentService } from "../../../core/services/payment.service";
import { ShowService } from "../../../core/services/show.service";
import { UserBookingService } from "../../../core/services/user-booking.service";
import { TrailerModalComponent } from "../../../shared/trailer-modal/trailer-modal";

/**
 * User booking page rendered at `/movies/:id/book`. Three stacked steps:
 * 1) Show the movie summary + a Trailer CTA. If no upcoming shows exist, an
 *    "I'm interested" toggle lets the user signal demand instead.
 * 2) List upcoming shows; picking one reveals the seat picker.
 * 3) Confirm calls {@link UserBookingService.create} and routes to /my-bookings.
 */
@Component({
  selector: "app-booking",
  standalone: true,
  imports: [
    CurrencyPipe,
    DatePipe,
    TrailerModalComponent,
    LucideArmchair,
    LucideArrowLeft,
    LucideArrowRight,
    LucideCalendar,
    LucideClock,
    LucideHeart,
    LucideMapPin,
    LucidePlay,
    LucideTicket
  ],
  templateUrl: "./booking.html",
  styleUrl: "./booking.css"
})
export class BookingComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly movieService = inject(MovieService);
  private readonly showService = inject(ShowService);
  private readonly bookingService = inject(UserBookingService);
  private readonly paymentService = inject(PaymentService);
  private readonly interestService = inject(MovieInterestService);

  private static readonly TAX_RATE = 0.18;

  readonly movie = signal<Movie | null>(null);
  readonly shows = signal<PublicShow[]>([]);
  readonly selectedShow = signal<PublicShow | null>(null);
  readonly bookedSeats = signal<string[]>([]);
  readonly selectedSeats = signal<string[]>([]);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  /** Trailer modal open/closed. */
  readonly trailerOpen = signal(false);

  /** Interest state for the current movie — interested flag + total count. */
  readonly interest = signal<MovieInterestStatus | null>(null);
  readonly interestSubmitting = signal(false);

  /** Show to auto-select once shows load — set from a `?showId=` query param (e.g. from a theater page). */
  private pendingShowId: number | null = null;

  readonly rows = computed(() => {
    const show = this.selectedShow();
    if (!show) return [] as { label: string; seats: { label: string; booked: boolean; picked: boolean }[] }[];
    const total = show.totalSeats ?? 0;
    const seatsPerRow = 10;
    const rowCount = Math.ceil(total / seatsPerRow);
    const booked = new Set(this.bookedSeats());
    const picked = new Set(this.selectedSeats());
    const rows: { label: string; seats: { label: string; booked: boolean; picked: boolean }[] }[] = [];
    for (let r = 0; r < rowCount; r++) {
      const rowLetter = String.fromCharCode(65 + r);
      const seats: { label: string; booked: boolean; picked: boolean }[] = [];
      const cap = Math.min(seatsPerRow, total - r * seatsPerRow);
      for (let c = 1; c <= cap; c++) {
        const label = `${rowLetter}${c}`;
        seats.push({
          label,
          booked: booked.has(label),
          picked: picked.has(label)
        });
      }
      rows.push({ label: rowLetter, seats });
    }
    return rows;
  });

  readonly subtotal = computed(() => {
    const show = this.selectedShow();
    return show ? this.selectedSeats().length * Number(show.ticketPrice ?? 0) : 0;
  });
  readonly tax = computed(() => Math.round(this.subtotal() * BookingComponent.TAX_RATE * 100) / 100);
  readonly total = computed(() => Math.round((this.subtotal() + this.tax()) * 100) / 100);

  ngOnInit(): void {
    const showIdParam = Number(this.route.snapshot.queryParamMap.get("showId"));
    this.pendingShowId = showIdParam > 0 ? showIdParam : null;

    this.route.paramMap
      .pipe(
        tap(() => {
          this.movie.set(null);
          this.selectedShow.set(null);
          this.shows.set([]);
          this.interest.set(null);
        }),
        switchMap((params) => this.movieService.getOne(Number(params.get("id"))))
      )
      .subscribe({
        next: (movie) => {
          this.movie.set(movie);
          this.loadShows(movie.id);
          this.loadInterest(movie.id);
        },
        error: (err) => {
          console.error("Failed to load movie", err);
          this.error.set("Movie not found.");
        }
      });
  }

  private loadShows(movieId: number): void {
    this.showService.listForMovie(movieId).subscribe({
      next: (shows) => {
        this.shows.set(shows);
        // Jump straight to seat selection when arriving with a pre-selected show.
        if (this.pendingShowId != null) {
          const match = shows.find((s) => s.id === this.pendingShowId);
          if (match) this.pickShow(match);
          this.pendingShowId = null;
        }
      },
      error: (err) => {
        console.error("Failed to load shows", err);
        this.error.set("Could not load showtimes. Try again later.");
      }
    });
  }

  private loadInterest(movieId: number): void {
    this.interestService.status(movieId).subscribe({
      next: (status) => this.interest.set(status),
      error: (err) => console.warn("Could not load interest status", err)
    });
  }

  pickShow(show: PublicShow): void {
    this.selectedShow.set(show);
    this.selectedSeats.set([]);
    this.bookedSeats.set([]);
    this.error.set(null);
    this.bookingService.seatAvailability(show.id).subscribe({
      next: (snap) => this.bookedSeats.set(snap.booked ?? []),
      error: (err) => {
        console.error("Failed to load seat layout", err);
        this.error.set("Could not load seat layout.");
      }
    });
  }

  toggleSeat(label: string, booked: boolean): void {
    if (booked) return;
    this.selectedSeats.update((list) =>
      list.includes(label) ? list.filter((s) => s !== label) : [...list, label]
    );
  }

  resetShow(): void {
    this.selectedShow.set(null);
    this.selectedSeats.set([]);
    this.bookedSeats.set([]);
    this.error.set(null);
  }

  goBack(): void {
    this.router.navigate(["/movies"]);
  }

  openTrailer(): void {
    this.trailerOpen.set(true);
  }

  closeTrailer(): void {
    this.trailerOpen.set(false);
  }

  /** Flip the interest flag — POST when not interested, DELETE when already. */
  toggleInterest(): void {
    const movie = this.movie();
    const status = this.interest();
    if (!movie || this.interestSubmitting()) return;
    this.interestSubmitting.set(true);
    const obs = status?.interested
      ? this.interestService.unmark(movie.id)
      : this.interestService.mark(movie.id);
    obs.subscribe({
      next: (next) => {
        this.interest.set(next);
        this.interestSubmitting.set(false);
      },
      error: (err) => {
        this.interestSubmitting.set(false);
        this.error.set(err?.error?.message ?? "Could not update interest.");
      }
    });
  }

  confirm(): void {
    const show = this.selectedShow();
    if (!show || this.selectedSeats().length === 0) {
      this.error.set("Pick at least one seat.");
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    // Hold the seats + open Stripe Checkout, then hand off to the hosted payment page.
    this.paymentService
      .startCheckout({ showId: show.id, seatLabels: this.selectedSeats() })
      .subscribe({
        next: (res) => {
          window.location.href = res.checkoutUrl;
        },
        error: (err) => {
          this.submitting.set(false);
          this.error.set(err?.error?.message ?? "Could not start payment. Try again.");
        }
      });
  }
}
