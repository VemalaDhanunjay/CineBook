import { Routes } from "@angular/router";
import { adminGuard } from "./core/guards/admin.guard";
import { authGuard } from "./core/guards/auth.guard";
import { guestGuard } from "./core/guards/guest.guard";
import { userGuard } from "./core/guards/user.guard";

export const routes: Routes = [
  {
    path: "login",
    canActivate: [guestGuard],
    loadComponent: () =>
      import("./features/auth/login/login").then((m) => m.LoginComponent)
  },
  {
    path: "register",
    canActivate: [guestGuard],
    loadComponent: () =>
      import("./features/auth/register/register").then((m) => m.RegisterComponent)
  },
  {
    path: "manage-movies",
    canActivate: [authGuard, adminGuard],
    loadComponent: () =>
      import("./features/admin/manage-movies/manage-movies").then((m) => m.ManageMoviesComponent)
  },
  {
    path: "manage-shows",
    canActivate: [authGuard, adminGuard],
    loadComponent: () =>
      import("./features/admin/manage-shows/manage-shows").then((m) => m.ManageShowsComponent)
  },
  {
    path: "manage-bookings",
    canActivate: [authGuard, adminGuard],
    loadComponent: () =>
      import("./features/admin/manage-bookings/manage-bookings").then(
        (m) => m.ManageBookingsComponent
      )
  },
  {
    path: "analytics",
    canActivate: [authGuard, adminGuard],
    loadComponent: () =>
      import("./features/admin/analytics/analytics").then((m) => m.AnalyticsComponent)
  },
  {
    path: "movies",
    canActivate: [authGuard, userGuard],
    loadComponent: () =>
      import("./features/user/movies/movies").then((m) => m.MoviesComponent)
  },
  {
    path: "movies/:id/book",
    canActivate: [authGuard, userGuard],
    loadComponent: () =>
      import("./features/user/booking/booking").then((m) => m.BookingComponent)
  },
  {
    path: "theaters",
    canActivate: [authGuard, userGuard],
    loadComponent: () =>
      import("./features/user/theaters/theaters").then((m) => m.TheatersComponent)
  },
  {
    path: "theaters/:id",
    canActivate: [authGuard, userGuard],
    loadComponent: () =>
      import("./features/user/theater-detail/theater-detail").then(
        (m) => m.TheaterDetailComponent
      )
  },
  {
    path: "my-bookings",
    canActivate: [authGuard, userGuard],
    loadComponent: () =>
      import("./features/user/my-bookings/my-bookings").then((m) => m.MyBookingsComponent)
  },
  {
    path: "payment/success",
    canActivate: [authGuard, userGuard],
    loadComponent: () =>
      import("./features/user/payment-success/payment-success").then(
        (m) => m.PaymentSuccessComponent
      )
  },
  {
    path: "payment/cancel",
    canActivate: [authGuard, userGuard],
    loadComponent: () =>
      import("./features/user/payment-cancel/payment-cancel").then(
        (m) => m.PaymentCancelComponent
      )
  },
  {
    path: "",
    pathMatch: "full",
    redirectTo: "movies"
  },
  { path: "**", redirectTo: "" }
];
