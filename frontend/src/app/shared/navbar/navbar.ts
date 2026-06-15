import { Component, HostListener, computed, effect, inject, signal } from "@angular/core";
import { toSignal } from "@angular/core/rxjs-interop";
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from "@angular/router";
import {
  LucideCheck,
  LucideChevronDown,
  LucideFilm,
  LucideLogOut,
  LucideMapPin,
  LucideStore,
  LucideTicket,
  LucideUser
} from "@lucide/angular";
import { filter, map } from "rxjs";
import { AuthService } from "../../core/services/auth.service";
import { LocationService } from "../../core/services/location.service";
import { TheaterService } from "../../core/services/theater.service";

@Component({
  selector: "app-navbar",
  standalone: true,
  imports: [
    RouterLink,
    RouterLinkActive,
    LucideCheck,
    LucideChevronDown,
    LucideFilm,
    LucideLogOut,
    LucideMapPin,
    LucideStore,
    LucideTicket,
    LucideUser
  ],
  templateUrl: "./navbar.html",
  styleUrl: "./navbar.css"
})
export class NavbarComponent {
  readonly auth = inject(AuthService);
  readonly location = inject(LocationService);
  readonly theaterService = inject(TheaterService);
  private readonly router = inject(Router);

  constructor() {
    // Lazily seed the city picker once the user is logged in. Effect re-runs
    // on auth changes so login → load is automatic without a page reload.
    effect(() => {
      if (this.auth.isLoggedIn() && !this.theaterService.loaded()) {
        this.theaterService.load().subscribe({
          error: (err) => console.error("Failed to load theaters for navbar", err)
        });
      }
    });
  }

  /** Current URL, kept reactive so the auth-switch thumb can slide on navigation. */
  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map(() => this.router.url)
    ),
    { initialValue: this.router.url }
  );

  readonly onRegister = computed(() => this.currentUrl().startsWith("/register"));

  /** Login/register share a pre-auth context — hide theater chrome (e.g. location) there. */
  readonly onAuthPage = computed(
    () => this.currentUrl().startsWith("/login") || this.currentUrl().startsWith("/register")
  );

  /** Only USER role gets the navbar tab strip; admins navigate via the sidebar. */
  readonly showUserTabs = computed(
    () => !this.onAuthPage() && this.auth.currentUser()?.role === "USER"
  );

  /** City picker is for USERs only — admins don't browse, they own one theater. */
  readonly showLocationPicker = computed(
    () => !this.onAuthPage() && this.auth.currentUser()?.role === "USER"
  );

  /**
   * The admin's own theater, looked up from {@link TheaterService.theaters} via
   * the {@code theaterId} on the JWT principal. Returns null for users / when
   * the directory hasn't loaded yet / when the admin's theater isn't found.
   */
  readonly adminTheater = computed(() => {
    if (this.onAuthPage()) return null;
    const user = this.auth.currentUser();
    if (user?.role !== "ADMIN" || user.theaterId == null) return null;
    return this.theaterService.theaters().find((t) => t.id === user.theaterId) ?? null;
  });

  /** Whether the city dropdown menu is currently open. */
  readonly locationOpen = signal(false);

  toggleLocation(): void {
    this.locationOpen.update((open) => !open);
  }

  closeLocation(): void {
    this.locationOpen.set(false);
  }

  pickLocation(city: string | null, event?: Event): void {
    event?.stopPropagation();
    this.location.setLocation(city);
    this.closeLocation();
  }

  /** First two characters of the username, uppercased — drives the avatar chip. */
  initialsOf(username: string | null | undefined): string {
    if (!username) return "?";
    const trimmed = username.trim();
    if (!trimmed) return "?";
    const parts = trimmed.split(/\s+/);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return trimmed.slice(0, 2).toUpperCase();
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(["/login"]);
  }

  /** Close the city dropdown when the user clicks anywhere outside its wrapper. */
  @HostListener("document:click", ["$event"])
  onDocumentClick(event: Event): void {
    if (!this.locationOpen()) return;
    const target = event.target as HTMLElement | null;
    if (!target?.closest("#nav-location-wrap")) {
      this.closeLocation();
    }
  }

  /** Escape always dismisses an open dropdown — keyboard parity with click-out. */
  @HostListener("document:keydown.escape")
  onEscape(): void {
    if (this.locationOpen()) this.closeLocation();
  }
}
