package com.cinebook.controller;

import com.cinebook.dto.MovieRatingResponse;
import com.cinebook.dto.ReviewRequest;
import com.cinebook.dto.ReviewResponse;
import com.cinebook.security.AuthPrincipal;
import com.cinebook.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * User reviews API. Writes require the caller to own the linked booking; reads
 * are open to any authenticated user (USER or ADMIN).
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> create(
            @Valid @RequestBody ReviewRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        ReviewResponse created = reviewService.createReview(principal.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/movies/{movieId}")
    public ResponseEntity<List<ReviewResponse>> listForMovie(@PathVariable Long movieId) {
        return ResponseEntity.ok(reviewService.listForMovie(movieId));
    }

    @GetMapping("/movies/{movieId}/rating")
    public ResponseEntity<MovieRatingResponse> ratingForMovie(@PathVariable Long movieId) {
        return ResponseEntity.ok(reviewService.ratingForMovie(movieId));
    }
}
