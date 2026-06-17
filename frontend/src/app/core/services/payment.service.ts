import { HttpClient } from "@angular/common/http";
import { Injectable, inject } from "@angular/core";
import { Observable } from "rxjs";
import { environment } from "../../../environments/environment";
import { BookingCreatePayload, UserBooking } from "../models/catalog.model";

/** Response of POST /api/payments/checkout — the hosted Stripe page to redirect to. */
export interface CheckoutResponse {
  checkoutUrl: string;
  bookingId: number;
}

/**
 * Stripe Checkout client. The booking is held server-side and the user is redirected
 * to Stripe's hosted page (no card UI or Stripe.js needed here); on return the success
 * page calls {@link confirm} to finalize, and the cancel page calls {@link cancelHold}.
 */
@Injectable({ providedIn: "root" })
export class PaymentService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/payments`;

  /** Reserve seats + open a Checkout Session. Caller redirects to `checkoutUrl`. */
  startCheckout(payload: BookingCreatePayload): Observable<CheckoutResponse> {
    return this.http.post<CheckoutResponse>(`${this.base}/checkout`, payload);
  }

  /** Finalize the booking after returning from Stripe (server re-verifies the payment). */
  confirm(sessionId: string): Observable<UserBooking> {
    return this.http.post<UserBooking>(`${this.base}/confirm`, { sessionId });
  }

  /** Release the held seats when the user backs out of Checkout. */
  cancelHold(sessionId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/cancel-hold`, { sessionId });
  }
}
