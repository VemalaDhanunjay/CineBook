package com.cinebook.controller;

import com.cinebook.dto.MovieInterestResponse;
import com.cinebook.dto.MovieRatingResponse;
import com.cinebook.security.AuthPrincipal;
import com.cinebook.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin analytics API. Theater-scoped — every endpoint reads the owning
 * {@code theaterId} from the authenticated admin's JWT, so an admin only ever
 * sees metrics for movies their own theater screens. Booking, show and movie
 * raw data are served by existing endpoints; this controller supplies the two
 * aggregates the dashboard cannot derive client-side (ratings and waitlist).
 */
@RestController
@RequestMapping("/api/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /** Average rating + review count per movie — drives the "Top Rated" chart. */
    @GetMapping("/movie-ratings")
    public ResponseEntity<List<MovieRatingResponse>> movieRatings(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(analyticsService.movieRatings(principal.theaterId()));
    }

    /** Waitlist count per movie — drives the "Audience Interest" footer cards. */
    @GetMapping("/movie-interest")
    public ResponseEntity<List<MovieInterestResponse>> movieInterest(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(analyticsService.movieInterest(principal.theaterId()));
    }
}
