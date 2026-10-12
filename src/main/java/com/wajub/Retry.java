package com.wajub;

import okhttp3.Headers;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;

final class Retry {

    static final int MAX_RETRIES = 2;

    private static final Set<Integer> RETRYABLE_STATUS = Set.of(429, 500, 502, 503, 504);

    private Retry() {}

    static boolean shouldRetryStatus(int status) {
        return RETRYABLE_STATUS.contains(status);
    }

    static Duration retryDelay(int attempt, Headers headers) {
        if (headers != null) {
            Duration delay = parseRetryAfter(headers.get("Retry-After"));
            if (delay != null && !delay.isZero() && !delay.isNegative()) {
                return delay;
            }
        }
        long millis = 500L * (1L << (attempt - 1));
        return Duration.ofMillis(millis);
    }

    /**
     * Whether the API answered the 409 it sends while a request with the same
     * Idempotency-Key is still running ({@code errors.idempotency_key}). A call
     * sent again after a timeout can arrive before the first one has finished:
     * its answer is on the way, so the call is worth repeating with the same
     * key. Every other 409 is a real refusal.
     */
    static boolean isIdempotencyKeyInProgress(int status, java.util.Map<String, Object> body) {
        if (status != 409 || body == null) {
            return false;
        }
        Object errors = body.get("errors");
        return errors instanceof java.util.Map<?, ?> map && map.containsKey("idempotency_key");
    }

    /** The pause before asking again about a key still being processed: 2, 4, then 8 seconds. */
    static Duration idempotencyConflictDelay(int attempt) {
        return Duration.ofSeconds(1L << Math.max(1, attempt));
    }

    static int parseRetryAfterSeconds(Headers headers) {
        Duration delay = parseRetryAfter(headers != null ? headers.get("Retry-After") : null);
        if (delay == null) {
            return 0;
        }
        return (int) delay.getSeconds();
    }

    private static Duration parseRetryAfter(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        header = header.trim();
        try {
            return Duration.ofSeconds(Long.parseLong(header));
        } catch (NumberFormatException ignored) {
            // fall through
        }
        try {
            Instant target = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(header));
            Duration delay = Duration.between(Instant.now(), target);
            if (delay.isNegative()) {
                return Duration.ZERO;
            }
            return delay;
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
