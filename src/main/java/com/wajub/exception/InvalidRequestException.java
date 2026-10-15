package com.wajub.exception;

import java.util.List;
import java.util.Map;

/** Returned for HTTP 400/422 responses. */
public class InvalidRequestException extends WajubException {

    public InvalidRequestException(String message, String code, int httpStatus,
                                   Map<String, String> errors, Map<String, Object> raw) {
        super(message, code, httpStatus, errors, raw);
    }

    public InvalidRequestException(String message, String code, int httpStatus,
                                   Map<String, String> errors, Map<String, List<String>> fieldErrors,
                                   Map<String, Object> raw) {
        super(message, code, httpStatus, errors, fieldErrors, raw);
    }
}
