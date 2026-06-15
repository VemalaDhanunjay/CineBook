package com.cinebook.repository;

/**
 * Spring Data projection for the waitlist-by-movie aggregation query. One row per
 * movie with the count of users who flagged interest (the "audience interest" tally).
 */
public interface MovieInterestAggregate {
    Long getMovieId();
    Long getWaitlistCount();
}
