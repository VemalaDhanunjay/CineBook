package com.cinebook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Stripe configuration bound from {@code app.stripe.*} in application.properties.
 * Secrets are supplied via environment variables (see STRIPE_SETUP.md); when the
 * secret key is blank the payment endpoints fail fast with a clear message rather
 * than calling Stripe with no credentials.
 */
@Component
@ConfigurationProperties(prefix = "app.stripe")
public class StripeProperties {

    private String secretKey;
    private String webhookSecret;
    private String successUrl;
    private String cancelUrl;
    private String currency = "inr";
    private int holdTtlMinutes = 30;

    /** True once a Stripe secret key has been configured. */
    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }

    public String getSuccessUrl() { return successUrl; }
    public void setSuccessUrl(String successUrl) { this.successUrl = successUrl; }

    public String getCancelUrl() { return cancelUrl; }
    public void setCancelUrl(String cancelUrl) { this.cancelUrl = cancelUrl; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public int getHoldTtlMinutes() { return holdTtlMinutes; }
    public void setHoldTtlMinutes(int holdTtlMinutes) { this.holdTtlMinutes = holdTtlMinutes; }
}
