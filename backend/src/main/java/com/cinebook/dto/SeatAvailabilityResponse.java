package com.cinebook.dto;

import java.util.List;

/**
 * Snapshot of seat availability for a show, used by the booking page to render
 * the seat picker grid: every label in {@code booked} is rendered as taken.
 */
public class SeatAvailabilityResponse {

    private Long showId;
    private Integer totalSeats;
    private List<String> booked;

    public SeatAvailabilityResponse() {
    }

    public SeatAvailabilityResponse(Long showId, Integer totalSeats, List<String> booked) {
        this.showId = showId;
        this.totalSeats = totalSeats;
        this.booked = booked;
    }

    public Long getShowId() { return showId; }
    public void setShowId(Long showId) { this.showId = showId; }

    public Integer getTotalSeats() { return totalSeats; }
    public void setTotalSeats(Integer totalSeats) { this.totalSeats = totalSeats; }

    public List<String> getBooked() { return booked; }
    public void setBooked(List<String> booked) { this.booked = booked; }
}
