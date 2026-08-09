package com.wajub.exception;

import java.util.Map;

/** Returned for HTTP 429 responses. */
public class RateLimitException extends WajubException {

    private final int retryAfter;

    public RateLimitException(String message, String code, int httpStatus,
                              Map<String, String> errors, Map<String, Object> raw,
                              int retryAfter) {
        super(message, code, httpStatus, errors, raw);
        this.retryAfter = retryAfter;
    }

    public int getRetryAfter() {
        return retryAfter;
    }
}
