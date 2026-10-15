package com.wajub.exception;

import java.util.Map;

/** Returned when the HTTP transport fails. */
public class ApiConnectionException extends WajubException {

    public ApiConnectionException(String message) {
        super(message, "network_error", 0, null, null);
    }
}
