package com.cinebook.controller;

import com.cinebook.dto.ShowRequest;
import com.cinebook.entity.Show;
import com.cinebook.security.AuthPrincipal;
import com.cinebook.service.ShowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Show (screening) API. All endpoints are theater-scoped: the owning {@code theaterId}
 * is read from the authenticated caller's JWT, so an admin only ever sees and manages
 * their own theater's shows. Writes are restricted to theater owners (ROLE ADMIN).
 */
@RestController
@RequestMapping("/api/shows")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    /** List the calling admin's theater's shows. */
    @GetMapping
    public ResponseEntity<List<Show>> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(showService.listShows(principal.theaterId()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Show> create(
            @Valid @RequestBody ShowRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        Show created = showService.createShow(request, principal.theaterId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<Show> update(
            @PathVariable Long id,
            @Valid @RequestBody ShowRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(showService.updateShow(id, request, principal.theaterId()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthPrincipal principal) {
        showService.deleteShow(id, principal.theaterId());
        return ResponseEntity.noContent().build();
    }
}
