package com.cinebook.repository;

/**
 * Spring Data projection for the ratings-by-movie aggregation query. One row per
 * movie with the average star rating and the number of reviews backing it.
 */
public interface MovieRatingAggregate {
    Long getMovieId();
    Double getAverageRating();
    Long getReviewCount();
}
