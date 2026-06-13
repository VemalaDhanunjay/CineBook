package com.cinebook.controller;

import com.cinebook.dto.AdminBookingResponse;
import com.cinebook.dto.MostBookedMovieResponse;
import com.cinebook.security.AuthPrincipal;
import com.cinebook.service.BookingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin "All Bookings" API. Theater-scoped — every endpoint reads the owning
 * {@code theaterId} from the authenticated admin's JWT, so an admin only ever
 * sees their own theater's reservations.
 */
@RestController
@RequestMapping("/api/admin/bookings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBookingController {

    private final BookingService bookingService;

    public AdminBookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** Every booking for the calling admin's theater, newest first. */
    @GetMapping
    public ResponseEntity<List<AdminBookingResponse>> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(bookingService.listForTheater(principal.theaterId()));
    }

    /** Top movies by summed seats_booked — drives the "Most Booked" carousel. */
    @GetMapping("/most-booked")
    public ResponseEntity<List<MostBookedMovieResponse>> mostBooked(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(bookingService.mostBookedMovies(principal.theaterId()));
    }
}
