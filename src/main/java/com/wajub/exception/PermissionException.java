package com.wajub.exception;

import java.util.Map;

/** Returned for HTTP 403 responses. */
public class PermissionException extends WajubException {

    public PermissionException(String message, String code, int httpStatus,
                               Map<String, String> errors, Map<String, Object> raw) {
        super(message, code, httpStatus, errors, raw);
    }
}
