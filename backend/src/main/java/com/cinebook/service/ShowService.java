package com.cinebook.service;

import com.cinebook.dto.PublicShowResponse;
import com.cinebook.dto.ShowRequest;
import com.cinebook.entity.BookingStatus;
import com.cinebook.entity.Show;
import com.cinebook.entity.Theater;
import com.cinebook.exception.ApiException;
import com.cinebook.repository.BookingRepository;
import com.cinebook.repository.MovieRepository;
import com.cinebook.repository.ShowRepository;
import com.cinebook.repository.TheaterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CRUD for shows (screenings). Mirrors {@link MovieService}: deletes are soft (the
 * {@code deleted} flag) so bookings referencing a show are never orphaned.
 *
 * <p>Every show belongs to a theater. The owning {@code theaterId} comes from the
 * authenticated admin's JWT (passed in by the controller) — it is never taken from
 * the client payload — and write operations verify the caller owns the show.
 */
@Service
public class ShowService {

    private static final List<BookingStatus> ACTIVE_BOOKING_STATUSES = List.of(
            BookingStatus.PENDING_PAYMENT,
            BookingStatus.CONFIRMED,
            BookingStatus.PARTIALLY_CANCELLED
    );

    private final ShowRepository showRepository;
    private final MovieRepository movieRepository;
    private final TheaterRepository theaterRepository;
    private final BookingRepository bookingRepository;

    public ShowService(ShowRepository showRepository,
                       MovieRepository movieRepository,
                       TheaterRepository theaterRepository,
                       BookingRepository bookingRepository) {
        this.showRepository = showRepository;
        this.movieRepository = movieRepository;
        this.theaterRepository = theaterRepository;
        this.bookingRepository = bookingRepository;
    }

    /** All non-deleted shows for a single theater (the admin's own theater). */
    public List<Show> listShows(Long theaterId) {
        requireTheater(theaterId);
        return showRepository.findByTheaterIdAndDeletedFalse(theaterId);
    }

    /**
     * Upcoming (future) shows for one movie, theater-enriched, soonest first.
     * Drives the user booking page; not theater-scoped because users browse the
     * whole platform.
     */
    @Transactional(readOnly = true)
    public List<PublicShowResponse> listUpcomingForMovie(Long movieId) {
        LocalDateTime now = LocalDateTime.now();
        List<Show> shows = showRepository.findByMovieIdAndDeletedFalse(movieId).stream()
                .filter(show -> show.getShowTime() != null && show.getShowTime().isAfter(now))
                .sorted(Comparator.comparing(Show::getShowTime))
                .toList();
        if (shows.isEmpty()) {
            return List.of();
        }

        List<Long> theaterIds = shows.stream().map(Show::getTheaterId).distinct().toList();
        Map<Long, Theater> theatersById = theaterRepository.findAllById(theaterIds).stream()
                .collect(Collectors.toMap(Theater::getId, Function.identity()));

        return shows.stream()
                .map(show -> toPublicResponse(show, theatersById.get(show.getTheaterId())))
                .toList();
    }

    /**
     * Upcoming (future) shows for one theater, soonest first. Drives the theater
     * detail page; not theater-scoped to the caller because users browse every venue.
     */
    @Transactional(readOnly = true)
    public List<PublicShowResponse> listUpcomingForTheater(Long theaterId) {
        LocalDateTime now = LocalDateTime.now();
        List<Show> shows = showRepository.findByTheaterIdAndDeletedFalse(theaterId).stream()
                .filter(show -> show.getShowTime() != null && show.getShowTime().isAfter(now))
                .sorted(Comparator.comparing(Show::getShowTime))
                .toList();
        if (shows.isEmpty()) {
            return List.of();
        }
        Theater theater = theaterRepository.findById(theaterId).orElse(null);
        return shows.stream()
                .map(show -> toPublicResponse(show, theater))
                .toList();
    }

    private PublicShowResponse toPublicResponse(Show show, Theater theater) {
        PublicShowResponse dto = new PublicShowResponse();
        dto.setId(show.getId());
        dto.setMovieId(show.getMovieId());
        dto.setTheaterId(show.getTheaterId());
        dto.setTheaterName(theater == null ? null : theater.getName());
        dto.setTheaterLocation(theater == null ? null : theater.getLocation());
        dto.setShowTime(show.getShowTime());
        dto.setLanguage(show.getLanguage());
        dto.setTicketPrice(show.getTicketPrice());
        dto.setTotalSeats(show.getTotalSeats());
        dto.setAvailableSeats(show.getAvailableSeats());
        return dto;
    }

    public Show getShow(Long id) {
        return showRepository.findById(id)
                .filter(show -> !show.isDeleted())
                .orElseThrow(() -> ApiException.notFound("Show not found"));
    }

    @Transactional
    public Show createShow(ShowRequest request, Long theaterId) {
        requireTheater(theaterId);
        Show show = new Show();
        show.setTheaterId(theaterId);
        apply(show, request);
        // On create, the whole house is open — available seats equal total capacity.
        show.setAvailableSeats(request.getTotalSeats());
        return showRepository.save(show);
    }

    @Transactional
    public Show updateShow(Long id, ShowRequest request, Long theaterId) {
        Show show = getShow(id);
        requireOwnership(show, theaterId);

        // Preserve already-booked seats: shift availability by the change in capacity
        // rather than blindly resetting it, so seats sold so far stay accounted for.
        int previousTotal = show.getTotalSeats() == null ? 0 : show.getTotalSeats();
        int previousAvailable = show.getAvailableSeats() == null ? 0 : show.getAvailableSeats();
        int delta = request.getTotalSeats() - previousTotal;
        int nextAvailable = Math.max(0, previousAvailable + delta);

        apply(show, request);
        show.setAvailableSeats(Math.min(nextAvailable, request.getTotalSeats()));
        return showRepository.save(show);
    }

    @Transactional
    public void deleteShow(Long id, Long theaterId) {
        Show show = getShow(id);
        requireOwnership(show, theaterId);
        if (bookingRepository.existsByShowIdAndStatusIn(show.getId(), ACTIVE_BOOKING_STATUSES)) {
            throw ApiException.conflict("Cannot delete this show because it has active bookings");
        }
        show.setDeleted(true);
        showRepository.save(show);
    }

    /** Copy validated request fields onto the entity (capacity handled by the callers). */
    private void apply(Show show, ShowRequest request) {
        // The movie must exist and not be archived before we can schedule a show for it.
        movieRepository.findById(request.getMovieId())
                .filter(movie -> !movie.isDeleted())
                .orElseThrow(() -> ApiException.badRequest("Movie not found"));

        show.setMovieId(request.getMovieId());
        show.setShowTime(request.getShowTime());
        show.setLanguage(request.getLanguage().trim());
        show.setTicketPrice(request.getTicketPrice());
        show.setTotalSeats(request.getTotalSeats());
    }

    /** Only admins (who carry a theaterId in their token) may manage shows. */
    private void requireTheater(Long theaterId) {
        if (theaterId == null) {
            throw ApiException.forbidden("No theater is associated with this account");
        }
    }

    /** Guard against one theater's admin mutating another theater's shows. */
    private void requireOwnership(Show show, Long theaterId) {
        requireTheater(theaterId);
        if (!theaterId.equals(show.getTheaterId())) {
            throw ApiException.forbidden("This show belongs to another theater");
        }
    }
}
