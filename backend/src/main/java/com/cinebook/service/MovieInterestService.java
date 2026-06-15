package com.cinebook.service;

import com.cinebook.dto.MovieInterestStatusResponse;
import com.cinebook.entity.MovieInterest;
import com.cinebook.exception.ApiException;
import com.cinebook.repository.MovieInterestRepository;
import com.cinebook.repository.MovieRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * User "I'm interested" waitlist for movies that don't yet have upcoming shows
 * (or that the user wants to flag for later). Mark is idempotent — repeat
 * POSTs from the same user are no-ops — so the UI button can safely toggle.
 */
@Service
public class MovieInterestService {

    private final MovieInterestRepository interestRepository;
    private final MovieRepository movieRepository;

    public MovieInterestService(MovieInterestRepository interestRepository,
                                MovieRepository movieRepository) {
        this.interestRepository = interestRepository;
        this.movieRepository = movieRepository;
    }

    @Transactional(readOnly = true)
    public MovieInterestStatusResponse status(Long userId, Long movieId) {
        requireMovie(movieId);
        boolean interested = interestRepository.findByMovieIdAndUserId(movieId, userId).isPresent();
        long count = interestRepository.countByMovieId(movieId);
        return new MovieInterestStatusResponse(movieId, interested, count);
    }

    @Transactional
    public MovieInterestStatusResponse mark(Long userId, Long movieId) {
        requireMovie(movieId);
        if (interestRepository.findByMovieIdAndUserId(movieId, userId).isEmpty()) {
            MovieInterest mi = new MovieInterest();
            mi.setMovieId(movieId);
            mi.setUserId(userId);
            mi.setCreatedAt(LocalDateTime.now());
            interestRepository.save(mi);
        }
        return status(userId, movieId);
    }

    @Transactional
    public MovieInterestStatusResponse unmark(Long userId, Long movieId) {
        requireMovie(movieId);
        interestRepository
                .findByMovieIdAndUserId(movieId, userId)
                .ifPresent(interestRepository::delete);
        return status(userId, movieId);
    }

    private void requireMovie(Long movieId) {
        movieRepository.findById(movieId)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> ApiException.notFound("Movie not found"));
    }
}
