package com.cinebook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Body for {@code DELETE /api/bookings/me/{id}/seats}: the subset of seat
 * labels (from the original booking) that the user wants to cancel.
 */
public class CancelSeatsRequest {

    @NotEmpty
    private List<@NotBlank String> seatLabels;

    public List<String> getSeatLabels() { return seatLabels; }
    public void setSeatLabels(List<String> seatLabels) { this.seatLabels = seatLabels; }
}
