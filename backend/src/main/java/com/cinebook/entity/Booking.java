package com.cinebook.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "show_id", nullable = false)
    private Long showId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // Immutable snapshot of the originally booked seats, CSV e.g. "A1,A2,B5".
    // Current per-seat state lives in BookingSeat rows (authoritative for cancellations).
    @Column(length = 500)
    private String seats;

    @Column(name = "seats_booked")
    private Integer seatsBooked;

    @Column(precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_amount", precision = 10, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "refund_amount", precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "booking_date")
    private LocalDateTime bookingDate;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    // ── Stripe payment linkage ───────────────────────────────────────────────
    /** Stripe Checkout Session id — set when the hold is created, used to finalize. */
    @Column(name = "stripe_session_id", length = 255)
    private String stripeSessionId;

    /** Stripe PaymentIntent id of the successful charge — used to issue refunds. */
    @Column(name = "stripe_payment_intent_id", length = 255)
    private String stripePaymentIntentId;

    /** When the payment succeeded and the booking was finalized to CONFIRMED. */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getShowId() { return showId; }
    public void setShowId(Long showId) { this.showId = showId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getSeats() { return seats; }
    public void setSeats(String seats) { this.seats = seats; }

    public Integer getSeatsBooked() { return seatsBooked; }
    public void setSeatsBooked(Integer seatsBooked) { this.seatsBooked = seatsBooked; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }

    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }

    public LocalDateTime getBookingDate() { return bookingDate; }
    public void setBookingDate(LocalDateTime bookingDate) { this.bookingDate = bookingDate; }

    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

    public String getStripeSessionId() { return stripeSessionId; }
    public void setStripeSessionId(String stripeSessionId) { this.stripeSessionId = stripeSessionId; }

    public String getStripePaymentIntentId() { return stripePaymentIntentId; }
    public void setStripePaymentIntentId(String stripePaymentIntentId) { this.stripePaymentIntentId = stripePaymentIntentId; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
}
