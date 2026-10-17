package com.wajub.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Base exception for API failures. */
public class WajubException extends RuntimeException {

    private final String code;
    private final int httpStatus;
    private final Map<String, String> errors;
    private final Map<String, List<String>> fieldErrors;
    private final Map<String, Object> raw;

    /** Builds the exception with one message per field, which {@link #getFieldErrors()} lists too. */
    public WajubException(String message, String code, int httpStatus,
                          Map<String, String> errors, Map<String, Object> raw) {
        this(message, code, httpStatus, errors, null, raw);
    }

    /**
     * Builds the exception with every message of each field. When {@code fieldErrors} is null,
     * {@link #getFieldErrors()} lists the messages of {@code errors}.
     */
    public WajubException(String message, String code, int httpStatus,
                          Map<String, String> errors, Map<String, List<String>> fieldErrors,
                          Map<String, Object> raw) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        // JSON nulls are kept as they are: Map.copyOf would refuse them.
        this.errors = errors != null ? Collections.unmodifiableMap(new LinkedHashMap<>(errors)) : Map.of();
        this.fieldErrors = copyFieldErrors(fieldErrors != null ? fieldErrors : asLists(this.errors));
        this.raw = raw != null ? Collections.unmodifiableMap(new LinkedHashMap<>(raw)) : Map.of();
    }

    private static Map<String, List<String>> asLists(Map<String, String> errors) {
        Map<String, List<String>> lists = new LinkedHashMap<>();
        errors.forEach((field, message) -> lists.put(field, Collections.singletonList(message)));
        return lists;
    }

    /** Every field, with its messages; a null list or message is no message. */
    private static Map<String, List<String>> copyFieldErrors(Map<String, List<String>> fieldErrors) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        fieldErrors.forEach((field, messages) -> copy.put(field, messages == null ? List.of()
                : messages.stream().filter(Objects::nonNull).toList()));
        return Collections.unmodifiableMap(copy);
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    /** The first validation message of each field. {@link #getFieldErrors()} has them all. */
    public Map<String, String> getErrors() {
        return errors;
    }

    /** Every validation message of each field, in the order the API gave them. */
    public Map<String, List<String>> getFieldErrors() {
        return fieldErrors;
    }

    public Map<String, Object> getRaw() {
        return raw;
    }
}
