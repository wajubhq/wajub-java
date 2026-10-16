package com.wajub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wajub.exception.WebhookSignatureVerificationException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

/** Verifies inbound webhook signatures. */
public class Webhooks {

    private static final Duration DEFAULT_TOLERANCE = Duration.ofSeconds(300);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String secret;
    private final Clock clock;

    public Webhooks(String secret) {
        this(secret, Clock.systemUTC());
    }

    Webhooks(String secret, Clock clock) {
        this.secret = secret;
        this.clock = clock;
    }

    /**
     * Verifies X-Wajub-Signature and parses the event payload.
     *
     * @param payload   raw request body, exactly as received
     * @param signature value of X-Wajub-Signature header
     * @param timestamp value of X-Wajub-Timestamp header
     * @param tolerance maximum allowed timestamp drift, rounded up to whole seconds as the
     *                  timestamp is in whole seconds (500 ms is one second); null, zero or
     *                  negative keeps the default 300 seconds, so replay protection cannot be
     *                  switched off
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
        if (signature == null || signature.isBlank() || timestamp == null || timestamp.isBlank()) {
            throw new WebhookSignatureVerificationException(
                    "Missing X-Wajub-Signature or X-Wajub-Timestamp header.");
        }
        if (!signature.startsWith("v1=")) {
            throw new WebhookSignatureVerificationException(
                    "Invalid webhook signature format. Expected v1={hash}.");
        }

        // Over the body's bytes as received: decoded as text, bytes that are not valid
        // UTF-8 would all read the same, and different bodies would share a signature.
        String expected = Crypto.hmacSha256(secret, (timestamp + ".").getBytes(StandardCharsets.UTF_8), payload);
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

        Duration allowed = (tolerance == null || tolerance.isZero() || tolerance.isNegative())
                ? DEFAULT_TOLERANCE
                : tolerance;
        // The timestamp is in whole seconds, so is the window: a part of a second
        // counts as a whole one, and a tolerance under a second is never 0 s.
        long allowedSeconds = allowed.getSeconds() + (allowed.getNano() > 0 ? 1 : 0);
        long driftSeconds = driftSecondsFromNow(ts);
        if (driftSeconds > allowedSeconds) {
            throw new WebhookSignatureVerificationException(String.format(
                    "Timestamp outside tolerance zone (%ds drift, allowed %ds).",
                    driftSeconds, allowedSeconds));
        }

        try {
            return MAPPER.readValue(payload, Map.class);
        } catch (Exception e) {
            throw new WebhookSignatureVerificationException("Invalid webhook payload JSON.");
        }
    }

    private long driftSecondsFromNow(long timestampSeconds) {
        try {
            return Math.absExact(Math.subtractExact(clock.instant().getEpochSecond(), timestampSeconds));
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }
}
