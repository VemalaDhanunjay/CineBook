package com.cinebook.controller;

import com.cinebook.dto.MovieInterestStatusResponse;
import com.cinebook.security.AuthPrincipal;
import com.cinebook.service.MovieInterestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "I'm interested" waitlist API. Lives under /api/movies/{movieId}/interest so
 * the path makes the linkage obvious. Reads return the caller's current status
 * + the running count; writes are idempotent.
 */
@RestController
@RequestMapping("/api/movies/{movieId}/interest")
public class MovieInterestController {

    private final MovieInterestService interestService;

    public MovieInterestController(MovieInterestService interestService) {
        this.interestService = interestService;
    }

    @GetMapping
    public ResponseEntity<MovieInterestStatusResponse> status(
            @PathVariable Long movieId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(interestService.status(principal.userId(), movieId));
    }

    @PostMapping
    public ResponseEntity<MovieInterestStatusResponse> mark(
            @PathVariable Long movieId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(interestService.mark(principal.userId(), movieId));
    }

    @DeleteMapping
    public ResponseEntity<MovieInterestStatusResponse> unmark(
            @PathVariable Long movieId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(interestService.unmark(principal.userId(), movieId));
    }
}
