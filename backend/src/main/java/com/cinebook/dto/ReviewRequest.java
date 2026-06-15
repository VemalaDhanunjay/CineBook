package com.cinebook.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Body for {@code POST /api/reviews}: the booking to attach the review to and
 * an integer rating in [1, 5]. The movie is derived from the booking on the
 * server so the client can't post mismatched data.
 */
public class ReviewRequest {

    @NotNull
    private Long bookingId;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating;

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
}
