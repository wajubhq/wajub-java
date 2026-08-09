package com.wajub.exception;

import java.util.Map;

/** Returned for HTTP 401 responses. */
public class AuthenticationException extends WajubException {

    public AuthenticationException(String message, String code, int httpStatus,
                                   Map<String, String> errors, Map<String, Object> raw) {
        super(message, code, httpStatus, errors, raw);
    }
}
