package com.cinebook.repository;

import com.cinebook.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByMovieId(Long movieId);

    /** Reviews on a single movie, newest first — drives the public reviews list. */
    List<Review> findByMovieIdOrderByCreatedAtDesc(Long movieId);

    Optional<Review> findByBookingId(Long bookingId);

    /** One review per booking — guard for the "you already rated this" path. */
    boolean existsByBookingId(Long bookingId);

    // Average rating + review count per movie, restricted to the given movie set
    // (the theater's movies). Powers the "Top Rated" analytics chart.
    @Query("SELECT r.movieId AS movieId, " +
           "AVG(r.rating) AS averageRating, " +
           "COUNT(r.id) AS reviewCount " +
           "FROM Review r WHERE r.movieId IN :movieIds " +
           "GROUP BY r.movieId")
    List<MovieRatingAggregate> averageRatingByMovie(@Param("movieIds") Collection<Long> movieIds);
}
