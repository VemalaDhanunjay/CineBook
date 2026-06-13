package com.cinebook.service;

import com.cinebook.dto.AdminBookingResponse;
import com.cinebook.dto.MostBookedMovieResponse;
import com.cinebook.entity.Booking;
import com.cinebook.entity.Movie;
import com.cinebook.entity.Show;
import com.cinebook.entity.Theater;
import com.cinebook.entity.User;
import com.cinebook.exception.ApiException;
import com.cinebook.repository.BookingRepository;
import com.cinebook.repository.MovieRepository;
import com.cinebook.repository.MovieSeatAggregate;
import com.cinebook.repository.ShowRepository;
import com.cinebook.repository.TheaterRepository;
import com.cinebook.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-side service powering the admin "All Bookings" dashboard. Lists bookings
 * for the calling admin's theater and computes the "Most Booked" leaderboard.
 *
 * <p>Theater scope: callers pass in the {@code theaterId} from the authenticated
 * JWT — never trusted from the client — matching the {@link ShowService} pattern.
 */
@Service
public class BookingService {

    private static final int MOST_BOOKED_LIMIT = 5;

    private final BookingRepository bookingRepository;
    private final ShowRepository showRepository;
    private final MovieRepository movieRepository;
    private final TheaterRepository theaterRepository;
    private final UserRepository userRepository;

    public BookingService(BookingRepository bookingRepository,
                          ShowRepository showRepository,
                          MovieRepository movieRepository,
                          TheaterRepository theaterRepository,
                          UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.showRepository = showRepository;
        this.movieRepository = movieRepository;
        this.theaterRepository = theaterRepository;
        this.userRepository = userRepository;
    }

    /** All bookings for the admin's own theater, enriched with movie + show + user context. */
    @Transactional(readOnly = true)
    public List<AdminBookingResponse> listForTheater(Long theaterId) {
        requireTheater(theaterId);

        List<Booking> bookings = bookingRepository.findAllByTheaterId(theaterId);
        if (bookings.isEmpty()) {
            return List.of();
        }

        // Build id → entity lookups once so we don't re-query inside the per-row mapping loop.
        List<Long> showIds = bookings.stream().map(Booking::getShowId).distinct().toList();
        Map<Long, Show> showsById = showRepository.findAllById(showIds).stream()
                .collect(Collectors.toMap(Show::getId, Function.identity()));

        List<Long> movieIds = showsById.values().stream().map(Show::getMovieId).distinct().toList();
        Map<Long, Movie> moviesById = movieRepository.findAllById(movieIds).stream()
                .collect(Collectors.toMap(Movie::getId, Function.identity()));

        Theater theater = theaterRepository.findById(theaterId).orElse(null);

        List<Long> userIds = bookings.stream().map(Booking::getUserId).distinct().toList();
        Map<Long, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<AdminBookingResponse> rows = new ArrayList<>(bookings.size());
        for (Booking booking : bookings) {
            Show show = showsById.get(booking.getShowId());
            Movie movie = show == null ? null : moviesById.get(show.getMovieId());
            User user = usersById.get(booking.getUserId());
            rows.add(toResponse(booking, show, movie, theater, user));
        }
        return rows;
    }

    /** Top {@value #MOST_BOOKED_LIMIT} movies by summed seats_booked for the admin's theater. */
    @Transactional(readOnly = true)
    public List<MostBookedMovieResponse> mostBookedMovies(Long theaterId) {
        requireTheater(theaterId);

        List<MovieSeatAggregate> aggregates = bookingRepository.sumSeatsBookedByMovie(theaterId);
        if (aggregates.isEmpty()) {
            return List.of();
        }

        List<Long> movieIds = aggregates.stream().map(MovieSeatAggregate::getMovieId).toList();
        Map<Long, Movie> moviesById = movieRepository.findAllById(movieIds).stream()
                .filter(movie -> !movie.isDeleted())
                .collect(Collectors.toMap(Movie::getId, Function.identity()));

        // Aggregates already arrive ordered by seat count descending; preserve that order
        // and skip movies that have since been soft-deleted from the catalog.
        return aggregates.stream()
                .map(agg -> {
                    Movie movie = moviesById.get(agg.getMovieId());
                    if (movie == null) return null;
                    return new MostBookedMovieResponse(
                            movie.getId(),
                            movie.getTitle(),
                            movie.getPosterUrl(),
                            agg.getTotalSeats(),
                            agg.getTotalBookings()
                    );
                })
                .filter(java.util.Objects::nonNull)
                .limit(MOST_BOOKED_LIMIT)
                .toList();
    }

    private AdminBookingResponse toResponse(Booking booking, Show show, Movie movie,
                                            Theater theater, User user) {
        AdminBookingResponse row = new AdminBookingResponse();
        row.setId(booking.getId());
        row.setUserId(booking.getUserId());
        row.setUsername(user == null ? "User #" + booking.getUserId() : user.getUsername());

        row.setMovieId(movie == null ? null : movie.getId());
        row.setMovieTitle(movie == null ? "(unavailable)" : movie.getTitle());
        row.setMoviePosterUrl(movie == null ? null : movie.getPosterUrl());

        row.setShowId(booking.getShowId());
        row.setShowTime(show == null ? null : show.getShowTime());
        row.setTheaterName(theater == null ? null : theater.getName());
        row.setTheaterLocation(theater == null ? null : theater.getLocation());

        row.setSeatsBooked(booking.getSeatsBooked());
        row.setSeatNumbers(booking.getSeats());
        row.setSubtotal(booking.getSubtotal());
        row.setTaxAmount(booking.getTaxAmount());
        row.setTotalAmount(booking.getTotalAmount());
        row.setRefundAmount(booking.getRefundAmount());
        row.setStatus(booking.getStatus());
        row.setBookingDate(booking.getBookingDate());
        row.setCancelledAt(booking.getCancelledAt());
        return row;
    }

    private void requireTheater(Long theaterId) {
        if (theaterId == null) {
            throw ApiException.forbidden("No theater is associated with this account");
        }
    }
}
