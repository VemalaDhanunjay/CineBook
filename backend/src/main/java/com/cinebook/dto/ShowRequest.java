package com.cinebook.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Create/update payload for a show (a single screening of a movie at a theater).
 * Field constraints mirror the {@code shows} table column limits.
 *
 * <p>Deliberately omitted from the request body:
 * <ul>
 *   <li>{@code theaterId} — derived from the authenticated admin's JWT, never trusted from the client.</li>
 *   <li>{@code availableSeats} — derived by the service ({@code = totalSeats} on create).</li>
 *   <li>{@code deleted} — managed server-side via the soft-delete flow.</li>
 * </ul>
 */
public class ShowRequest {

    /** Movie being screened. Must reference an existing, non-deleted movie. */
    @NotNull
    private Long movieId;

    /** Combined date + time of the screening (sent by the frontend as ISO {@code yyyy-MM-ddTHH:mm}). */
    @NotNull
    private LocalDateTime showTime;

    /** Language the show is screened in (single value, e.g. "Telugu"). */
    @NotBlank
    @Size(max = 40)
    private String language;

    /** Per-ticket price for this show. */
    @NotNull
    @PositiveOrZero
    private BigDecimal ticketPrice;

    /** Total seat capacity allotted to this show. */
    @NotNull
    @Positive
    private Integer totalSeats;

    public Long getMovieId() { return movieId; }
    public void setMovieId(Long movieId) { this.movieId = movieId; }

    public LocalDateTime getShowTime() { return showTime; }
    public void setShowTime(LocalDateTime showTime) { this.showTime = showTime; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public BigDecimal getTicketPrice() { return ticketPrice; }
    public void setTicketPrice(BigDecimal ticketPrice) { this.ticketPrice = ticketPrice; }

    public Integer getTotalSeats() { return totalSeats; }
    public void setTotalSeats(Integer totalSeats) { this.totalSeats = totalSeats; }
}
