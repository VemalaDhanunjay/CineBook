package com.cinebook.service;

import com.cinebook.dto.MovieRequest;
import com.cinebook.entity.Movie;
import com.cinebook.exception.ApiException;
import com.cinebook.repository.MovieRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD for the movie catalog. Deletes are soft (the {@code deleted} flag) so that
 * shows and bookings referencing a movie are never orphaned.
 */
@Service
public class MovieService {

    private final MovieRepository movieRepository;

    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    /** All movies that have not been soft-deleted. */
    public List<Movie> listMovies() {

        return movieRepository.findByDeletedFalse();
    }

    public Movie getMovie(Long id) {
        return movieRepository.findById(id)
                .filter(movie -> !movie.isDeleted())
                .orElseThrow(() -> ApiException.notFound("Movie not found"));
    }

    @Transactional
    public Movie createMovie(MovieRequest request) {
        Movie movie = new Movie();
        apply(movie, request);
        return movieRepository.save(movie);
    }

    @Transactional
    public Movie updateMovie(Long id, MovieRequest request) {
        Movie movie = getMovie(id);
        apply(movie, request);
        return movieRepository.save(movie);
    }

    @Transactional
    public void deleteMovie(Long id) {
        Movie movie = getMovie(id);
        movie.setDeleted(true);
        movieRepository.save(movie);
    }

    private void apply(Movie movie, MovieRequest request) {
        movie.setTitle(request.getTitle().trim());
        movie.setGenre(request.getGenre().trim());
        movie.setDurationMins(request.getDurationMins());
        movie.setLanguages(request.getLanguages().trim());
        movie.setPosterUrl(request.getPosterUrl().trim());
        movie.setTrailerUrl(request.getTrailerUrl().trim());
        movie.setPrice(request.getPrice());
    }
}
