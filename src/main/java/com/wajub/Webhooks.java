package com.wajub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wajub.exception.WebhookSignatureVerificationException;

import java.time.Duration;
import java.util.Map;

/** Verifies inbound webhook signatures. */
public class Webhooks {

    private static final Duration DEFAULT_TOLERANCE = Duration.ofSeconds(300);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String secret;

    public Webhooks(String secret) {
        this.secret = secret;
    }

    /**
     * Verifies X-Wajub-Signature and parses the event payload.
     *
     * @param payload   raw request body
     * @param signature value of X-Wajub-Signature header
     * @param timestamp value of X-Wajub-Timestamp header
     * @param tolerance maximum allowed timestamp drift; null or zero uses default (300s)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> constructEvent(
            byte[] payload,
            String signature,
            String timestamp,
            Duration tolerance) throws WebhookSignatureVerificationException {

        if (secret == null || secret.isBlank()) {
            throw new WebhookSignatureVerificationException(
                    "Webhook secret is required for constructEvent");
        }
        if (signature == null || !signature.startsWith("v1=")) {
            throw new WebhookSignatureVerificationException(
                    "Invalid webhook signature format. Expected v1={hash}.");
        }

        String body = new String(payload);
        String expected = Crypto.hmacSha256(secret, timestamp + "." + body);
        String received = signature.substring(3);

        if (!Crypto.timingSafeEqual(expected, received)) {
            throw new WebhookSignatureVerificationException(
                    "Webhook signature verification failed.");
        }

        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            throw new WebhookSignatureVerificationException("Invalid webhook timestamp.");
        }

        Duration allowed = (tolerance == null || tolerance.isZero()) ? DEFAULT_TOLERANCE : tolerance;
        long driftSeconds = Math.abs(System.currentTimeMillis() / 1000 - ts);
        if (driftSeconds > allowed.getSeconds()) {
            throw new WebhookSignatureVerificationException(String.format(
                    "Timestamp outside tolerance zone (%ds drift, allowed %ds).",
                    driftSeconds, allowed.getSeconds()));
        }

        try {
            return MAPPER.readValue(payload, Map.class);
        } catch (Exception e) {
            throw new WebhookSignatureVerificationException("Invalid webhook payload JSON.");
        }
    }
}
