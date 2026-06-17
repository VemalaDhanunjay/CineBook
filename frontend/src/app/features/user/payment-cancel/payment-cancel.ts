import { Component, OnInit, inject } from "@angular/core";
import { ActivatedRoute, RouterLink } from "@angular/router";
import { LucideX } from "@lucide/angular";
import { PaymentService } from "../../../core/services/payment.service";

/**
 * Stripe Checkout cancel/return page. Best-effort releases the held seats immediately
 * (the reaper / `checkout.session.expired` webhook are the backstops) so the auditorium
 * frees up without waiting for the hold to expire.
 */
@Component({
  selector: "app-payment-cancel",
  standalone: true,
  imports: [RouterLink, LucideX],
  templateUrl: "./payment-cancel.html",
  styleUrl: "./payment-cancel.css"
})
export class PaymentCancelComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly paymentService = inject(PaymentService);

  ngOnInit(): void {
    const sessionId = this.route.snapshot.queryParamMap.get("session_id");
    if (sessionId) {
      this.paymentService.cancelHold(sessionId).subscribe({ error: () => {} });
    }
  }
}
