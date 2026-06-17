import { inject } from "@angular/core";
import { CanActivateFn, Router } from "@angular/router";
import { AuthService } from "../services/auth.service";

/**
 * Restricts customer-facing routes to signed-in USERS. Admins are theater operators
 * and have no customer flow, so they're redirected to their dashboard; guests go to login.
 * Mirror image of {@link adminGuard}.
 */
export const userGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isLoggedIn() && !auth.isAdmin()) {
    return true;
  }
  return router.createUrlTree([auth.isAdmin() ? "/manage-movies" : "/login"]);
};
