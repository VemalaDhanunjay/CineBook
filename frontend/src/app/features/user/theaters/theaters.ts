import { Component, OnInit, computed, inject, signal } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { RouterLink } from "@angular/router";
import { LucideChevronRight, LucideMapPin, LucideSearch, LucideStore } from "@lucide/angular";
import { Theater } from "../../../core/models/catalog.model";
import { TheaterService } from "../../../core/services/theater.service";

/**
 * User-facing theater directory. Lists every theater registered in the system
 * with a client-side search that narrows by name or location.
 */
@Component({
  selector: "app-theaters",
  standalone: true,
  imports: [FormsModule, RouterLink, LucideChevronRight, LucideMapPin, LucideSearch, LucideStore],
  templateUrl: "./theaters.html",
  styleUrl: "./theaters.css"
})
export class TheatersComponent implements OnInit {
  private readonly theaterService = inject(TheaterService);

  readonly theaters = this.theaterService.theaters;
  readonly search = signal("");

  readonly filtered = computed<Theater[]>(() => {
    const query = this.search().trim().toLowerCase();
    if (!query) return this.theaters();
    return this.theaters().filter(
      (t) =>
        t.name.toLowerCase().includes(query) ||
        (t.location ?? "").toLowerCase().includes(query)
    );
  });

  ngOnInit(): void {
    this.theaterService.load().subscribe({
      error: (err) => console.error("Failed to load theaters", err)
    });
  }
}
