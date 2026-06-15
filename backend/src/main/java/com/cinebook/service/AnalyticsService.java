package com.cinebook.service;

import com.cinebook.dto.MovieInterestResponse;
import com.cinebook.dto.MovieRatingResponse;
import com.cinebook.entity.Movie;
import com.cinebook.entity.Show;
import com.cinebook.exception.ApiException;
import com.cinebook.repository.MovieInterestAggregate;
import com.cinebook.repository.MovieInterestRepository;
import com.cinebook.repository.MovieRatingAggregate;
import com.cinebook.repository.MovieRepository;
import com.cinebook.repository.ReviewRepository;
import com.cinebook.repository.ShowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only analytics for the admin dashboard. Theater-scoped — every figure is
 * derived only from movies the calling admin's theater actually screens, resolved
 * from that theater's shows. Mirrors {@link BookingService}: constructor injection,
 * a {@code requireTheater} guard, and batched lookups to avoid N+1 queries.
 */
@Service
public class AnalyticsService {

    private final ShowRepository showRepository;
    private final MovieRepository movieRepository;
    private final ReviewRepository reviewRepository;
    private final MovieInterestRepository movieInterestRepository;

    public AnalyticsService(ShowRepository showRepository,
                            MovieRepository movieRepository,
                            ReviewRepository reviewRepository,
                            MovieInterestRepository movieInterestRepository) {
        this.showRepository = showRepository;
        this.movieRepository = movieRepository;
        this.reviewRepository = reviewRepository;
        this.movieInterestRepository = movieInterestRepository;
    }

    /** Average rating + review count per movie shown at the theater, best rated first. */
    @Transactional(readOnly = true)
    public List<MovieRatingResponse> movieRatings(Long theaterId) {
        requireTheater(theaterId);
        Set<Long> movieIds = theaterMovieIds(theaterId);
        if (movieIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Movie> moviesById = moviesById(movieIds);
        return reviewRepository.averageRatingByMovie(movieIds).stream()
                .map(agg -> toRating(agg, moviesById.get(agg.getMovieId())))
                .sorted(Comparator.comparingDouble(
                        (MovieRatingResponse r) -> r.getAverageRating() == null ? 0.0 : r.getAverageRating())
                        .reversed())
                .toList();
    }

    /** Waitlist (interest) count per movie shown at the theater, most-wanted first. */
    @Transactional(readOnly = true)
    public List<MovieInterestResponse> movieInterest(Long theaterId) {
        requireTheater(theaterId);
        Set<Long> movieIds = theaterMovieIds(theaterId);
        if (movieIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Movie> moviesById = moviesById(movieIds);
        return movieInterestRepository.countByMovieIn(movieIds).stream()
                .map(agg -> toInterest(agg, moviesById.get(agg.getMovieId())))
                .sorted(Comparator.comparingLong(
                        (MovieInterestResponse r) -> r.getWaitlistCount() == null ? 0L : r.getWaitlistCount())
                        .reversed())
                .toList();
    }

    /** Distinct, non-archived movie ids that the theater currently schedules shows for. */
    private Set<Long> theaterMovieIds(Long theaterId) {
        return showRepository.findByTheaterIdAndDeletedFalse(theaterId).stream()
                .map(Show::getMovieId)
                .collect(Collectors.toSet());
    }

    /** Batch-load the movies so each aggregate row can be enriched without extra queries. */
    private Map<Long, Movie> moviesById(Set<Long> movieIds) {
        return movieRepository.findAllById(movieIds).stream()
                .collect(Collectors.toMap(Movie::getId, Function.identity()));
    }

    private MovieRatingResponse toRating(MovieRatingAggregate agg, Movie movie) {
        return new MovieRatingResponse(
                agg.getMovieId(),
                movie != null ? movie.getTitle() : "Unknown",
                movie != null ? movie.getPosterUrl() : null,
                agg.getAverageRating(),
                agg.getReviewCount());
    }

    private MovieInterestResponse toInterest(MovieInterestAggregate agg, Movie movie) {
        return new MovieInterestResponse(
                agg.getMovieId(),
                movie != null ? movie.getTitle() : "Unknown",
                movie != null ? movie.getPosterUrl() : null,
                agg.getWaitlistCount());
    }

    /** Only admins (who carry a theaterId in their token) may read theater analytics. */
    private void requireTheater(Long theaterId) {
        if (theaterId == null) {
            throw ApiException.forbidden("No theater is associated with this account");
        }
    }
}
