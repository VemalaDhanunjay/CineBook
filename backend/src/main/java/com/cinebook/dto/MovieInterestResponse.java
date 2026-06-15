package com.cinebook.dto;

/**
 * Analytics row for the admin "Audience Interest" footer cards. One entry per movie
 * shown at the theater, carrying the count of users who flagged interest (waitlist).
 */
public class MovieInterestResponse {

    private Long movieId;
    private String title;
    private String posterUrl;
    private Long waitlistCount;

    public MovieInterestResponse() {}

    public MovieInterestResponse(Long movieId, String title, String posterUrl, Long waitlistCount) {
        this.movieId = movieId;
        this.title = title;
        this.posterUrl = posterUrl;
        this.waitlistCount = waitlistCount;
    }

    public Long getMovieId() { return movieId; }
    public void setMovieId(Long movieId) { this.movieId = movieId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getPosterUrl() { return posterUrl; }
    public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }

    public Long getWaitlistCount() { return waitlistCount; }
    public void setWaitlistCount(Long waitlistCount) { this.waitlistCount = waitlistCount; }
}
