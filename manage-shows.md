# Manage Shows — Feature Documentation

The **Manage Shows** feature lets a theater admin schedule screenings ("shows") of
catalog movies for their own theater, and review/edit/delete them. It sits on the
admin route **`/manage-shows`** and is modeled on the existing **Manage Movies** page
(same dashboard + carousel + form + ledger layout and the same tomato/ink theme).

This document covers what was built, how it works end-to-end, the request/route flow,
the frontend structure, and an abstract of the business logic.

---

## 1. Business logic (abstract)

A **show** is one screening of a movie at a theater at a specific date/time, with a
ticket price, a language, and a seat capacity. The domain rules:

- **A show belongs to exactly one theater.** The owning `theaterId` is taken from the
  authenticated admin's JWT — never from the client — so an admin can only create, see,
  and mutate shows for *their* theater.
- **A show references an existing movie.** The movie must exist and not be archived
  (soft-deleted) before a show can be scheduled for it.
- **Seat capacity is bounded.** A show holds between **1 and 250** seats. On creation,
  `availableSeats == totalSeats` (the whole house is open). On edit, availability is
  shifted by the *change* in capacity rather than reset, so seats already sold stay
  accounted for: `newAvailable = clamp(oldAvailable + (newTotal - oldTotal), 0, newTotal)`.
- **Open vs. Closed is derived, not stored.** A show is *Open* (upcoming) while its
  start time is in the future and *Closed* once it has passed. There is no status column;
  it is computed from `showTime` vs. "now". An upcoming show can be **edited or deleted**;
  a closed show can only be **deleted**.
- **Deletes are soft.** Deleting flips a `deleted` flag instead of removing the row, so
  bookings and ticket history that reference the show are never orphaned. Soft-deleted
  shows are filtered out of every read.

---

## 2. Data model

The `shows` table / `Show` entity already existed in the backend; this feature added the
service, controller, and DTO around it.

| Field            | Type            | Notes                                            |
|------------------|-----------------|--------------------------------------------------|
| `id`             | `Long`          | PK, auto-generated                               |
| `movieId`        | `Long`          | FK → `movies` (must be a live movie)             |
| `theaterId`      | `Long`          | FK → `theaters`; **set from the JWT**            |
| `showTime`       | `LocalDateTime` | combined date + time of the screening            |
| `language`       | `String(40)`    | single language the show is screened in          |
| `ticketPrice`    | `BigDecimal`    | per-ticket price                                 |
| `totalSeats`     | `Integer`       | capacity (1–250, enforced on the frontend)       |
| `availableSeats` | `Integer`       | derived; `= totalSeats` on create                |
| `deleted`        | `boolean`       | soft-delete flag (default `false`)               |

No DB migration was required — Hibernate `ddl-auto=update` already maps the `shows` table.

---

## 3. Backend (Spring Boot, `com.cinebook`)

Three new classes, mirroring the Movie layer's conventions (constructor injection,
`@Transactional` writes, soft delete, `ApiException`, `@PreAuthorize` for writes).

### `dto/ShowRequest.java`
Validated create/update payload. Carries `movieId`, `showTime`, `language`,
`ticketPrice`, `totalSeats`. Deliberately **omits** `theaterId` (from JWT),
`availableSeats` (derived), and `deleted` (server-managed). Validation: `@NotNull`,
`@NotBlank @Size(max = 40)` on language, `@PositiveOrZero` on price, `@Positive` on seats.

### `service/ShowService.java`
Holds all the rules from §1:
- `listShows(theaterId)` → `showRepository.findByTheaterIdAndDeletedFalse(theaterId)`.
- `getShow(id)` → found and not deleted, else `ApiException.notFound`.
- `createShow(request, theaterId)` → validates the movie is live, sets fields,
  `availableSeats = totalSeats`, saves.
- `updateShow(id, request, theaterId)` → loads, **verifies ownership**, applies fields,
  and shifts `availableSeats` by the capacity delta.
- `deleteShow(id, theaterId)` → ownership check, then soft-delete.
- Guards: `requireTheater` (admin must carry a theaterId), `requireOwnership` (the show's
  theater must match the caller's) → `ApiException.forbidden` otherwise.

### `controller/ShowController.java` — base path `/api/shows`
Reads the caller's identity with `@AuthenticationPrincipal AuthPrincipal principal` and
passes `principal.theaterId()` into the service.

| Method | Path              | Auth                         | Behavior                                   |
|--------|-------------------|------------------------------|--------------------------------------------|
| GET    | `/api/shows`      | authenticated                | shows for the caller's theater             |
| POST   | `/api/shows`      | `hasRole('ADMIN')`           | create → `201 Created` with the saved show |
| PUT    | `/api/shows/{id}` | `hasRole('ADMIN')`           | update (ownership-checked)                 |
| DELETE | `/api/shows/{id}` | `hasRole('ADMIN')`           | soft-delete → `204 No Content`             |

Security context is populated by the existing `JwtAuthFilter`; `SecurityConfig` already
protects all `/api/**` routes.

---

## 4. Request / route flow

### Backend request flow (e.g. scheduling a show)
```
Admin (browser)
  → POST /api/shows  { movieId, showTime, language, ticketPrice, totalSeats }
      Header: Authorization: Bearer <JWT>
  → JwtAuthFilter verifies the token, builds AuthPrincipal(userId, username, role, theaterId)
  → SecurityConfig allows /api/** for authenticated; @PreAuthorize requires ROLE_ADMIN
  → ShowController.create(request, principal)
      reads theaterId from principal (NOT the body)
  → ShowService.createShow(request, theaterId)
      • validates the movie exists and isn't archived  → else 400
      • sets theaterId from the JWT, availableSeats = totalSeats
      • saves via ShowRepository
  → 201 Created  { id, movieId, theaterId, showTime, language, ticketPrice,
                    totalSeats, availableSeats, deleted:false }
```
Edit/delete follow the same path with an added ownership check (a show from another
theater → `403 Forbidden`).

### Frontend route flow
```
app.routes.ts
  path: "manage-shows"
  canActivate: [authGuard, adminGuard]        // logged-in + ADMIN only
  loadComponent: ManageShowsComponent          // lazy-loaded chunk

Sidebar (shared) → NAV_ITEMS includes
  { id: "sidebar-shows", label: "Manage Shows", route: "/manage-shows", roles: ["ADMIN"] }
  → routerLinkActive highlights the link when on the page
```
`authGuard` redirects anonymous users to `/login`; `adminGuard` keeps non-admins out.

---

## 5. Frontend (Angular — standalone, signals, template forms)

All new/edited files and their roles:

| File | Role |
|------|------|
| `core/models/catalog.model.ts` | added `ShowPayload` (`Pick<Show, movieId·showTime·language·ticketPrice·totalSeats>`) |
| `core/services/show.service.ts` | signal-based CRUD; `count`, `upcoming`, `closed` computeds |
| `features/admin/manage-shows/manage-shows.ts` | component logic (selection, form, carousel, filter) |
| `features/admin/manage-shows/manage-shows.html` | template (header, carousel, form, ledger, modal) |
| `features/admin/manage-shows/manage-shows.css` | local styles reusing the Manage Movies theme |
| `app.routes.ts` | guarded, lazy `manage-shows` route |
| `shared/sidebar/sidebar.ts` + `.html` | new "Manage Shows" nav item + calendar-clock icon |

### `ShowService`
A singleton cache, identical in shape to `MovieService`. It keeps `shows` in a `signal`
and updates it on every successful HTTP call (`tap`), so the header counter, carousel,
and table all re-render reactively. Computeds:
- `count` — total shows in the cache.
- `upcoming` — shows with `showTime > now`, soonest first (drives carousel + default filter).
- `closed` — shows with `showTime <= now`.

### `ManageShowsComponent`
Injects both `ShowService` (the shows) and `MovieService` (the catalog), and on init loads
both. Movie metadata (poster/title/genre/languages) is resolved from a `movieId → Movie`
`Map` computed off the movie cache. State is held in signals: `selectedMovieId`,
`selectedLanguage`, `editingId`, `submitting`, `submitted`, `error`, `deleteTarget`,
`filter`, `slideIndex`. Notable derived values:
- `selectedMovie` / `availableLanguages` — the chosen movie and its language chips.
- `carouselShows` — upcoming shows joined with their movie (only those with a known movie).
- `filteredShows` — the ledger rows for the active filter (upcoming / closed / all).
- `canSubmit` — gates the Schedule button: a movie is selected, a language is chosen, and
  date, time, price (≥ 0), and seats (1–250) are all filled.

### The page (4 sections, all element ids prefixed `ms-`)
1. **Header** (`ms-header`) — title, subtitle, and a counter (`ms-counter`) of upcoming shows.
2. **Carousel** (`ms-carousel`) — auto-advances every 4 s over upcoming shows: blurred movie
   poster background, "Scheduled · {date/time}" eyebrow, movie title, genre chips, and the
   movie's runtime in minutes; dot navigation (`ms-carousel-dot-{i}`).
3. **Schedule form** (`ms-form`):
   - **Movie selector** — a horizontally scrollable row of poster cards
     (`ms-movie-card-{id}`); the selected card shows a tomato **tick-mark** badge
     (`ms-movie-check-{id}`) and a small **Clear** button (`ms-movie-clear`).
   - **Date** (`ms-input-date`, `min` = today), **Time slot** (`ms-input-time`),
     **Ticket price** (`ms-input-price`, ₹ prefix), **Total seats**
     (`ms-input-seats`, `min=1 max=250` with an inline over-cap error).
   - **Language** (`ms-lang-chips`) — single-select badges built from the *selected movie's*
     languages.
   - **Schedule show** (`ms-submit`, disabled until `canSubmit()`), **Reset** (`ms-reset`).
4. **Ledger** (`ms-table`) — a status filter (`ms-filter-upcoming|closed|all`) and a count
   badge; columns: ID, Movie (poster + title + genre chips), Showtime (date + time), Price,
   Seats (`available / total`), Status (Open/Closed badge), Actions. **Edit** (`ms-edit-{id}`)
   appears only for upcoming shows; **Delete** (`ms-delete-{id}`) is always available and
   opens a confirmation modal (`ms-delete-modal`).

### Validation (frontend)
- Required fields (movie, date, time, price, seats, language) are enforced through
  `canSubmit()`; inline messages appear after a submit attempt.
- Seats are bounded to **1–250** via the input `max`, the `canSubmit()` check, and a
  dedicated over-cap message ("A show can hold at most 250 seats.").
- The date picker's `min` prevents scheduling in the past.

### Styling
Reuses the Manage Movies tomato/ink theme exactly — `tomato-500` `#ff4d4d` (primary,
active states, carousel dots, selected-card border + tick badge), `tomato-400` `#ff6f59`
(carousel eyebrow), `tomato-600` `#e63946` (danger/errors), `ink-900/800/700` surfaces and
borders. Shared `.card` / `.input` / `.btn-primary` / `.btn-ghost` classes plus local
`.chip`, `.lang-chip`, `.carousel-*`, `.movie-card`, `.filter-btn`, and `.status-*` badges
(green = Open, gray = Closed).

---

## 6. How to run / verify

**Backend** — `cd backend && ./mvnw spring-boot:run` (port 8181). With an ADMIN JWT:
- `POST /api/shows` → `201`, response has `theaterId` from the token and
  `availableSeats == totalSeats`.
- `GET /api/shows` → only the admin's theater's non-deleted shows.
- `PUT`/`DELETE` on another theater's show → `403`; an invalid `movieId` → `400`.

**Frontend** — `cd frontend && npm start` (proxies to 8181). Log in as ADMIN and open
`/manage-shows`:
- The sidebar shows the new **Manage Shows** link (highlighted on the page).
- Pick a movie → it shows a tick-mark and its languages populate; **Schedule** stays
  disabled until movie + date + time + price + seats (1–250) + language are all set.
- A scheduled show appears in the table as **Open** with Edit + Delete; a past-dated show
  shows **Closed** with Delete only. Reset clears everything; the filter toggles
  upcoming / closed / all.
