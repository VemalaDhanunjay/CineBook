package com.cinebook.dto;

/**
 * Carousel row for the admin "Most Booked" ribbon. One entry per movie with the
 * summed seat count and booking count over non-cancelled bookings, ordered by
 * seat total descending (index 0 is the leader).
 */
public class MostBookedMovieResponse {

    private Long movieId;
    private String title;
    private String posterUrl;
    private Long totalSeatsBooked;
    private Long totalBookings;

    public MostBookedMovieResponse() {}

    public MostBookedMovieResponse(Long movieId, String title, String posterUrl,
                                   Long totalSeatsBooked, Long totalBookings) {
        this.movieId = movieId;
        this.title = title;
        this.posterUrl = posterUrl;
        this.totalSeatsBooked = totalSeatsBooked;
        this.totalBookings = totalBookings;
    }

    public Long getMovieId() { return movieId; }
    public void setMovieId(Long movieId) { this.movieId = movieId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getPosterUrl() { return posterUrl; }
    public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }

    public Long getTotalSeatsBooked() { return totalSeatsBooked; }
    public void setTotalSeatsBooked(Long totalSeatsBooked) { this.totalSeatsBooked = totalSeatsBooked; }

    public Long getTotalBookings() { return totalBookings; }
    public void setTotalBookings(Long totalBookings) { this.totalBookings = totalBookings; }
}
