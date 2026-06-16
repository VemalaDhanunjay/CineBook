# Manage Movies — Feature Documentation

The **Manage Movies** feature is the admin-side catalog editor: a dashboard
header with a live movie counter, an auto carousel of the most recently added
titles, a two-column **add / edit form with a live preview**, an existing-movies
ledger table, and an archival (soft-delete) confirmation modal. It sits on the
admin route **`/manage-movies`** and follows the same layout DNA as Manage Shows
/ All Bookings (dashboard header + KPI counter + auto carousel + ledger), plus a
write side: create, edit, and soft-delete movies in the global catalog with the
catalog signal updating reactively so every section re-renders in one click.

This document covers what was built, how it works end-to-end, the request/route
flow, the frontend structure, and an abstract of the business logic.

---

## 1. Business logic (abstract)

A **movie** is a global catalog entry — title, genre, runtime, languages, price,
poster, and trailer — that admins curate and that shows later attach to a
theater. The domain rules for the admin view:

- **Movies are a global catalog, not theater-scoped.** Unlike bookings or shows,
  the `Movie` entity carries no `theaterId`. Any ADMIN can create / edit / delete
  any movie; per-theater isolation happens one layer up, where `Show` links a
  movie to a theater. The JWT still carries `theaterId`, but movie write
  endpoints do not read it.
- **Reads are open, writes are ADMIN-only.** `GET /api/movies` and
  `GET /api/movies/{id}` are available to any authenticated user (the user-facing
  booking catalog consumes them). `POST` / `PUT` / `DELETE` require
  `@PreAuthorize("hasRole('ADMIN')")`.
- **Delete is always a soft delete.** `deleteMovie` flips `deleted = true` and
  saves; rows are never physically removed, so shows and past bookings that
  reference a movie are never orphaned. The archival modal copy reflects this:
  "the movie and its shows will be archived… Past bookings and ticket history are
  preserved."
- **Soft-deleted movies are invisible.** `listMovies()` returns only
  `findByDeletedFalse()`, and `getMovie(id)` filters out a row whose `deleted`
  flag is set, raising `404 Movie not found` — so an archived title behaves
  exactly like one that never existed.
- **Validation is declarative and server-trusted.** `MovieRequest` carries Bean
  Validation constraints (`@NotBlank`, `@Size`, `@Positive`, `@PositiveOrZero`)
  enforced by `@Valid`; the frontend mirrors the same limits in the template, but
  the server is the source of truth and trims every string before persisting.
- **Price is optional on the client, never null on the server.** The form leaves
  price blank (`null`) by default; submit coalesces it to `₹0`
  (`this.form.price ?? 0`), and the request constraint is `@PositiveOrZero`, so a
  free movie is valid.
- **The catalog signal is the single source of truth on the client.** Every write
  (`create` / `update` / `remove`) patches the in-memory `movies` signal inside a
  `tap`, so the counter, carousel, live preview and ledger all re-render without
  a refetch.

---

## 2. Data model

The `movies` table / `Movie` entity is the catalog root. This feature exercises
the entity's CRUD; there are no soft-delete timestamps or audit columns.

| Field          | Type         | Notes                                                  |
|----------------|--------------|--------------------------------------------------------|
| `id`           | `Long`       | PK, `IDENTITY` auto-generated; rendered as `#{id}` on UI |
| `title`        | `String(150)`| required (`nullable = false`)                          |
| `genre`        | `String(50)` | CSV, e.g. `"Action,Drama"`; rendered as chips          |
| `durationMins` | `Integer`    | column `duration_mins`; runtime in minutes             |
| `languages`    | `String(250)`| CSV, e.g. `"Telugu,Hindi,English"`                     |
| `price`        | `BigDecimal` | `precision 10, scale 2`; default ticket price          |
| `posterUrl`    | `String(500)`| column `poster_url`; portrait image URL                |
| `trailerUrl`   | `String(500)`| column `trailer_url`; YouTube URL                      |
| `deleted`      | `boolean`    | `nullable = false`, default `false` — soft-delete flag |

There is **no theater FK and no timestamps** on the movie row. Poster and trailer
are stored as plain URL strings — there is no file/multipart upload; the client
supplies already-hosted URLs.

---

## 3. Backend (Spring Boot, `com.cinebook`)

Movie CRUD follows the existing layer conventions (constructor injection,
`@Transactional` for writes, `ApiException` for failures, `@PreAuthorize` for
admin write routes, `@Valid` for request validation).

### `entity/Movie.java`
The catalog entity mapped to table `movies` with the fields in §2. The
soft-delete flag is a plain boolean defaulting to `false`:

```java
@Column(nullable = false)
private boolean deleted = false;
```

### `repository/MovieRepository.java`
Extends `JpaRepository<Movie, Long>` (so `save` / `findById` / `findAllById` are
inherited) and adds one derived query used by the listing path:

```java
List<Movie> findByDeletedFalse();
```

No custom JPQL or native query is needed — the soft-delete filter is the only
catalog-level rule, and `getMovie` applies the deleted check in the service.

### `dto/MovieRequest.java`
The create/update payload, validated by `@Valid` at the controller. No `id`
field (the DB assigns it); constraints mirror the column limits:

```java
@NotBlank @Size(max = 150)   private String title;
@NotBlank @Size(max = 50)    private String genre;
@NotNull  @Positive          private Integer durationMins;
@NotBlank @Size(max = 250)   private String languages;
@NotBlank @Size(max = 500)   private String posterUrl;
@NotBlank @Size(max = 500)   private String trailerUrl;
@NotNull  @PositiveOrZero    private BigDecimal price;
```

The `Movie` entity is returned directly as the response body (it carries `id` and
`deleted` in addition to the request fields).

### `service/MovieService.java`
Five public methods plus a private `apply` mapper:

- `listMovies()` — returns `movieRepository.findByDeletedFalse()` (non-deleted
  catalog only).
- `getMovie(id)` — `findById(id).filter(m -> !m.isDeleted())`, throwing
  `ApiException.notFound("Movie not found")` when missing or archived.
- `createMovie(request)` *(`@Transactional`)* — new `Movie`, `apply(...)`, save.
- `updateMovie(id, request)` *(`@Transactional`)* — `getMovie(id)` (404-guarded),
  `apply(...)`, save.
- `deleteMovie(id)` *(`@Transactional`)* — `getMovie(id)`, `setDeleted(true)`,
  save (soft delete).

`apply(movie, request)` copies every field and **trims each string** before set
(`title`, `genre`, `languages`, `posterUrl`, `trailerUrl`), so whitespace never
reaches the database.

### `controller/MovieController.java` — base path `/api/movies`
Read endpoints are open to any authenticated user; write endpoints carry a
method-level `@PreAuthorize("hasRole('ADMIN')")`. The controller does **not**
inject `@AuthenticationPrincipal` — there is no theater scoping on movies.

| Method | Path                | Auth          | Request           | Returns                  |
|--------|---------------------|---------------|-------------------|--------------------------|
| GET    | `/api/movies`       | Authenticated | —                 | `List<Movie>` · 200      |
| GET    | `/api/movies/{id}`  | Authenticated | path `id`         | `Movie` · 200            |
| POST   | `/api/movies`       | ADMIN         | `@Valid MovieRequest` | `Movie` · 201 Created |
| PUT    | `/api/movies/{id}`  | ADMIN         | path `id` + `@Valid MovieRequest` | `Movie` · 200 |
| DELETE | `/api/movies/{id}`  | ADMIN         | path `id`         | empty · 204 No Content   |

Security context is populated by the existing `JwtAuthFilter`; `SecurityConfig`
(`@EnableMethodSecurity`, stateless JWT, `/api/auth/**` public, everything else
authenticated) already protects all `/api/**` routes, so no config change was
needed. A non-ADMIN hitting a write route is rejected by `@PreAuthorize` and
mapped to `403` by `GlobalExceptionHandler`; an invalid `MovieRequest` becomes a
`400` with the field errors joined into the message.

---

## 4. Request / route flow

### Backend request flow (loading + editing the catalog)
```
Admin (browser)
  → GET /api/movies
      Header: Authorization: Bearer <JWT>
  → JwtAuthFilter verifies the token, builds AuthPrincipal(userId, username, role, theaterId)
  → SecurityConfig allows /api/** for authenticated (GET needs no ADMIN role)
  → MovieController.list()
  → MovieService.listMovies()  →  movieRepository.findByDeletedFalse()
  → 200 OK  [ { id, title, genre, durationMins, languages, price,
                posterUrl, trailerUrl, deleted }, ... ]

  → POST /api/movies            (create)
      @PreAuthorize requires ROLE_ADMIN; @Valid validates MovieRequest
  → MovieService.createMovie(request)  →  apply(...) (trims fields)  →  save
  → 201 Created  { id, title, ... }

  → PUT /api/movies/{id}         (edit)
      ROLE_ADMIN + @Valid
  → MovieService.updateMovie(id, request)
      • getMovie(id)  — 404 if missing or soft-deleted
      • apply(...) + save
  → 200 OK  { id, title, ... }

  → DELETE /api/movies/{id}      (archive)
      ROLE_ADMIN
  → MovieService.deleteMovie(id)  →  getMovie(id) + setDeleted(true) + save
  → 204 No Content
```
Movie writes are global — no `theaterId` is read from the principal.

### Frontend route flow
```
app.routes.ts
  path: "manage-movies"
  canActivate: [authGuard, adminGuard]          // logged-in + ADMIN only
  loadComponent: ManageMoviesComponent           // lazy-loaded chunk

Sidebar (shared) → NAV_ITEMS includes
  { id: "sidebar-manage", label: "Manage Movies", route: "/manage-movies",
    icon: "clapperboard", roles: ["ADMIN"] }
  → routerLinkActive highlights the link when on the page
```
`authGuard` bounces anonymous users to `/login`; `adminGuard` keeps non-admins out.

---

## 5. Frontend (Angular — standalone, signals, ngModel form)

All files and their roles:

| File | Role |
|------|------|
| `core/models/catalog.model.ts` | `Movie` interface + `MoviePayload` (`Omit<Movie, "id" \| "deleted">`) |
| `core/services/movie.service.ts` | signal-based CRUD store; `load`, `getOne`, `create`, `update`, `remove` |
| `features/admin/manage-movies/manage-movies.ts` | component logic (form, preview, edit, soft-delete modal) |
| `features/admin/manage-movies/manage-movies.html` | template (header + counter, carousel, form + live preview, ledger, modal) |
| `features/admin/manage-movies/manage-movies.css` | local styles reusing the shared theme + chip / lang-chip variants |
| `app.routes.ts` | guarded, lazy `manage-movies` route |
| `shared/sidebar/sidebar.ts` + `.html` | "Manage Movies" nav item + `clapperboard` icon |
| `shared/carousel/carousel.ts` | shared carousel reused for the "Now in catalog" ribbon |

### `MovieService`
A singleton signal store (`providedIn: "root"`) keyed off
`${environment.apiUrl}/movies`. Holds one signal and two computed values:

- **`movies: Movie[]`** — the catalog, newest last (backend insertion order).
- **`count`** — `movies().length`, used by the dashboard counter.
- **`latestThree`** — `movies().slice(-3).reverse()`, drives the top carousel.

`load()` does a `GET` and `.set(...)`s the signal. Every write patches the signal
in a `tap` so the UI never refetches: `create` appends, `update` maps the matching
id to the server response, `remove` filters the id out. The auth interceptor
attaches the admin JWT to the write calls.

### `ManageMoviesComponent`
Injects `MovieService` and `DomSanitizer`. Catalog state is re-exposed from the
service signals (`movies`, `count`, `latestThree`). On init it calls
`movieService.load().subscribe()`. Local state in signals:

- **`editingId`** — `number | null`; `null` is "Add" mode, an id is "Edit" mode
  (drives the form heading, submit button label, and Cancel vs. Reset).
- **`submitting`** — true while a request is in flight (disables the submit
  button, shows the spinner).
- **`submitted`** — set on submit to reveal validation messages.
- **`error`** — last error message or `null`.
- **`deleteTarget`** — `Movie | null`; non-null opens the archival modal.

The form model `form: MovieForm` is a plain object bound via `ngModel` (number
fields start as `null` so inputs render blank). `presetLanguages` holds the six
chip options. Derived/helper methods:

- **`toChips(csv)`** — splits a CSV string into trimmed, non-empty chips (used for
  genre and language rendering everywhere).
- **`youTubeId(url)`** *(private)* / **`embedUrl(url)`** — extract an 11-char
  YouTube id from common URL shapes and return a sanitized
  `SafeResourceUrl` embed link, or `null`.
- **`isInvalidTrailer(url)`** — true when a URL is present but isn't a recognizable
  YouTube link.
- **`isLanguageSelected(language)`** / **`toggleLanguage(language)`** — read/append
  a preset chip into the comma-separated `languages` field (case-insensitive).
- **`submit(form)`** — guards on `form.invalid`, builds a trimmed `MoviePayload`
  (price coalesced to `0`), then calls `update(id, ...)` or `create(...)` by
  `editingId()`; resets the form on success, surfaces `err.error.message` on
  failure.
- **`editMovie(movie)`** — seeds the form from a row and switches to Edit mode.
- **`askDelete` / `cancelDelete` / `confirmDelete`** — drive the modal;
  `confirmDelete` drops back to Add mode if the movie being deleted is the one in
  the form, then calls `remove(id)`.
- **`cancelEdit` / `resetForm`** — clear the form back to `EMPTY_FORM` and Add mode.

### The page (sections, all element ids prefixed `mm-`)

1. **Header + counter** (`mm-header`):
   - Left: a clapperboard chip + title "Manage Movies" and subtitle
     (`mm-subtitle`) "Add new Movies and review the catalog."
   - Right: a card counter (`mm-counter`) whose value (`mm-counter-value`) shows
     `count()` in `tomato-500` beside a "Total movies" label.

2. **"Now in catalog" carousel** — the shared `<app-carousel [items]="latestThree()">`
   with a templated slide: a tomato eyebrow "Now in catalog", the movie title,
   genre chips (`chip-on-dark`), and a `{durationMins} min` line.

3. **Two-column grid** (`lg:grid-cols-2`):
   - **Left — add/edit form** (`mm-form`, `#movieForm="ngForm"`, `novalidate`):
     heading (`mm-form-heading`) reads "Edit movie" / "Add a Movie" by
     `editingId()`. Fields, each required unless noted:
     - **Title** (`mm-input-title`, text, max 150).
     - **Genre** (`mm-input-genre`, text, max 50) — rendered as live chips.
     - **Duration** (`mm-input-duration`, number, min 1).
     - **Languages** — preset chip row (`mm-lang-chips`, each button
       `mm-lang-chip-{language}` toggling `.is-selected`) plus a manual CSV input
       (`mm-input-languages`, max 250).
     - **Poster URL** (`mm-input-poster`, url, max 500, pattern `https?://.+`).
     - **YouTube Trailer URL** (`mm-input-trailer`, url, max 500, pattern
       `https?://.+`).
     - **Default Ticket Price** (`mm-input-price`, number, min 0, step 0.01) —
       optional, defaults to ₹0.
     - Actions: submit (`mm-submit`) showing "Saving…" / "Save changes" / "Add
       Movie" by state; a context button that is **Cancel** (`mm-cancel`) in edit
       mode or **Reset** (`mm-reset`) in add mode; and an error line
       (`mm-form-error`) when `error()` is set.
   - **Right — live preview** (`mm-preview`): poster (`mm-preview-poster`, or an
     "No poster yet" placeholder), title (`mm-preview-title`, "Untitled movie"
     fallback), a meta line (`mm-preview-meta`) with the runtime, genre chips
     (`mm-preview-genres`), and a "Trailer attached" badge (`mm-preview-trailer`)
     when `embedUrl(...)` resolves.

4. **Existing movies ledger** (`mm-table`) with a count pill (`mm-table-count`,
   "{n} total"). Columns: **ID** (`#{id}`), **Poster** (thumbnail), **Title**,
   **Genre** (chips), **Duration** (`{n} min`), **Price** (`| currency: "INR"`),
   and a right-aligned **Actions** cell with an **Edit** button
   (`mm-edit-{id}` → `editMovie`) and a tomato **Delete** button
   (`mm-delete-{id}` → `askDelete`). Each row is `mm-row-{id}`. When the catalog
   is empty, an empty state (`mm-table-empty`) prompts adding the first title.

5. **Archival confirmation modal** (`mm-delete-modal`) — shown via
   `@if (deleteTarget(); as target)`. A fixed overlay (backdrop click cancels) over
   a card asking 'Delete "{title}"?', explaining the movie and its shows are
   archived while bookings are preserved, with **Cancel** (`mm-delete-cancel`) and
   a tomato **Yes, delete movie** (`mm-delete-confirm` → `confirmDelete`).

### Styling
Reuses the tomato/ink theme exactly — `tomato-500` `#ff4d4d` (primary, active
states, counter value, active language chip), `tomato-400` `#ff6f59` (carousel
eyebrow), `tomato-600` `#e63946` (chip text, hover, error / delete). `ink-900`
surfaces and inputs, `ink-700` borders, `ink-600` input / chip borders. Shared
`.card` / `.input` / `.btn-primary` / `.btn-ghost` classes plus local
component-scoped styles:

- **Chips** — `.chip` (tomato-tinted pill) and `.chip-on-dark` (white-on-dark for
  the carousel slide).
- **Language toggles** — `.lang-chip` (bordered pill) with a `.is-selected`
  tomato-filled state.

---

## 6. How to run / verify

**Backend** — `cd backend && ./mvnw spring-boot:run` (port 8181). With an ADMIN JWT:
- `GET /api/movies` → `200`, every non-deleted catalog row; archived titles do
  not appear.
- `POST /api/movies` with a valid `MovieRequest` → `201`, the created movie
  (strings trimmed). An invalid body (blank title, non-positive duration, etc.)
  → `400` with the field errors in the message.
- `PUT /api/movies/{id}` → `200` for a live movie; `404 "Movie not found"` for a
  missing or already-archived id.
- `DELETE /api/movies/{id}` → `204`; the row is soft-deleted (`deleted = true`),
  not removed, so its shows and bookings stay intact, and it disappears from
  subsequent `GET` results.
- Any write endpoint with a non-ADMIN JWT → `403 Forbidden`.
- `GET /api/movies` / `GET /api/movies/{id}` with any authenticated user →
  `200` (reads are open).

**Frontend** — `cd frontend && npm start` (proxies to 8181). Log in as ADMIN and
open `/manage-movies`:
- The sidebar shows the **Manage Movies** link (highlighted on the page).
- The header reads "Manage Movies — Add new Movies and review the catalog." with
  the live **Total movies** counter on the right.
- **Carousel** cross-fades over the three most recently added titles.
- **Add a Movie** — fill the form; the **Live preview** updates as you type
  (poster, title, genre chips, "Trailer attached" badge for a valid YouTube URL).
  Tap preset **language chips** to build the CSV. Submitting appends the movie to
  the ledger and the counter without a refetch.
- **Edit** — clicking a row's **Edit** seeds the form, flips the heading to "Edit
  movie" and the button to "Save changes"; **Cancel** drops back to Add mode.
- **Delete** — clicking **Delete** opens the archival modal; confirming
  soft-deletes the movie (it leaves the table immediately) and any open edit of
  that movie resets to Add mode.
