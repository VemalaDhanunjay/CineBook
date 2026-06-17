package com.cinebook.dto;

import jakarta.validation.constraints.NotBlank;

/** Body carrying a Stripe Checkout Session id — used by the confirm and cancel-hold endpoints. */
public class SessionRequest {

    @NotBlank
    private String sessionId;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
