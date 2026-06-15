package com.cinebook.dto;

import com.cinebook.entity.Theater;

/**
 * Public read-shape for a theater. Omits {@code ownerUserId} since users don't
 * need to know which admin owns the venue.
 */
public class TheaterResponse {

    private Long id;
    private String name;
    private String location;

    public static TheaterResponse fromEntity(Theater theater) {
        TheaterResponse dto = new TheaterResponse();
        dto.id = theater.getId();
        dto.name = theater.getName();
        dto.location = theater.getLocation();
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
