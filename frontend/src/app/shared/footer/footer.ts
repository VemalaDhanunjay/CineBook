import { Component } from "@angular/core";
import { LucideFilm } from "@lucide/angular";

/**
 * Slim app footer rendered below every page. Picks up the tomato accent + ink
 * surface tokens so it sits seamlessly under the navbar without competing for
 * attention.
 */
@Component({
  selector: "app-footer",
  standalone: true,
  imports: [LucideFilm],
  templateUrl: "./footer.html",
  styleUrl: "./footer.css"
})
export class FooterComponent {
  readonly year = new Date().getFullYear();
}
