package com.cinebook.dto;

import com.cinebook.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Flat, pre-joined row served to the user's My Bookings page. Mirrors
 * {@link AdminBookingResponse} minus the admin-scoped chrome (no username — the
 * caller already knows who they are) and with a {@code hasReview} flag so the
 * UI can hide the "Rate this movie" button when a review already exists.
 */
public class UserBookingResponse {

    private Long id;

    private Long movieId;
    private String movieTitle;
    private String moviePosterUrl;

    private Long showId;
    private LocalDateTime showTime;
    private String theaterName;
    private String theaterLocation;

    private String seats;
    private Integer seatsBooked;

    /** Seat labels still BOOKED — the cancellable set the modal lets you pick from. */
    private List<String> activeSeats;
    /** Seat labels already CANCELLED — rendered disabled/struck-through in the modal. */
    private List<String> cancelledSeats;

    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal refundAmount;

    private BookingStatus status;
    private LocalDateTime bookingDate;
    private LocalDateTime cancelledAt;

    private boolean hasReview;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMovieId() { return movieId; }
    public void setMovieId(Long movieId) { this.movieId = movieId; }

    public String getMovieTitle() { return movieTitle; }
    public void setMovieTitle(String movieTitle) { this.movieTitle = movieTitle; }

    public String getMoviePosterUrl() { return moviePosterUrl; }
    public void setMoviePosterUrl(String moviePosterUrl) { this.moviePosterUrl = moviePosterUrl; }

    public Long getShowId() { return showId; }
    public void setShowId(Long showId) { this.showId = showId; }

    public LocalDateTime getShowTime() { return showTime; }
    public void setShowTime(LocalDateTime showTime) { this.showTime = showTime; }

    public String getTheaterName() { return theaterName; }
    public void setTheaterName(String theaterName) { this.theaterName = theaterName; }

    public String getTheaterLocation() { return theaterLocation; }
    public void setTheaterLocation(String theaterLocation) { this.theaterLocation = theaterLocation; }

    public String getSeats() { return seats; }
    public void setSeats(String seats) { this.seats = seats; }

    public Integer getSeatsBooked() { return seatsBooked; }
    public void setSeatsBooked(Integer seatsBooked) { this.seatsBooked = seatsBooked; }

    public List<String> getActiveSeats() { return activeSeats; }
    public void setActiveSeats(List<String> activeSeats) { this.activeSeats = activeSeats; }

    public List<String> getCancelledSeats() { return cancelledSeats; }
    public void setCancelledSeats(List<String> cancelledSeats) { this.cancelledSeats = cancelledSeats; }

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

    public boolean isHasReview() { return hasReview; }
    public void setHasReview(boolean hasReview) { this.hasReview = hasReview; }
}
