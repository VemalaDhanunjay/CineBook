import { Component, computed, inject, input, output } from "@angular/core";
import { DomSanitizer, SafeResourceUrl } from "@angular/platform-browser";
import { LucideX } from "@lucide/angular";

/**
 * Reusable trailer modal. Accepts a raw YouTube URL (watch / youtu.be / embed
 * forms) and renders a sized iframe inside a dialog backdrop. Visibility is
 * driven by the {@code open} input; the parent listens to {@code close} to
 * react to backdrop/close-button clicks and reset its own state.
 */
@Component({
  selector: "app-trailer-modal",
  standalone: true,
  imports: [LucideX],
  templateUrl: "./trailer-modal.html",
  styleUrl: "./trailer-modal.css"
})
export class TrailerModalComponent {
  private readonly sanitizer = inject(DomSanitizer);

  readonly open = input(false);
  readonly trailerUrl = input<string | null>(null);
  readonly title = input("Trailer");

  readonly close = output<void>();

  /** Embed URL bypassed through Angular's sanitizer so the iframe accepts it. */
  readonly embedUrl = computed<SafeResourceUrl | null>(() => {
    const raw = this.trailerUrl();
    if (!raw) return null;
    const id = this.extractYoutubeId(raw);
    if (!id) return null;
    return this.sanitizer.bypassSecurityTrustResourceUrl(
      `https://www.youtube.com/embed/${id}?autoplay=1&rel=0`
    );
  });

  onBackdropClick(): void {
    this.close.emit();
  }

  /** Stop modal-content clicks from bubbling to the backdrop. */
  stop(event: Event): void {
    event.stopPropagation();
  }

  /** Pull a YouTube id out of any of its common URL shapes. */
  private extractYoutubeId(url: string): string | null {
    try {
      const u = new URL(url);
      // youtu.be/<id>
      if (u.hostname.includes("youtu.be")) {
        return u.pathname.replace(/^\//, "").split("/")[0] || null;
      }
      // youtube.com/watch?v=<id>
      const v = u.searchParams.get("v");
      if (v) return v;
      // youtube.com/embed/<id> or youtube.com/shorts/<id>
      const parts = u.pathname.split("/").filter(Boolean);
      const at = parts.findIndex((p) => p === "embed" || p === "shorts");
      if (at >= 0 && parts[at + 1]) return parts[at + 1];
      return null;
    } catch {
      return null;
    }
  }
}
