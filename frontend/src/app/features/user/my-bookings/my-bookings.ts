import { CurrencyPipe, DatePipe, LowerCasePipe } from "@angular/common";
import { Component, OnInit, computed, inject, signal } from "@angular/core";
import { RouterLink } from "@angular/router";
import {
  LucideCalendar,
  LucideMapPin,
  LucideTicket,
  LucideX
} from "@lucide/angular";
import { RefundQuote, UserBooking } from "../../../core/models/catalog.model";
import { ReviewService } from "../../../core/services/review.service";
import { UserBookingService } from "../../../core/services/user-booking.service";
import { TomatoIconComponent } from "../../../shared/tomato-icon/tomato-icon";

/** Status-tab matching the admin "All Bookings" ledger pattern. */
type StatusTab = "ALL" | "CONFIRMED" | "CANCELLED";

/**
 * A seat in the cancel modal's auditorium layout:
 *   - mine: the caller's still-active seat (the only cancellable kind)
 *   - occupied: booked by someone else
 *   - cancelled: the caller already cancelled it
 *   - empty: unsold
 */
type SeatKind = "mine" | "occupied" | "cancelled" | "empty";
interface CancelSeat {
  label: string;
  kind: SeatKind;
}
interface CancelRow {
  row: string;
  seats: CancelSeat[];
}

/**
 * User-facing "My Bookings" ledger. Lists the caller's own reservations
 * (newest first) with status badges, refund info, and contextual actions:
 *   - Cancel: opens an interactive seat-cancellation modal (pick specific seats
 *     or the whole booking) with a live, policy-based refund estimate.
 *   - Rate: visible only after the show has played and the user hasn't yet
 *     reviewed the booking.
 *   - Tab strip: ALL / CONFIRMED / CANCELLED, mirroring the admin ledger.
 */
@Component({
  selector: "app-my-bookings",
  standalone: true,
  imports: [
    CurrencyPipe,
    DatePipe,
    LowerCasePipe,
    RouterLink,
    LucideCalendar,
    LucideMapPin,
    LucideTicket,
    LucideX,
    TomatoIconComponent
  ],
  templateUrl: "./my-bookings.html",
  styleUrl: "./my-bookings.css"
})
export class MyBookingsComponent implements OnInit {
  private readonly bookingService = inject(UserBookingService);
  private readonly reviewService = inject(ReviewService);

  readonly bookings = this.bookingService.bookings;

  /** Booking the user is currently rating; null when the modal is closed. */
  readonly rateTarget = signal<UserBooking | null>(null);
  readonly ratingValue = signal(0);
  /** Tomato the cursor is hovering in the rate picker (0 = none) — drives the live preview. */
  readonly rateHover = signal(0);
  readonly submittingRating = signal(false);
  readonly cancellingId = signal<number | null>(null);
  readonly error = signal<string | null>(null);

  // ── Cancel modal state ───────────────────────────────────────────────────
  /** Booking whose seats are being cancelled; null when the modal is closed. */
  readonly cancelTarget = signal<UserBooking | null>(null);
  /** Seat labels the user has marked for cancellation. */
  readonly seatsToCancel = signal<string[]>([]);
  /** Refund preview for the cancel target (per-seat amount + policy %). */
  readonly cancelQuote = signal<RefundQuote | null>(null);
  readonly loadingQuote = signal(false);
  /** Show capacity + all currently-booked labels, for rendering the full auditorium. */
  readonly cancelTotalSeats = signal(0);
  readonly cancelBookedAll = signal<string[]>([]);

  /** Active status-tab. Mirrors the admin "All Bookings" tab strip. */
  readonly statusTab = signal<StatusTab>("ALL");

  /** Bookings narrowed by the active tab — drives the rendered list. */
  readonly visibleBookings = computed(() => {
    const tab = this.statusTab();
    const rows = this.bookings();
    if (tab === "ALL") return rows;
    if (tab === "CONFIRMED") return rows.filter((b) => b.status === "CONFIRMED");
    // "CANCELLED" tab also surfaces partial cancellations — same as admin pattern.
    return rows.filter(
      (b) => b.status === "CANCELLED" || b.status === "PARTIALLY_CANCELLED"
    );
  });

  readonly allCount = computed(() => this.bookings().length);
  readonly confirmedCount = computed(
    () => this.bookings().filter((b) => b.status === "CONFIRMED").length
  );
  readonly cancelledCount = computed(
    () =>
      this.bookings().filter(
        (b) => b.status === "CANCELLED" || b.status === "PARTIALLY_CANCELLED"
      ).length
  );

  readonly stars = [1, 2, 3, 4, 5];

  /**
   * Full auditorium grid for the cancel modal — every seat in the show, tagged by
   * kind. Built from `totalSeats` (10 seats/row, row letters A, B, … — matching how
   * the booking picker generates labels). Falls back to just the caller's own seats
   * if the show's seat map didn't load.
   */
  readonly cancelSeatRows = computed<CancelRow[]>(() => {
    const target = this.cancelTarget();
    if (!target) return [];
    const mineActive = new Set(target.activeSeats ?? []);
    const mineCancelled = new Set(target.cancelledSeats ?? []);
    const bookedAll = new Set(this.cancelBookedAll());
    const total = this.cancelTotalSeats();
    const seatsPerRow = 10;

    // Fallback: seat map unavailable — render just the caller's own seats grouped.
    if (total <= 0) {
      const byRow = new Map<string, CancelSeat[]>();
      for (const label of [...mineActive, ...mineCancelled]) {
        const row = label.replace(/[0-9]/g, "") || "?";
        if (!byRow.has(row)) byRow.set(row, []);
        byRow.get(row)!.push({ label, kind: mineActive.has(label) ? "mine" : "cancelled" });
      }
      return [...byRow.entries()]
        .sort((a, b) => a[0].localeCompare(b[0]))
        .map(([row, seats]) => ({
          row,
          seats: seats.sort((x, y) => this.seatNumber(x.label) - this.seatNumber(y.label))
        }));
    }

    const rowCount = Math.ceil(total / seatsPerRow);
    const rows: CancelRow[] = [];
    for (let r = 0; r < rowCount; r++) {
      const rowLetter = String.fromCharCode(65 + r);
      const cap = Math.min(seatsPerRow, total - r * seatsPerRow);
      const seats: CancelSeat[] = [];
      for (let c = 1; c <= cap; c++) {
        const label = `${rowLetter}${c}`;
        let kind: SeatKind;
        if (mineActive.has(label)) kind = "mine";
        else if (bookedAll.has(label)) kind = "occupied";
        else if (mineCancelled.has(label)) kind = "cancelled";
        else kind = "empty";
        seats.push({ label, kind });
      }
      rows.push({ row: rowLetter, seats });
    }
    return rows;
  });

  /** Live refund estimate = per-seat refund × number of seats picked. */
  readonly cancelRefundEstimate = computed(() => {
    const quote = this.cancelQuote();
    if (!quote) return 0;
    return quote.refundPerSeat * this.seatsToCancel().length;
  });

  ngOnInit(): void {
    this.bookingService.loadMine().subscribe({
      error: (err) => {
        console.error("Failed to load bookings", err);
        this.error.set("Could not load your bookings. Try again later.");
      }
    });
  }

  // ── Cancel flow ────────────────────────────────────────────────────────────

  openCancel(booking: UserBooking): void {
    if (booking.status === "CANCELLED") return;
    this.cancelTarget.set(booking);
    this.seatsToCancel.set([]);
    this.cancelQuote.set(null);
    this.cancelTotalSeats.set(0);
    this.cancelBookedAll.set([]);
    this.loadingQuote.set(true);
    this.error.set(null);
    this.bookingService.refundQuote(booking.id).subscribe({
      next: (quote) => {
        this.cancelQuote.set(quote);
        this.loadingQuote.set(false);
      },
      error: () => this.loadingQuote.set(false)
    });
    // Pull the show's full seat map so the modal can render the whole auditorium.
    this.bookingService.seatAvailability(booking.showId).subscribe({
      next: (snap) => {
        this.cancelTotalSeats.set(snap.totalSeats ?? 0);
        this.cancelBookedAll.set(snap.booked ?? []);
      },
      error: (err) => console.warn("Could not load seat map", err)
    });
  }

  closeCancel(): void {
    this.cancelTarget.set(null);
    this.seatsToCancel.set([]);
    this.cancelQuote.set(null);
    this.cancelTotalSeats.set(0);
    this.cancelBookedAll.set([]);
    this.loadingQuote.set(false);
  }

  isSeatSelected(label: string): boolean {
    return this.seatsToCancel().includes(label);
  }

  toggleCancelSeat(label: string): void {
    this.seatsToCancel.update((list) =>
      list.includes(label) ? list.filter((l) => l !== label) : [...list, label]
    );
  }

  /** Select every active seat — the "cancel the entire booking" shortcut. */
  selectAllSeats(): void {
    const target = this.cancelTarget();
    if (!target) return;
    this.seatsToCancel.set([...(target.activeSeats ?? [])]);
  }

  clearSeatSelection(): void {
    this.seatsToCancel.set([]);
  }

  confirmCancel(): void {
    const target = this.cancelTarget();
    const labels = this.seatsToCancel();
    if (!target || labels.length === 0) return;
    this.cancellingId.set(target.id);
    this.bookingService.cancelSeats(target.id, labels).subscribe({
      next: () => {
        this.cancellingId.set(null);
        this.closeCancel();
      },
      error: (err) => {
        this.cancellingId.set(null);
        this.error.set(err?.error?.message ?? "Cancellation failed.");
      }
    });
  }

  // ── Rating flow ──────────────────────────────────────────────────────────

  openRate(booking: UserBooking): void {
    this.rateTarget.set(booking);
    this.ratingValue.set(0);
    this.error.set(null);
  }

  closeRate(): void {
    this.rateTarget.set(null);
    this.ratingValue.set(0);
    this.submittingRating.set(false);
  }

  pickStar(value: number): void {
    this.ratingValue.set(value);
  }

  submitRating(): void {
    const target = this.rateTarget();
    const rating = this.ratingValue();
    if (!target || rating < 1) {
      this.error.set("Pick a rating between 1 and 5.");
      return;
    }
    this.submittingRating.set(true);
    this.reviewService.submit({ bookingId: target.id, rating }).subscribe({
      next: () => {
        this.submittingRating.set(false);
        // Flag the booking locally so the Rate button disappears immediately.
        this.bookingService.bookings.update((list) =>
          list.map((b) => (b.id === target.id ? { ...b, hasReview: true } : b))
        );
        this.closeRate();
      },
      error: (err) => {
        this.submittingRating.set(false);
        this.error.set(err?.error?.message ?? "Could not submit rating.");
      }
    });
  }

  statusClass(status: UserBooking["status"]): string {
    switch (status) {
      case "CONFIRMED":
        return "status-badge status-confirmed";
      case "PARTIALLY_CANCELLED":
        return "status-badge status-partial";
      case "CANCELLED":
        return "status-badge status-cancelled";
    }
  }

  setStatusTab(tab: StatusTab): void {
    this.statusTab.set(tab);
  }

  /**
   * Rating button only appears once the show has actually been watched:
   * <ul>
   *   <li>booking not yet reviewed,</li>
   *   <li>booking not fully cancelled,</li>
   *   <li>and the show's start time is in the past.</li>
   * </ul>
   * Mirrors the backend guard added to ReviewService.createReview.
   */
  canRate(b: UserBooking): boolean {
    if (b.hasReview || b.status === "CANCELLED") return false;
    if (!b.showTime) return false;
    return new Date(b.showTime).getTime() <= Date.now();
  }

  /**
   * Cancellation is offered only while the show is still upcoming — once the
   * start time passes the booking can no longer be cancelled (Rate takes over).
   * Fully-cancelled bookings never show Cancel.
   */
  canCancel(b: UserBooking): boolean {
    if (b.status === "CANCELLED") return false;
    if (!b.showTime) return true;
    return new Date(b.showTime).getTime() > Date.now();
  }

  /** Numeric part of a seat label ("A12" → 12) for natural ordering. */
  private seatNumber(label: string): number {
    const digits = label.replace(/[^0-9]/g, "");
    return digits ? Number(digits) : 0;
  }
}
