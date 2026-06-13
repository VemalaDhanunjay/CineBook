import { Component, computed, inject } from "@angular/core";
import { RouterLink, RouterLinkActive } from "@angular/router";
import { LucideCalendarClock, LucideClapperboard, LucideTicket } from "@lucide/angular";
import { Role } from "../../core/models/user.model";
import { AuthService } from "../../core/services/auth.service";

/** A single sidebar navigation entry. Add a new object to NAV_ITEMS to extend the menu. */
interface NavItem {
  id: string;
  label: string;
  route: string;
  icon: "clapperboard" | "calendar-clock" | "ticket";
  roles: Role[];
}

const NAV_ITEMS: NavItem[] = [
  {
    id: "sidebar-manage",
    label: "Manage Movies",
    route: "/manage-movies",
    icon: "clapperboard",
    roles: ["ADMIN"]
  },
  {
    id: "sidebar-shows",
    label: "Manage Shows",
    route: "/manage-shows",
    icon: "calendar-clock",
    roles: ["ADMIN"]
  },
  {
    id: "sidebar-bookings",
    label: "All Bookings",
    route: "/manage-bookings",
    icon: "ticket",
    roles: ["ADMIN"]
  }
];

/**
 * Persistent left navigation, shared across the app. Items are data-driven and
 * filtered by the signed-in user's role, so future destinations are a one-line
 * addition to NAV_ITEMS. Hides itself entirely when there is nothing to show
 * (e.g. logged out), letting the layout collapse to full width.
 */
@Component({
  selector: "app-sidebar",
  standalone: true,
  imports: [RouterLink, RouterLinkActive, LucideClapperboard, LucideCalendarClock, LucideTicket],
  templateUrl: "./sidebar.html",
  styleUrl: "./sidebar.css",
  host: { "[class.hidden]": "!items().length" }
})
export class SidebarComponent {
  private readonly auth = inject(AuthService);

  /** Nav items the current user is allowed to see. */
  readonly items = computed(() => {
    const role = this.auth.currentUser()?.role;
    return role ? NAV_ITEMS.filter((item) => item.roles.includes(role)) : [];
  });
}
