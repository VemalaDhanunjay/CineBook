package com.cinebook.dto;

/**
 * Analytics row for the admin "Top Rated" chart. One entry per movie shown at the
 * theater, carrying the average star rating and the number of reviews behind it.
 */
public class MovieRatingResponse {

    private Long movieId;
    private String title;
    private String posterUrl;
    private Double averageRating;
    private Long reviewCount;

    public MovieRatingResponse() {}

    public MovieRatingResponse(Long movieId, String title, String posterUrl,
                               Double averageRating, Long reviewCount) {
        this.movieId = movieId;
        this.title = title;
        this.posterUrl = posterUrl;
        this.averageRating = averageRating;
        this.reviewCount = reviewCount;
    }

    public Long getMovieId() { return movieId; }
    public void setMovieId(Long movieId) { this.movieId = movieId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getPosterUrl() { return posterUrl; }
    public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }

    public Double getAverageRating() { return averageRating; }
    public void setAverageRating(Double averageRating) { this.averageRating = averageRating; }

    public Long getReviewCount() { return reviewCount; }
    public void setReviewCount(Long reviewCount) { this.reviewCount = reviewCount; }
}
