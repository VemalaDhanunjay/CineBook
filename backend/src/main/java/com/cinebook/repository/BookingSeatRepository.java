package com.cinebook.repository;

import com.cinebook.entity.BookingSeat;
import com.cinebook.entity.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

    List<BookingSeat> findByBookingId(Long bookingId);

    List<BookingSeat> findByBookingIdAndStatus(Long bookingId, SeatStatus status);

    // Supports future show-level seat-availability checks across a set of bookings.
    List<BookingSeat> findByBookingIdInAndStatus(List<Long> bookingIds, SeatStatus status);

    /**
     * All seats with the given status booked on a given show, across every
     * booking. Powers the seat picker (BOOKED seats render as unavailable) and
     * the create-booking conflict check.
     */
    @Query("SELECT bs FROM BookingSeat bs, Booking b " +
           "WHERE bs.bookingId = b.id AND b.showId = :showId AND bs.status = :status")
    List<BookingSeat> findByShowIdAndStatus(@Param("showId") Long showId,
                                            @Param("status") SeatStatus status);
}
