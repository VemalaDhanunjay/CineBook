package com.cinebook.controller;

import com.cinebook.dto.CancelSeatsRequest;
import com.cinebook.dto.RefundQuoteResponse;
import com.cinebook.dto.SeatAvailabilityResponse;
import com.cinebook.dto.UserBookingResponse;
import com.cinebook.security.AuthPrincipal;
import com.cinebook.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * User-facing booking API. Every endpoint is scoped to the caller's userId
 * (read from the JWT principal — never from the request body), and ownership
 * is re-checked in {@link BookingService} for read/cancel operations.
 *
 * <p>Sister to {@link AdminBookingController}, which is theater-scoped for admins.
 */
@RestController
@RequestMapping("/api/bookings")
@PreAuthorize("hasRole('USER')")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** All bookings owned by the caller, newest first. */
    @GetMapping("/me")
    public ResponseEntity<List<UserBookingResponse>> listMine(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(bookingService.listForUser(principal.userId()));
    }

    @GetMapping("/me/{id}")
    public ResponseEntity<UserBookingResponse> getMine(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(bookingService.getUserBooking(principal.userId(), id));
    }

    // Bookings are now created via the Stripe payment flow (POST /api/payments/checkout);
    // the previous unauthenticated "instant book" endpoint has been retired.

    /** Cancel the entire booking (all remaining BOOKED seats). */
    @DeleteMapping("/me/{id}")
    public ResponseEntity<UserBookingResponse> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(bookingService.cancelBooking(principal.userId(), id));
    }

    /** Cancel a subset of seats — leaves the rest BOOKED if any remain. */
    @DeleteMapping("/me/{id}/seats")
    public ResponseEntity<UserBookingResponse> cancelSeats(
            @PathVariable Long id,
            @Valid @RequestBody CancelSeatsRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(
                bookingService.cancelSeats(principal.userId(), id, request.getSeatLabels()));
    }

    /** Refund preview for the cancel modal — refund % and per-seat amount, by time-to-show. */
    @GetMapping("/me/{id}/refund-quote")
    public ResponseEntity<RefundQuoteResponse> refundQuote(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(bookingService.quoteRefund(principal.userId(), id));
    }

    /** Seat picker snapshot — total seats + already-BOOKED labels for a show. */
    @GetMapping("/shows/{showId}/seats")
    public ResponseEntity<SeatAvailabilityResponse> seats(@PathVariable Long showId) {
        return ResponseEntity.ok(bookingService.getSeatAvailability(showId));
    }
}
