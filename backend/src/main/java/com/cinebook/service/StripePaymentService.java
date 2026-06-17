package com.cinebook.service;

import com.cinebook.config.StripeProperties;
import com.cinebook.dto.BookingRequest;
import com.cinebook.dto.CheckoutResponse;
import com.cinebook.dto.UserBookingResponse;
import com.cinebook.entity.Booking;
import com.cinebook.entity.BookingStatus;
import com.cinebook.exception.ApiException;
import com.cinebook.repository.BookingRepository;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Orchestrates the Stripe Checkout flow on top of {@link BookingService}'s seat-hold
 * primitives: reserve seats → create a hosted Checkout Session → finalize on payment
 * (idempotently, from both the return page and the webhook) → release abandoned holds
 * (webhook + scheduled reaper).
 */
@Service
public class StripePaymentService {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentService.class);

    private static final String EVENT_COMPLETED = "checkout.session.completed";
    private static final String EVENT_EXPIRED = "checkout.session.expired";
    private static final String PAID = "paid";

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final StripeGateway gateway;
    private final StripeProperties props;

    public StripePaymentService(BookingService bookingService,
                                BookingRepository bookingRepository,
                                StripeGateway gateway,
                                StripeProperties props) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.gateway = gateway;
        this.props = props;
    }

    /**
     * Hold the requested seats and open a Stripe Checkout Session. If Stripe rejects the
     * session, the surrounding transaction rolls back so the hold is undone automatically.
     */
    @Transactional
    public CheckoutResponse startCheckout(Long userId, BookingRequest request) {
        Booking booking = bookingService.holdSeats(userId, request);
        long amountMinor = BookingService.toMinorUnits(booking.getTotalAmount());
        String productName = "CineBook tickets — " + booking.getSeatsBooked()
                + " seat(s) [" + booking.getSeats() + "]";

        Session session = gateway.createCheckoutSession(amountMinor, productName, booking.getId());
        booking.setStripeSessionId(session.getId());
        bookingRepository.save(booking);
        return new CheckoutResponse(session.getUrl(), booking.getId());
    }

    /**
     * Finalize the booking for a Checkout Session once payment has succeeded — idempotent and
     * shared by the success page (with {@code userId}) and the webhook (with {@code null}).
     * If the hold was already reaped but the customer paid, the charge is auto-refunded.
     */
    @Transactional
    public UserBookingResponse finalizeBySession(Long userId, String sessionId) {
        Booking booking = bookingRepository.findByStripeSessionId(sessionId).orElse(null);
        if (booking == null) {
            Session session = gateway.retrieveSession(sessionId);
            if (PAID.equals(session.getPaymentStatus())
                    && session.getPaymentIntent() != null
                    && session.getAmountTotal() != null) {
                gateway.refund(session.getPaymentIntent(), session.getAmountTotal());
            }
            throw ApiException.badRequest(
                    "This reservation expired before payment was confirmed; any charge has been refunded.");
        }
        if (userId != null && !booking.getUserId().equals(userId)) {
            throw ApiException.forbidden("This booking does not belong to you");
        }
        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
            Session session = gateway.retrieveSession(sessionId);
            if (!PAID.equals(session.getPaymentStatus())) {
                throw ApiException.badRequest("Payment is not complete yet. Please finish checkout.");
            }
            return bookingService.finalizePending(booking.getId(), session.getPaymentIntent());
        }
        // Already finalized (or cancelled) — return the current state idempotently.
        return bookingService.finalizePending(booking.getId(), booking.getStripePaymentIntentId());
    }

    /** Free the held seats for a session if it is still pending (explicit user cancel). */
    @Transactional
    public void cancelHold(Long userId, String sessionId) {
        bookingRepository.findByStripeSessionId(sessionId).ifPresent(booking -> {
            if (userId == null || booking.getUserId().equals(userId)) {
                bookingService.releasePending(booking.getId());
            }
        });
    }

    /** Verify + dispatch a Stripe webhook event. Swallows benign errors so Stripe gets a 200. */
    public void handleWebhook(String payload, String signatureHeader) {
        Event event = gateway.parseEvent(payload, signatureHeader);
        String sessionId = sessionIdOf(event);
        if (sessionId == null) {
            return;
        }
        switch (event.getType()) {
            case EVENT_COMPLETED -> {
                try {
                    finalizeBySession(null, sessionId);
                } catch (ApiException ex) {
                    log.info("Webhook finalize for session {} skipped: {}", sessionId, ex.getMessage());
                }
            }
            case EVENT_EXPIRED -> cancelHold(null, sessionId);
            default -> { /* ignore other event types */ }
        }
    }

    private String sessionIdOf(Event event) {
        StripeObject obj = event.getDataObjectDeserializer().getObject().orElse(null);
        return (obj instanceof Session session) ? session.getId() : null;
    }

    /**
     * Belt-and-suspenders cleanup for holds the user never paid for and never explicitly
     * cancelled — complements the {@code checkout.session.expired} webhook.
     */
    @Scheduled(fixedDelay = 300_000L) // every 5 minutes
    @Transactional
    public void reapAbandonedHolds() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(props.getHoldTtlMinutes());
        List<Booking> stale =
                bookingRepository.findByStatusAndBookingDateBefore(BookingStatus.PENDING_PAYMENT, cutoff);
        for (Booking booking : stale) {
            bookingService.releasePending(booking.getId());
        }
        if (!stale.isEmpty()) {
            log.info("Reaped {} abandoned payment hold(s).", stale.size());
        }
    }
}
