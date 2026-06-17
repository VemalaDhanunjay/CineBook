package com.cinebook.dto;

/**
 * Returned by {@code POST /api/payments/checkout}. The SPA redirects the browser to
 * {@code checkoutUrl} (Stripe's hosted page); {@code bookingId} is the held
 * PENDING_PAYMENT booking that will be finalized once payment succeeds.
 */
public class CheckoutResponse {

    private String checkoutUrl;
    private Long bookingId;

    public CheckoutResponse() {
    }

    public CheckoutResponse(String checkoutUrl, Long bookingId) {
        this.checkoutUrl = checkoutUrl;
        this.bookingId = bookingId;
    }

    public String getCheckoutUrl() { return checkoutUrl; }
    public void setCheckoutUrl(String checkoutUrl) { this.checkoutUrl = checkoutUrl; }

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
}
