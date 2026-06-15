package com.cinebook.dto;

import java.math.BigDecimal;

/**
 * Pre-cancel refund preview served to the My Bookings cancel modal. The frontend
 * multiplies {@code refundPerSeat} by the number of seats the user has selected to
 * show a live estimate; the authoritative refund is recomputed server-side on the
 * actual cancel. Refund tiers (relative to show time):
 * <ul>
 *   <li>&ge; 24h &rarr; 100%</li>
 *   <li>12&ndash;24h &rarr; 80%</li>
 *   <li>2&ndash;12h &rarr; 50%</li>
 *   <li>&lt; 2h &rarr; 0% (still cancellable, non-refundable)</li>
 * </ul>
 */
public class RefundQuoteResponse {

    /** Refund percentage that applies right now (0, 50, 80 or 100). */
    private int refundPercent;

    /** Refund for a single seat (price + 18% tax, scaled by {@link #refundPercent}). */
    private BigDecimal refundPerSeat;

    /** Whole hours until the show starts (negative/zero once it has begun). */
    private long hoursUntilShow;

    /** Human-readable policy line for the current tier. */
    private String message;

    public RefundQuoteResponse() {
    }

    public RefundQuoteResponse(int refundPercent, BigDecimal refundPerSeat,
                               long hoursUntilShow, String message) {
        this.refundPercent = refundPercent;
        this.refundPerSeat = refundPerSeat;
        this.hoursUntilShow = hoursUntilShow;
        this.message = message;
    }

    public int getRefundPercent() { return refundPercent; }
    public void setRefundPercent(int refundPercent) { this.refundPercent = refundPercent; }

    public BigDecimal getRefundPerSeat() { return refundPerSeat; }
    public void setRefundPerSeat(BigDecimal refundPerSeat) { this.refundPerSeat = refundPerSeat; }

    public long getHoursUntilShow() { return hoursUntilShow; }
    public void setHoursUntilShow(long hoursUntilShow) { this.hoursUntilShow = hoursUntilShow; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
