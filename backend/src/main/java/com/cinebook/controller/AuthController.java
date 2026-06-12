package com.cinebook.controller;

import com.cinebook.dto.AuthResponse;
import com.cinebook.dto.LoginRequest;
//import com.cinebook.dto.RegisterAdminReques;
import com.cinebook.dto.RegisterAdminRequest;
import com.cinebook.dto.RegisterRequest;
import com.cinebook.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/register-admin")
    public ResponseEntity<AuthResponse> registerAdmin(@Valid @RequestBody RegisterAdminRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerAdmin(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
