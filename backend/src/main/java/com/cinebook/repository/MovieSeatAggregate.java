package com.cinebook.repository;

/**
 * Spring Data projection for the seats-booked-by-movie aggregation query.
 * One row per movie with the summed seat count and booking count over
 * non-cancelled bookings.
 */
public interface MovieSeatAggregate {
    Long getMovieId();
    Long getTotalSeats();
    Long getTotalBookings();
}
