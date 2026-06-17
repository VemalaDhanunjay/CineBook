package com.cinebook.repository;

import com.cinebook.entity.MovieInterest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MovieInterestRepository extends JpaRepository<MovieInterest, Long> {
    Optional<MovieInterest> findByMovieIdAndUserId(Long movieId, Long userId);
    long countByMovieId(Long movieId);

    // Waitlist (interest) count per movie across the WHOLE catalogue. Powers the
    // "Audience Interest" analytics panel, which deliberately spans every movie
    // (not just the theater's) to reveal demand for titles not yet scheduled.
    @Query("SELECT mi.movieId AS movieId, COUNT(mi.id) AS waitlistCount " +
           "FROM MovieInterest mi GROUP BY mi.movieId")
    List<MovieInterestAggregate> countAllByMovie();
}
