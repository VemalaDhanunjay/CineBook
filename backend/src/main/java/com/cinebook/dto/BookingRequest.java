package com.cinebook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body for {@code POST /api/bookings}: the show to book against and the seat
 * labels the user picked. Per-booking seat cap is enforced here (max 10) to
 * keep the bookings table from accidentally accepting bulk reservations.
 */
public class BookingRequest {

    @NotNull
    private Long showId;

    @NotEmpty
    @Size(max = 10, message = "You can book at most 10 seats per booking")
    private List<@NotBlank String> seatLabels;

    public Long getShowId() { return showId; }
    public void setShowId(Long showId) { this.showId = showId; }

    public List<String> getSeatLabels() { return seatLabels; }
    public void setSeatLabels(List<String> seatLabels) { this.seatLabels = seatLabels; }
}
