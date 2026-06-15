package com.cinebook.service;

import com.cinebook.dto.MovieRatingResponse;
import com.cinebook.dto.ReviewRequest;
import com.cinebook.dto.ReviewResponse;
import com.cinebook.entity.Booking;
import com.cinebook.entity.BookingStatus;
import com.cinebook.entity.Movie;
import com.cinebook.entity.Review;
import com.cinebook.entity.Show;
import com.cinebook.entity.User;
import com.cinebook.exception.ApiException;
import com.cinebook.repository.BookingRepository;
import com.cinebook.repository.MovieRatingAggregate;
import com.cinebook.repository.MovieRepository;
import com.cinebook.repository.ReviewRepository;
import com.cinebook.repository.ShowRepository;
import com.cinebook.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * User-facing review CRUD. Reviews are pinned to a {@link Booking} — a user can
 * only post a review for a movie they actually booked, and at most one review
 * per booking. The booking's status must indicate the user got to watch
 * (CONFIRMED or PARTIALLY_CANCELLED). Movie + show ids are derived from the
 * booking so the client cannot post mismatched data.
 */
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final ShowRepository showRepository;
    private final UserRepository userRepository;
    private final MovieRepository movieRepository;

    public ReviewService(ReviewRepository reviewRepository,
                         BookingRepository bookingRepository,
                         ShowRepository showRepository,
                         UserRepository userRepository,
                         MovieRepository movieRepository) {
        this.reviewRepository = reviewRepository;
        this.bookingRepository = bookingRepository;
        this.showRepository = showRepository;
        this.userRepository = userRepository;
        this.movieRepository = movieRepository;
    }

    @Transactional
    public ReviewResponse createReview(Long userId, ReviewRequest request) {
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> ApiException.notFound("Booking not found"));
        if (!booking.getUserId().equals(userId)) {
            throw ApiException.forbidden("You can only review your own bookings");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw ApiException.badRequest("Cannot review a fully cancelled booking");
        }
        if (reviewRepository.existsByBookingId(booking.getId())) {
            throw ApiException.conflict("You've already rated this booking");
        }

        Show show = showRepository.findById(booking.getShowId())
                .orElseThrow(() -> ApiException.notFound("Show not found"));

        // Gate ratings to *after* the show has played — no rating a film you
        // haven't watched yet. Mirrors the FE's canRate guard.
        if (show.getShowTime() == null || show.getShowTime().isAfter(LocalDateTime.now())) {
            throw ApiException.badRequest("You can only rate a movie after the show has played");
        }

        Review review = new Review();
        review.setBookingId(booking.getId());
        review.setMovieId(show.getMovieId());
        review.setShowId(show.getId());
        review.setUserId(userId);
        review.setRating(request.getRating());
        review.setCreatedAt(LocalDateTime.now());
        Review saved = reviewRepository.save(review);

        User user = userRepository.findById(userId).orElse(null);
        return toResponse(saved, user);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> listForMovie(Long movieId) {
        List<Review> reviews = reviewRepository.findByMovieIdOrderByCreatedAtDesc(movieId);
        if (reviews.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = reviews.stream().map(Review::getUserId).distinct().toList();
        Map<Long, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return reviews.stream()
                .map(r -> toResponse(r, usersById.get(r.getUserId())))
                .toList();
    }

    /** Average rating + count for a single movie — drives the card chip on the movies page. */
    @Transactional(readOnly = true)
    public MovieRatingResponse ratingForMovie(Long movieId) {
        Movie movie = movieRepository.findById(movieId)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> ApiException.notFound("Movie not found"));
        List<MovieRatingAggregate> aggregates = reviewRepository.averageRatingByMovie(List.of(movieId));
        double avg = aggregates.isEmpty() || aggregates.get(0).getAverageRating() == null
                ? 0.0
                : aggregates.get(0).getAverageRating();
        long count = aggregates.isEmpty() || aggregates.get(0).getReviewCount() == null
                ? 0L
                : aggregates.get(0).getReviewCount();
        return new MovieRatingResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getPosterUrl(),
                avg,
                count
        );
    }

    private ReviewResponse toResponse(Review review, User user) {
        ReviewResponse dto = new ReviewResponse();
        dto.setId(review.getId());
        dto.setMovieId(review.getMovieId());
        dto.setUserId(review.getUserId());
        dto.setUsername(user == null ? "User #" + review.getUserId() : user.getUsername());
        dto.setRating(review.getRating());
        dto.setCreatedAt(review.getCreatedAt());
        return dto;
    }
}
