package com.cinebook.entity;

public enum BookingStatus {
    /** Seats are held while the user completes Stripe Checkout; not yet paid. */
    PENDING_PAYMENT,
    CONFIRMED,
    PARTIALLY_CANCELLED,
    CANCELLED
}
