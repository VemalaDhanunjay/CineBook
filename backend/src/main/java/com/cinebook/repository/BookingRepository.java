package com.cinebook.repository;

import com.cinebook.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserIdOrderByBookingDateDesc(Long userId);
    List<Booking> findByShowId(Long showId);

    // Every booking that belongs to one of the theater's shows, newest first.
    // Uses an explicit entity join on the plain FK column since Booking/Show have no @ManyToOne mapping.
    @Query("SELECT b FROM Booking b JOIN Show s ON b.showId = s.id " +
           "WHERE s.theaterId = :theaterId AND s.deleted = false " +
           "ORDER BY b.bookingDate DESC")
    List<Booking> findAllByTheaterId(@Param("theaterId") Long theaterId);

    // Aggregation powering the "Most Booked" carousel ribbon: sums seats_booked per movie
    // for the admin's theater, excluding fully-cancelled bookings so the leaderboard
    // reflects real footfall. Returned ordered so the top movie is index 0.
    @Query("SELECT s.movieId AS movieId, " +
           "COALESCE(SUM(b.seatsBooked), 0) AS totalSeats, " +
           "COUNT(b.id) AS totalBookings " +
           "FROM Booking b JOIN Show s ON b.showId = s.id " +
           "WHERE s.theaterId = :theaterId " +
           "AND b.status <> com.cinebook.entity.BookingStatus.CANCELLED " +
           "AND s.deleted = false " +
           "GROUP BY s.movieId ORDER BY totalSeats DESC")
    List<MovieSeatAggregate> sumSeatsBookedByMovie(@Param("theaterId") Long theaterId);
}
