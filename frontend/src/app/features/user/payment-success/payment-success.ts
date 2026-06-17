import { Component, OnInit, inject, signal } from "@angular/core";
import { ActivatedRoute, RouterLink } from "@angular/router";
import { LucideCircleAlert, LucideCircleCheck, LucideTicket } from "@lucide/angular";
import { PaymentService } from "../../../core/services/payment.service";
import { UserBookingService } from "../../../core/services/user-booking.service";

type State = "processing" | "done" | "error";

/**
 * Stripe Checkout return page. Reads the `session_id` from the redirect, asks the
 * backend to finalize the booking (server re-verifies the payment), then refreshes
 * the My Bookings cache. The webhook is the backstop if the user never lands here.
 */
@Component({
  selector: "app-payment-success",
  standalone: true,
  imports: [RouterLink, LucideCircleAlert, LucideCircleCheck, LucideTicket],
  templateUrl: "./payment-success.html",
  styleUrl: "./payment-success.css"
})
export class PaymentSuccessComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly paymentService = inject(PaymentService);
  private readonly bookingService = inject(UserBookingService);

  readonly state = signal<State>("processing");
  readonly message = signal("");

  ngOnInit(): void {
    const sessionId = this.route.snapshot.queryParamMap.get("session_id");
    if (!sessionId) {
      this.state.set("error");
      this.message.set("Missing payment session reference.");
      return;
    }
    this.paymentService.confirm(sessionId).subscribe({
      next: () => {
        this.state.set("done");
        this.bookingService.loadMine().subscribe({ error: () => {} });
      },
      error: (err) => {
        this.state.set("error");
        this.message.set(err?.error?.message ?? "We couldn't confirm your payment.");
      }
    });
  }
}
