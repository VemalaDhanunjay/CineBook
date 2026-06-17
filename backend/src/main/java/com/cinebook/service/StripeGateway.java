package com.cinebook.service;

import com.cinebook.config.StripeProperties;
import com.cinebook.exception.ApiException;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper around the Stripe Java SDK. Holds no booking knowledge — it only
 * translates between our intent and Stripe API calls, mapping {@link StripeException}
 * to {@link ApiException} so callers can stay Stripe-agnostic. Depends solely on
 * {@link StripeProperties}, which keeps it free of any dependency cycle with the
 * booking services.
 */
@Component
public class StripeGateway {

    private final StripeProperties props;

    public StripeGateway(StripeProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        if (props.isConfigured()) {
            Stripe.apiKey = props.getSecretKey();
        }
    }

    private void requireConfigured() {
        if (!props.isConfigured()) {
            throw ApiException.badRequest(
                    "Stripe is not configured. Set STRIPE_SECRET_KEY (see STRIPE_SETUP.md).");
        }
        Stripe.apiKey = props.getSecretKey();
    }

    /** Create a hosted Checkout Session for a single line item; returns the Stripe Session. */
    public Session createCheckoutSession(long amountMinor, String productName, Long bookingId) {
        requireConfigured();
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(props.getSuccessUrl() + "?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(props.getCancelUrl() + "?session_id={CHECKOUT_SESSION_ID}")
                .setClientReferenceId(String.valueOf(bookingId))
                .putMetadata("bookingId", String.valueOf(bookingId))
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(props.getCurrency())
                                .setUnitAmount(amountMinor)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(productName)
                                        .build())
                                .build())
                        .build())
                .build();
        try {
            return Session.create(params);
        } catch (StripeException e) {
            throw ApiException.badRequest("Could not start payment: " + e.getMessage());
        }
    }

    public Session retrieveSession(String sessionId) {
        requireConfigured();
        try {
            return Session.retrieve(sessionId);
        } catch (StripeException e) {
            throw ApiException.badRequest("Could not retrieve payment session: " + e.getMessage());
        }
    }

    /** Issue a (partial or full) refund against a PaymentIntent, amount in the minor unit (paise). */
    public void refund(String paymentIntentId, long amountMinor) {
        requireConfigured();
        try {
            Refund.create(RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .setAmount(amountMinor)
                    .build());
        } catch (StripeException e) {
            throw ApiException.badRequest("Refund failed: " + e.getMessage());
        }
    }

    /** Verify a webhook signature and parse the event. */
    public Event parseEvent(String payload, String signatureHeader) {
        if (props.getWebhookSecret() == null || props.getWebhookSecret().isBlank()) {
            throw ApiException.badRequest("Stripe webhook secret is not configured.");
        }
        try {
            return Webhook.constructEvent(payload, signatureHeader, props.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            throw ApiException.badRequest("Invalid Stripe webhook signature.");
        }
    }
}
