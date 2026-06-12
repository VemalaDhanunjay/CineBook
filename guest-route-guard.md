# Blocking logged-in users from `/login` and `/register`

## Problem
After a user logged in, pressing the browser **Back** button — or manually typing `/login` or `/register` in the address bar — still rendered the authentication forms. This was confusing UX (a logged-in user shouldn't see a "Login" form) and exposed auth screens that were only meant for unauthenticated visitors.

Expected behavior: `/login` and `/register` should be reachable **only** when the user is *not* authenticated. After login, the only way back to those pages is to log out first.

## Root Cause
The Angular router had no protection on the auth routes. In [frontend/src/app/app.routes.ts](../frontend/src/app/app.routes.ts):

- `/manage-movies` was protected by `authGuard` + `adminGuard`.
- `/` (home) was protected by `authGuard`.
- `/login` and `/register` had **no guard at all** — so the router happily rendered them regardless of auth state.

The app already had:
- An `AuthService` ([auth.service.ts](../frontend/src/app/core/services/auth.service.ts)) exposing `isLoggedIn()` and `isAdmin()` signals backed by `localStorage`.
- An `authGuard` ([auth.guard.ts](../frontend/src/app/core/guards/auth.guard.ts)) that redirects *unauthenticated* users away from protected pages.

What was missing was the **inverse** guard — one that redirects *authenticated* users away from guest-only pages.

## Resolution
Added a new `guestGuard` and attached it to the `/login` and `/register` route definitions.

### 1. New guard
[frontend/src/app/core/guards/guest.guard.ts](../frontend/src/app/core/guards/guest.guard.ts)

```ts
import { inject } from "@angular/core";
import { CanActivateFn, Router } from "@angular/router";
import { AuthService } from "../services/auth.service";

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isLoggedIn()) {
    return true;
  }
  return router.createUrlTree([auth.isAdmin() ? "/manage-movies" : "/"]);
};
```

- Mirrors the style of the existing `authGuard` (`CanActivateFn`, `inject(AuthService)`, returns a `UrlTree` on rejection).
- Allows access only when the user is logged out.
- For logged-in users, the redirect target matches the post-login navigation logic in [login.ts](../frontend/src/app/features/auth/login/login.ts) and [register.ts](../frontend/src/app/features/auth/register/register.ts) — admins go to `/manage-movies`, regular users to `/`.

### 2. Router wiring
[frontend/src/app/app.routes.ts](../frontend/src/app/app.routes.ts) — added the import and `canActivate: [guestGuard]` to both routes:

```ts
import { guestGuard } from "./core/guards/guest.guard";

export const routes: Routes = [
  {
    path: "login",
    canActivate: [guestGuard],
    loadComponent: () => import("./features/auth/login/login").then((m) => m.LoginComponent)
  },
  {
    path: "register",
    canActivate: [guestGuard],
    loadComponent: () => import("./features/auth/register/register").then((m) => m.RegisterComponent)
  },
  // …unchanged routes…
];
```

No other files were touched. Logout in [navbar.ts](../frontend/src/app/shared/navbar/navbar.ts) already clears `AuthService` state before navigating to `/login`, so the guard correctly releases access at that moment.

## Process Used to Resolve

1. **Plan mode** — entered planning mode so no edits could happen until the approach was agreed.
2. **Explore phase** — launched an `Explore` subagent to map the project (framework, routing, auth state, existing guards, logout flow). Identified Angular 17 with signal-based auth and two existing guards, confirming the inverse-guard pattern would fit cleanly.
3. **Read critical files directly** — verified the contents of [app.routes.ts](../frontend/src/app/app.routes.ts), [auth.guard.ts](../frontend/src/app/core/guards/auth.guard.ts), and [admin.guard.ts](../frontend/src/app/core/guards/admin.guard.ts) before writing the plan so the new guard would match the codebase's style.
4. **Wrote the plan file** describing the fix, files to touch, and verification steps; got user approval via `ExitPlanMode`.
5. **Implemented** — created `guest.guard.ts` and added `canActivate: [guestGuard]` to the two routes.
6. **Tracked progress with a todo list** so each step was visible.

## Verification
1. Start the frontend: `cd frontend && npm start`.
2. **Logged-out**: visit `/login` and `/register` → both render normally.
3. **Logged in as USER**: log in → press browser **Back** → lands on `/`, not the login form. Typing `/login` or `/register` in the URL bar also redirects to `/`.
4. **Logged in as ADMIN**: same test → redirects land on `/manage-movies`.
5. **Logout**: click logout in the navbar → `/login` becomes reachable again, confirming the guard releases access after logout.
6. **No regressions**: `/manage-movies` still requires admin; `/` still requires auth (existing guards untouched).

## Files Touched
- **Added**: [frontend/src/app/core/guards/guest.guard.ts](../frontend/src/app/core/guards/guest.guard.ts)
- **Modified**: [frontend/src/app/app.routes.ts](../frontend/src/app/app.routes.ts)
