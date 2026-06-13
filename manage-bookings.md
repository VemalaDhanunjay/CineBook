# All Bookings — Feature Documentation

The **All Bookings** feature is a read-side admin dashboard that surfaces every
reservation a theater has received: a KPI summary, a leaderboard carousel of the
most-booked movies, and a status-tabbed ledger of every booking row. It sits on
the admin route **`/manage-bookings`** and follows the same layout DNA as Manage
Movies / Manage Shows (dashboard header + KPI grid + auto carousel + ledger),
plus a custom page-level **movie filter** in the header that re-scopes every
visible metric and row in one click.

This document covers what was built, how it works end-to-end, the request/route
flow, the frontend structure, and an abstract of the business logic.

---

## 1. Business logic (abstract)

A **booking** is one reservation against one screening (show). It carries an
immutable seat-label snapshot, monetary totals, and a status that distinguishes
fully-confirmed bookings from partially / fully cancelled ones. The domain rules
for the admin view:

- **Theater scope is non-negotiable.** Every endpoint reads the calling admin's
  `theaterId` from their JWT and returns only bookings whose show belongs to
  that theater. The client never picks the scope.
- **Status drives every aggregation.** Three values: `CONFIRMED`,
  `PARTIALLY_CANCELLED`, `CANCELLED`. Footfall sums seats from every booking
  whose status is not `CANCELLED` (a partial cancellation still represents some
  attendance). Revenue is `totalAmount - refundAmount` so partial refunds net
  out cleanly without needing a separate "net" column.
- **"Most Booked" reflects real footfall, not gross sales.** The carousel
  ranking sums `seats_booked` grouped by movie, **excluding fully cancelled
  bookings**, so a movie that sold and then refunded everything doesn't sit at
  the top of the leaderboard.
- **Soft-deleted shows are invisible.** The `findAllByTheaterId` query joins on
  `Show` and filters `deleted = false`; soft-deleted movies are skipped at the
  enrichment step in the service, so the dashboard never references archived
  catalog rows.
- **The movie filter is a page-level scope, not a ledger-only filter.** Picking
  a movie re-scopes the KPI cards, the table rows, **and** the three tab badge
  counts. Clearing it (or "All movies") returns to the theater-wide aggregate.
- **Refunds are projected into the TOTAL cell.** There is intentionally no
  standalone refund column; the refund amount appears as a small second line
  inside the TOTAL cell whenever `refundAmount > 0`, so the row's monetary
  footprint stays compact.

---

## 2. Data model

The `bookings` table / `Booking` entity already existed in the backend; this
feature added the dashboard-specific queries, DTOs, service, and controller
on top of it. No schema or migration changes.

| Field            | Type            | Notes                                              |
|------------------|-----------------|----------------------------------------------------|
| `id`             | `Long`          | PK, auto-generated; rendered as `BK-{id}` on UI    |
| `showId`         | `Long`          | FK → `shows`                                       |
| `userId`         | `Long`          | FK → `users` (the booker)                          |
| `seats`          | `String(500)`   | immutable CSV snapshot, e.g. `"A1,A2,B5"`          |
| `seatsBooked`    | `Integer`       | count of seats at booking time                     |
| `subtotal`       | `BigDecimal`    | price before tax                                   |
| `taxAmount`      | `BigDecimal`    | tax component                                      |
| `totalAmount`    | `BigDecimal`    | final amount charged                               |
| `refundAmount`   | `BigDecimal`    | accumulated refund value (`0` while CONFIRMED)     |
| `status`         | `BookingStatus` | `CONFIRMED` \| `PARTIALLY_CANCELLED` \| `CANCELLED` |
| `bookingDate`    | `LocalDateTime` | server-stamped creation time                       |
| `cancelledAt`    | `LocalDateTime` | set when the whole booking is cancelled            |

The per-seat current state lives in `BookingSeat` rows (authoritative for
partial cancellations); the admin dashboard does not need them — it summarises
at the booking level.

---

## 3. Backend (Spring Boot, `com.cinebook`)

Five new/edited classes, all following the existing Movie/Show layer conventions
(constructor injection, `@Transactional(readOnly = true)` for reads, theater
scoping via JWT, `ApiException` for failures, `@PreAuthorize` for admin routes).

### `repository/BookingRepository.java` — *edited*
Two custom `@Query` methods added next to the existing finders:

- `findAllByTheaterId(theaterId)` — joins `Booking` to `Show` on the bare FK
  column, returns every booking belonging to one of the theater's non-deleted
  shows, newest first. JPQL uses an explicit entity join because neither entity
  has a `@ManyToOne` mapping:

  ```jpql
  SELECT b FROM Booking b JOIN Show s ON b.showId = s.id
  WHERE s.theaterId = :theaterId AND s.deleted = false
  ORDER BY b.bookingDate DESC
  ```

- `sumSeatsBookedByMovie(theaterId)` — the critical aggregation: sums
  `seatsBooked` and counts bookings per `movieId` for the admin's theater,
  excluding `CANCELLED` bookings. Returns a Spring Data projection interface
  ordered by seat total descending (index 0 is the leader for the carousel).

### `repository/MovieSeatAggregate.java` — *new*
Spring Data projection interface (`Long getMovieId()`, `getTotalSeats()`,
`getTotalBookings()`) typed as the return shape of the aggregation query above.

### `dto/AdminBookingResponse.java` — *new*
Flat response row pre-joined on the server (movie + show + theater + user) so
the Angular table renders without secondary lookups. Carries:
- booking core: `id`, `userId`, `username`
- catalog context: `movieId`, `movieTitle`, `moviePosterUrl`
- show context: `showId`, `showTime`, `theaterName`, `theaterLocation`
- seats: `seatsBooked`, `seatNumbers` (the CSV)
- money: `subtotal`, `taxAmount`, `totalAmount`, `refundAmount`
- lifecycle: `status`, `bookingDate`, `cancelledAt`

### `dto/MostBookedMovieResponse.java` — *new*
Carousel row: `movieId`, `title`, `posterUrl`, `totalSeatsBooked`, `totalBookings`.

### `service/BookingService.java` — *new*
Two public read methods:

- `listForTheater(theaterId)` — fetches bookings via the new repo method, then
  builds `Map<Long, Show>` / `Map<Long, Movie>` / `Map<Long, User>` lookups
  with one batched `findAllById(...)` per type so enrichment is N+1-free.
  Theater is loaded once. Maps each booking to `AdminBookingResponse`; falls
  back to `"(unavailable)"` / `"User #{id}"` for soft-deleted references so a
  single missing row never breaks the page.
- `mostBookedMovies(theaterId)` — calls `sumSeatsBookedByMovie`, batches a
  `findAllById` over the movie ids, drops any that are soft-deleted, preserves
  the seat-total ordering, and caps the result at the top 5.

Guards: `requireTheater(theaterId)` raises `ApiException.forbidden("No theater is
associated with this account")` if the JWT's `theaterId` is null (a non-admin
somehow reaching the endpoint).

### `controller/AdminBookingController.java` — *new* — base path `/api/admin/bookings`
Class-level `@PreAuthorize("hasRole('ADMIN')")`. Reads the principal with
`@AuthenticationPrincipal AuthPrincipal principal` and passes
`principal.theaterId()` into the service.

| Method | Path                                  | Returns                              |
|--------|---------------------------------------|--------------------------------------|
| GET    | `/api/admin/bookings`                 | `List<AdminBookingResponse>` (theater) |
| GET    | `/api/admin/bookings/most-booked`     | `List<MostBookedMovieResponse>` (top 5) |

Security context is populated by the existing `JwtAuthFilter`; `SecurityConfig`
already protects all `/api/**` routes, so no config change was needed.

---

## 4. Request / route flow

### Backend request flow (loading the admin dashboard)
```
Admin (browser)
  → GET /api/admin/bookings
      Header: Authorization: Bearer <JWT>
  → JwtAuthFilter verifies the token, builds AuthPrincipal(userId, username, role, theaterId)
  → SecurityConfig allows /api/** for authenticated; @PreAuthorize requires ROLE_ADMIN
  → AdminBookingController.list(principal)
      reads theaterId from principal (NOT the body)
  → BookingService.listForTheater(theaterId)
      • bookingRepository.findAllByTheaterId(...)
      • batched lookups for Show/Movie/User; single fetch for Theater
      • maps each Booking to AdminBookingResponse (soft-deleted refs fall back gracefully)
  → 200 OK  [ { id, username, movieTitle, moviePosterUrl, showTime, theaterName,
                seatsBooked, seatNumbers, totalAmount, refundAmount,
                status, bookingDate, cancelledAt, ... }, ... ]

  → GET /api/admin/bookings/most-booked   (fired in parallel by the frontend)
      same auth path
  → BookingService.mostBookedMovies(theaterId)
      • bookingRepository.sumSeatsBookedByMovie(...)  — JPQL GROUP BY
      • drops soft-deleted movies, caps at top 5
  → 200 OK  [ { movieId, title, posterUrl, totalSeatsBooked, totalBookings }, ... ]
```
No write paths exist on this page — the dashboard is read-only.

### Frontend route flow
```
app.routes.ts
  path: "manage-bookings"
  canActivate: [authGuard, adminGuard]          // logged-in + ADMIN only
  loadComponent: ManageBookingsComponent         // lazy-loaded chunk

Sidebar (shared) → NAV_ITEMS now includes
  { id: "sidebar-bookings", label: "All Bookings", route: "/manage-bookings",
    icon: "ticket", roles: ["ADMIN"] }
  → routerLinkActive highlights the link when on the page
```
`authGuard` bounces anonymous users to `/login`; `adminGuard` keeps non-admins out.

---

## 5. Frontend (Angular — standalone, signals, custom dropdown)

All new/edited files and their roles:

| File | Role |
|------|------|
| `core/models/catalog.model.ts` | added `AdminBooking` and `MostBookedMovie` interfaces |
| `core/services/booking.service.ts` | signal-based read store; `load()` fans out via `forkJoin` |
| `features/admin/manage-bookings/manage-bookings.ts` | component logic (filter, KPIs, tabs, carousel, dropdown menu) |
| `features/admin/manage-bookings/manage-bookings.html` | template (header + filter, KPI grid, carousel, ledger) |
| `features/admin/manage-bookings/manage-bookings.css` | local styles reusing the shared theme + new dropdown + status badge variants |
| `app.routes.ts` | guarded, lazy `manage-bookings` route |
| `shared/sidebar/sidebar.ts` + `.html` | new "All Bookings" nav item + `LucideTicket` icon |

### `BookingService`
A singleton signal store mirroring `ShowService`'s shape. Holds two signals:
`bookings: AdminBooking[]` and `mostBooked: MostBookedMovie[]`. The single
`load()` method fires both `GET` requests in parallel via `forkJoin` and seeds
both signals in one `tap`, so the KPI grid, carousel and ledger all light up
together on the first paint.

### `ManageBookingsComponent`
Injects `BookingService`. On init it calls `load().subscribe({ error: log })`:
signals are seeded with `[]`/`0` so the KPI grid renders zeros immediately even
when the backend is unreachable, and a failed load is logged without breaking
the page (real data flows in later if the backend comes back). State is held in
signals: `movieFilter` (id or `"ALL"`), `statusTab` (`ALL` / `CONFIRMED` /
`CANCELLED`), `dropdownOpen`, `slideIndex`. Derived values:

- **`movieOptions`** — distinct movies seen across the booking list, enriched
  with `posterUrl` and a per-movie `bookingCount`, sorted alphabetically.
- **`selectedMovie`** — the currently picked option or `null` when `"ALL"`.
  Drives the trigger button's appearance (poster + title + count vs. empty
  state).
- **`scopedBookings`** — the single page-level filter result. Every KPI, the
  table, and every tab count derive from this one computed; flipping the
  dropdown re-keys the whole page in one signal update.
- **`tabbedBookings`** — `scopedBookings` filtered by the active status tab
  (drives only the table rows, not the KPIs).
- **KPIs**: `totalBookings`, `totalFootSteps` (sum of `seatsBooked` where
  `status !== "CANCELLED"`), `revenue` (`totalAmount − refundAmount`),
  `refundedAmount`, `cancellationCount` (rows where any refund was applied).
- **Tab counts**: `allCount`, `confirmedCount`, `cancelledCount` (the cancelled
  tab includes both `CANCELLED` and `PARTIALLY_CANCELLED`).

### The page (5 sections, all element ids prefixed `ab-`)

1. **Header + filter** (`ab-header`):
   - Left: title "All Bookings" + subtitle "Every reservation made across the
     platform."
   - Right: the **rich movie filter** (`ab-filter-wrap`). The trigger
     (`ab-filter-trigger`) shows either a film-icon + "All movies" / "Showing
     every reservation" in the empty state, or a small poster thumbnail + movie
     title + "{N} bookings" + an inline ✕ clear button (`ab-filter-clear`) when
     a movie is picked. A chevron rotates 180° on open. The menu
     (`ab-filter-menu`) is rendered with `@if (dropdownOpen())` as an absolute-
     positioned card: a sticky "All movies" row first, then one row per option
     (`ab-filter-row-{id}`) with poster + title + count + a tomato check on the
     active row. Click outside the wrapper or press Escape to close (host
     listeners on `document:click` and `document:keydown.escape`).

2. **KPI grid** (`ab-kpi-grid`) — four cards:
   - **Total Bookings** (`ab-kpi-bookings-value`) — count of scoped rows.
   - **Total Foot Steps** (`ab-kpi-footsteps-value`) — summed confirmed seats.
   - **Revenue** (`ab-kpi-revenue-value`) — rendered in `text-emerald-600`;
     net of refunds.
   - **Refunded** (`ab-kpi-refunded-value`) — total refund value, with a small
     tracking line (`ab-kpi-refunded-track`) showing the cancellation count.

3. **"Most Booked" carousel** (`ab-carousel`) — reuses the shared
   `.carousel-track / -slide / -bg / -overlay / -inner / -poster / -details /
   -dots / -dot` classes from Manage Shows. Cross-fades every 4 s over the top
   5 movies; blurred poster backdrop with a tomato eyebrow "Most Booked · {N}
   seats", movie title, and "{N} bookings" footer. Hidden when `mostBooked()`
   is empty.

4. **Ledger** (`ab-ledger`) with **status tabs** (`ab-ledger-tabs`):
   `[All · {n}] [Confirmed · {n}] [Cancelled · {n}]` pills using the shared
   `.filter-btn` style; counts derive from `scopedBookings()` so they shift
   with the movie filter. The previous in-ledger movie selector is gone — the
   header dropdown is the single source of truth.

5. **Ledger table** (`ab-table`, row id `ab-row-{id}`) — columns:
   - **REF** — `BK-{id}` in `font-mono`.
   - **USER** — initials chip (`.avatar-initials`) + username.
   - **MOVIE / SHOW** — poster thumb + title + show date/time + theater + location.
   - **SEATS** — integer count.
   - **SEAT NUMBERS** — the raw CSV in `font-mono`.
   - **TOTAL** — `totalAmount` with an inline `Refunded ₹{n}` second line
     projected directly into the cell when `refundAmount > 0`. No standalone
     refund column.
   - **STATUS** — coloured `.status-badge` variants: `.status-confirmed`
     (green), `.status-partial` (amber), `.status-cancelled` (tomato).
   - **BOOKED AT** — full raw timestamp string from `bookingDate`.
   - Empty state row (`ab-empty-row`) when `tabbedBookings()` is empty.

### Styling
Reuses the tomato/ink theme exactly — `tomato-500` `#ff4d4d` (primary, active
states, carousel dots), `tomato-400` `#ff6f59` (carousel eyebrow), `tomato-600`
`#e63946` (refund line, danger), `ink-900/800/700` surfaces and borders. Shared
`.card` / `.input` / `.btn-primary` / `.btn-ghost` / `.chip` classes plus local:

- **Carousel** — `.carousel-*` block copied verbatim from `manage-shows.css`.
- **Tab pills** — `.filter-btn` / `.filter-btn.is-active`.
- **Status badges** — `.status-badge` base plus three booking-specific tints
  (`.status-confirmed`, `.status-partial`, `.status-cancelled`).
- **Avatar chip** — `.avatar-initials` (tomato-tinted circular initials).
- **Rich dropdown** — `.filter-trigger` (h-14 card-shaped pill with hover,
  focus, and `.is-open` states), `.filter-trigger-poster / -icon / -text /
  -title / -meta / -clear / -chevron` (the chevron rotates via `.is-flipped`),
  `.filter-menu` (absolute panel, `max-h-[22rem]` with internal scroll, shadow,
  ring), `.filter-menu-row` with a tomato-tinted `.is-active` state.

---

## 6. How to run / verify

**Backend** — `cd backend && ./mvnw spring-boot:run` (port 8181). With an ADMIN JWT:
- `GET /api/admin/bookings` → `200`, every row's underlying show belongs to the
  caller's theater. Soft-deleted shows / movies do not appear.
- `GET /api/admin/bookings/most-booked` → `200`, up to 5 rows ordered by summed
  seats descending; rows whose movie was soft-deleted are skipped.
- Same endpoints with a non-ADMIN JWT → `403 Forbidden`.
- Same endpoints with an admin whose `theaterId` is `null` → `403 Forbidden`
  ("No theater is associated with this account").

**Frontend** — `cd frontend && npm start` (proxies to 8181). Log in as ADMIN and
open `/manage-bookings`:
- The sidebar shows the new **All Bookings** link (highlighted on the page).
- The page header reads "All Bookings — Every reservation made across the
  platform." with the rich movie filter to its right.
- **Default state** (no filter applied) → trigger reads "All movies — Showing
  every reservation". KPIs and tab counts reflect the theater-wide aggregate.
- **Click the trigger** → the menu drops below it, chevron rotates, "All
  movies" is checked. Outside-click and Escape both close it.
- **Pick a movie** → the trigger collapses into a poster + title + "{N}
  bookings" + ✕ clear pill. **Every KPI, every table row, and every tab count
  re-scope to that movie.** Click the ✕ to revert to the aggregate without
  re-opening the menu.
- **Carousel** auto-advances every 4 s over the top-booked movies (hidden when
  the theater has no bookings yet).
- **Status tabs** slice the visible ledger rows within the current movie
  scope; cancelled rows show the refund amount inline under their TOTAL.
