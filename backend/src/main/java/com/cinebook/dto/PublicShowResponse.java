package com.cinebook.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Show shape served to user-facing callers — flat, theater-enriched, with the
 * fields the booking page needs to render a "pick a show" card and seat picker.
 */
public class PublicShowResponse {

    private Long id;
    private Long movieId;
    private Long theaterId;
    private String theaterName;
    private String theaterLocation;
    private LocalDateTime showTime;
    private String language;
    private BigDecimal ticketPrice;
    private Integer totalSeats;
    private Integer availableSeats;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMovieId() { return movieId; }
    public void setMovieId(Long movieId) { this.movieId = movieId; }

    public Long getTheaterId() { return theaterId; }
    public void setTheaterId(Long theaterId) { this.theaterId = theaterId; }

    public String getTheaterName() { return theaterName; }
    public void setTheaterName(String theaterName) { this.theaterName = theaterName; }

    public String getTheaterLocation() { return theaterLocation; }
    public void setTheaterLocation(String theaterLocation) { this.theaterLocation = theaterLocation; }

    public LocalDateTime getShowTime() { return showTime; }
    public void setShowTime(LocalDateTime showTime) { this.showTime = showTime; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public BigDecimal getTicketPrice() { return ticketPrice; }
    public void setTicketPrice(BigDecimal ticketPrice) { this.ticketPrice = ticketPrice; }

    public Integer getTotalSeats() { return totalSeats; }
    public void setTotalSeats(Integer totalSeats) { this.totalSeats = totalSeats; }

    public Integer getAvailableSeats() { return availableSeats; }
    public void setAvailableSeats(Integer availableSeats) { this.availableSeats = availableSeats; }
}
