import { Component, input } from "@angular/core";

/** How much of the tomato is filled — drives rating display (`half` for fractions). */
export type TomatoFill = "empty" | "half" | "full";

/**
 * A single tomato glyph used for ratings (Rotten-Tomatoes style). Lucide has no
 * tomato icon and the project ships no tomato assets, so this is a small inline
 * SVG tinted with the app's `tomato` palette. Size it from the consumer via
 * Tailwind height/width classes on the host, e.g. `<app-tomato-icon class="h-5 w-5" />`.
 */
@Component({
  selector: "app-tomato-icon",
  standalone: true,
  templateUrl: "./tomato-icon.html",
  styleUrl: "./tomato-icon.css"
})
export class TomatoIconComponent {
  readonly fill = input<TomatoFill>("empty");
}
