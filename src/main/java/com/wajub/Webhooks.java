package com.wajub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wajub.exception.WebhookSignatureVerificationException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
     * @param payload   raw request body, exactly as received
     * @param signature value of X-Wajub-Signature header
     * @param timestamp value of X-Wajub-Timestamp header
     * @param tolerance maximum allowed timestamp drift, to the millisecond; null, zero or
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
        // To the millisecond, so that a tolerance under a second is not cut to 0 s.
        Duration drift = driftFromNow(ts);
        if (drift.compareTo(allowed) > 0) {
            throw new WebhookSignatureVerificationException(String.format(
                    "Timestamp outside tolerance zone (%ss drift, allowed %ss).",
                    seconds(drift), seconds(allowed)));
        }

        try {
            return MAPPER.readValue(payload, Map.class);
        } catch (Exception e) {
            throw new WebhookSignatureVerificationException("Invalid webhook payload JSON.");
        }
    }

    private static Duration driftFromNow(long timestampSeconds) {
        try {
            long millis = Math.subtractExact(System.currentTimeMillis(), Math.multiplyExact(timestampSeconds, 1000L));
            return Duration.ofMillis(Math.abs(millis));
        } catch (ArithmeticException e) {
            return Duration.ofSeconds(Long.MAX_VALUE);
        }
    }

    private static String seconds(Duration duration) {
        return BigDecimal.valueOf(duration.getSeconds())
                .add(BigDecimal.valueOf(duration.getNano(), 9))
                .stripTrailingZeros()
                .toPlainString();
    }
}
