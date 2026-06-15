package com.cinebook.repository;

import com.cinebook.entity.MovieInterest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MovieInterestRepository extends JpaRepository<MovieInterest, Long> {
    Optional<MovieInterest> findByMovieIdAndUserId(Long movieId, Long userId);
    long countByMovieId(Long movieId);

    // Waitlist (interest) count per movie, restricted to the given movie set
    // (the theater's movies). Powers the "Audience Interest" analytics footer.
    @Query("SELECT mi.movieId AS movieId, COUNT(mi.id) AS waitlistCount " +
           "FROM MovieInterest mi WHERE mi.movieId IN :movieIds " +
           "GROUP BY mi.movieId")
    List<MovieInterestAggregate> countByMovieIn(@Param("movieIds") Collection<Long> movieIds);
}
