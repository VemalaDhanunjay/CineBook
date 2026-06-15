package com.cinebook.dto;

/**
 * Per-movie interest snapshot served to {@code GET /api/movies/:id/interest}.
 * Carries whether the calling user has marked their interest, plus the running
 * total across all users so the UI can show "X people interested".
 */
public class MovieInterestStatusResponse {

    private Long movieId;
    private boolean interested;
    private long count;

    public MovieInterestStatusResponse() {}

    public MovieInterestStatusResponse(Long movieId, boolean interested, long count) {
        this.movieId = movieId;
        this.interested = interested;
        this.count = count;
    }

    public Long getMovieId() { return movieId; }
    public void setMovieId(Long movieId) { this.movieId = movieId; }

    public boolean isInterested() { return interested; }
    public void setInterested(boolean interested) { this.interested = interested; }

    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
}
