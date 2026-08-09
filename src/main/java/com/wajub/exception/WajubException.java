package com.wajub.exception;

import java.util.Collections;
import java.util.Map;

/** Base exception for API failures. */
public class WajubException extends RuntimeException {

    private final String code;
    private final int httpStatus;
    private final Map<String, String> errors;
    private final Map<String, Object> raw;

    public WajubException(String message, String code, int httpStatus,
                          Map<String, String> errors, Map<String, Object> raw) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.errors = errors != null ? Map.copyOf(errors) : Map.of();
        this.raw = raw != null ? Map.copyOf(raw) : Map.of();
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public Map<String, String> getErrors() {
        return Collections.unmodifiableMap(errors);
    }

    public Map<String, Object> getRaw() {
        return Collections.unmodifiableMap(raw);
    }
}
