import { NgTemplateOutlet } from "@angular/common";
import {
  Component,
  ContentChild,
  OnDestroy,
  TemplateRef,
  computed,
  input,
  signal
} from "@angular/core";

/** Minimum shape every carousel slide must provide. Pages pass richer objects. */
export interface CarouselItem {
  /** Drives both the blurred background and the foreground poster. */
  posterUrl: string | null;
  /** Optional — used as the poster's alt text for accessibility. */
  title?: string;
}

/** Context handed to each parent-supplied detail template. */
interface SlideContext {
  $implicit: CarouselItem;
  index: number;
}

/**
 * Shared cross-fade poster carousel (Ken-Burns zoom + auto-advance + dots),
 * extracted from the previously triplicated Manage Movies / Shows / Bookings
 * carousels. It owns the shell, animation, autoplay clock and navigation; each
 * page projects a `<ng-template>` describing the per-slide detail content, so
 * the unique markup (and its styles) stays with the parent.
 */
@Component({
  selector: "app-carousel",
  standalone: true,
  imports: [NgTemplateOutlet],
  templateUrl: "./carousel.html",
  styleUrl: "./carousel.css"
})
export class CarouselComponent implements OnDestroy {
  /** Slides to render. Each must expose at least a `posterUrl`. */
  readonly items = input.required<CarouselItem[]>();

  /** Auto-advance cadence in milliseconds. */
  readonly intervalMs = input(4000);

  /** Parent-supplied per-slide detail template (rendered inside `.carousel-details`). */
  @ContentChild(TemplateRef) detail!: TemplateRef<SlideContext>;

  /** Active slide index. */
  readonly slideIndex = signal(0);
  private timer?: ReturnType<typeof setInterval>;

  constructor() {
    this.startAutoSlide();
  }

  ngOnDestroy(): void {
    this.stopAutoSlide();
  }

  /** Index clamped to the current slide count (handles data changing under us). */
  readonly safeIndex = computed(() => {
    const total = this.items().length;
    return total === 0 ? 0 : this.slideIndex() % total;
  });

  next(): void {
    const total = this.items().length;
    if (total === 0) return;
    this.slideIndex.set((this.safeIndex() + 1) % total);
    this.restartAutoSlide();
  }

  prev(): void {
    const total = this.items().length;
    if (total === 0) return;
    this.slideIndex.set((this.safeIndex() - 1 + total) % total);
    this.restartAutoSlide();
  }

  goToSlide(index: number): void {
    this.slideIndex.set(index);
    this.restartAutoSlide();
  }

  private startAutoSlide(): void {
    this.timer = setInterval(() => {
      const total = this.items().length;
      if (total > 1) {
        this.slideIndex.update((i) => (i + 1) % total);
      }
    }, this.intervalMs());
  }

  private stopAutoSlide(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = undefined;
    }
  }

  private restartAutoSlide(): void {
    this.stopAutoSlide();
    this.startAutoSlide();
  }
}
