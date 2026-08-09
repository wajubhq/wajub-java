package com.wajub.exception;

import java.util.Map;

/** Returned for HTTP 404 responses. */
public class NotFoundException extends WajubException {

    public NotFoundException(String message, String code, int httpStatus,
                             Map<String, String> errors, Map<String, Object> raw) {
        super(message, code, httpStatus, errors, raw);
    }
}
