import { Component, inject } from "@angular/core";
import { ActivatedRoute } from "@angular/router";
import { LucideClock } from "@lucide/angular";
import { toSignal } from "@angular/core/rxjs-interop";
import { map } from "rxjs";

/**
 * Generic placeholder used by user routes that don't have a real page yet
 * (Theaters, My Bookings, the per-movie booking flow). The route reads a
 * `title` from `data` and a `subtitle` so different paths still feel distinct
 * while sharing one component.
 */
@Component({
  selector: "app-coming-soon",
  standalone: true,
  imports: [LucideClock],
  templateUrl: "./coming-soon.html",
  styleUrl: "./coming-soon.css"
})
export class ComingSoonComponent {
  private readonly route = inject(ActivatedRoute);

  readonly title = toSignal(
    this.route.data.pipe(map((d) => (d["title"] as string) ?? "Coming soon")),
    { initialValue: "Coming soon" }
  );

  readonly subtitle = toSignal(
    this.route.data.pipe(
      map((d) => (d["subtitle"] as string) ?? "We're building this page next.")
    ),
    { initialValue: "We're building this page next." }
  );
}
