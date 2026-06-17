package com.cinebook.controller;

import com.cinebook.dto.BookingRequest;
import com.cinebook.dto.CheckoutResponse;
import com.cinebook.dto.SessionRequest;
import com.cinebook.dto.UserBookingResponse;
import com.cinebook.security.AuthPrincipal;
import com.cinebook.service.StripePaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Stripe Checkout payment API. All endpoints are user-scoped (userId from the JWT)
 * except {@code /webhook}, which is public and authenticated by Stripe signature
 * verification (see {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final StripePaymentService paymentService;

    public PaymentController(StripePaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** Reserve seats and open a Stripe Checkout Session; returns the URL to redirect to. */
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(
            @Valid @RequestBody BookingRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(paymentService.startCheckout(principal.userId(), request));
    }

    /** Finalize a booking after the user returns from Stripe (server verifies the payment). */
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/confirm")
    public ResponseEntity<UserBookingResponse> confirm(
            @Valid @RequestBody SessionRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(
                paymentService.finalizeBySession(principal.userId(), request.getSessionId()));
    }

    /** Release the held seats when the user backs out of Checkout. */
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/cancel-hold")
    public ResponseEntity<Void> cancelHold(
            @Valid @RequestBody SessionRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        paymentService.cancelHold(principal.userId(), request.getSessionId());
        return ResponseEntity.noContent().build();
    }

    /** Stripe webhook — signature-verified, not JWT-authed. Always 200 once parsed. */
    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature) {
        paymentService.handleWebhook(payload, signature);
        return ResponseEntity.ok("ok");
    }
}
