package com.cinebook.dto;

import java.time.LocalDateTime;

/**
 * One review row exposed to readers. Includes the reviewer's username so the
 * list view can label entries without an extra lookup on the client.
 */
public class ReviewResponse {

    private Long id;
    private Long movieId;
    private Long userId;
    private String username;
    private Integer rating;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMovieId() { return movieId; }
    public void setMovieId(Long movieId) { this.movieId = movieId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
