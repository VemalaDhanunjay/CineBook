package com.cinebook.service;

import com.cinebook.dto.ShowRequest;
import com.cinebook.entity.Show;
import com.cinebook.exception.ApiException;
import com.cinebook.repository.MovieRepository;
import com.cinebook.repository.ShowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    private final ShowRepository showRepository;
    private final MovieRepository movieRepository;

    public ShowService(ShowRepository showRepository, MovieRepository movieRepository) {
        this.showRepository = showRepository;
        this.movieRepository = movieRepository;
    }

    /** All non-deleted shows for a single theater (the admin's own theater). */
    public List<Show> listShows(Long theaterId) {
        requireTheater(theaterId);
        return showRepository.findByTheaterIdAndDeletedFalse(theaterId);
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
