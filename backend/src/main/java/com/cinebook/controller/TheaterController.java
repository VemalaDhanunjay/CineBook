package com.cinebook.controller;

import com.cinebook.dto.TheaterResponse;
import com.cinebook.exception.ApiException;
import com.cinebook.repository.TheaterRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public theater catalog for the user-facing Theaters page. Authenticated read
 * access only (default JWT guard from {@code SecurityConfig}); no role check
 * because both USER and ADMIN can browse the directory.
 */
@RestController
@RequestMapping("/api/theaters")
public class TheaterController {

    private final TheaterRepository theaterRepository;

    public TheaterController(TheaterRepository theaterRepository) {
        this.theaterRepository = theaterRepository;
    }

    /** All theaters, optionally filtered by an exact (case-insensitive) location match. */
    @GetMapping
    public ResponseEntity<List<TheaterResponse>> list(@RequestParam(required = false) String location) {
        List<TheaterResponse> rows = (location == null || location.isBlank()
                ? theaterRepository.findAll()
                : theaterRepository.findByLocationIgnoreCase(location.trim()))
                .stream()
                .map(TheaterResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TheaterResponse> get(@PathVariable Long id) {
        return theaterRepository.findById(id)
                .map(TheaterResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> ApiException.notFound("Theater not found"));
    }
}
