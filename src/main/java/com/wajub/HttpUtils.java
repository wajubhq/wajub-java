package com.wajub;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wajub.exception.AuthenticationException;
import com.wajub.exception.InvalidRequestException;
import com.wajub.exception.NotFoundException;
import com.wajub.exception.PermissionException;
import com.wajub.exception.RateLimitException;
import com.wajub.exception.WajubException;
import okhttp3.HttpUrl;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

final class HttpUtils {

    static final String API_URL = "https://api.wajub.com";

    private static final Pattern BEARER_PREFIX = Pattern.compile("(?i)^Bearer\\s+");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HttpUrl SEGMENT_BASE = HttpUrl.get(API_URL);

    private HttpUtils() {}

    static String normalizeApiKey(String raw) {
        if (raw == null) {
            return "";
        }
        String key = raw.trim();
        if (BEARER_PREFIX.matcher(key).lookingAt()) {
            key = BEARER_PREFIX.matcher(key).replaceFirst("").trim();
        }
        return key;
    }

    /**
     * The id as one segment of a request path, without the spaces around it, percent-encoded.
     * Wajub ids are never empty, "." or "..", and never contain "/": such an id would reach
     * another path, so it is refused before anything is sent.
     */
    static String pathSegment(String id) {
        String segment = id == null ? null : id.trim();
        if (segment == null || segment.isEmpty() || segment.equals(".") || segment.equals("..")
                || segment.indexOf('/') >= 0) {
            throw new IllegalArgumentException("wajub: invalid id " + (id == null ? "null" : "\"" + id + "\"")
                    + ": a Wajub id is never empty, \".\" or \"..\", and never contains \"/\"");
        }
        return SEGMENT_BASE.newBuilder().addPathSegment(segment).build().encodedPath().substring(1);
    }

    static String defaultApiUrl() {
        return API_URL;
    }

    static String createIdempotencyKey(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            prefix = "wajub";
        }
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return prefix + "-" + HexFormat.of().formatHex(bytes);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> pickResource(Map<String, Object> body, String... keys) {
        if (body == null) {
            return Map.of();
        }
        for (String key : keys) {
            Object value = body.get(key);
            if (value instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    static ListResult pickList(Map<String, Object> body, String... keys) {
        if (body == null) {
            return new ListResult(List.of(), null);
        }
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null) {
                return new ListResult(toMapList(value), toMeta(body.get("meta")));
            }
        }
        Object items = body.get("items");
        if (items != null) {
            return new ListResult(toMapList(items), toMeta(body.get("meta")));
        }
        return new ListResult(toMapList(body.get("data")), toMeta(body.get("meta")));
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> toMapList(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (item instanceof Map<?, ?> map) {
                out.add((Map<String, Object>) map);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> toMeta(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    static Map<String, Object> decodeJson(String data) {
        if (data == null || data.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(data, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    static String encodeJson(Map<String, Object> body) {
        try {
            return MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("JSON encoding failed", e);
        }
    }

    static WajubException errorFromResponse(int status, Map<String, Object> body, int retryAfter) {
        String message = "Request failed (" + status + ")";
        if (body != null) {
            Object msg = body.get("message");
            if (msg != null) {
                message = String.valueOf(msg);
            }
        }

        String code = "http_" + status;
        if (body != null) {
            Object errorCode = body.get("error_code");
            if (errorCode != null) {
                code = String.valueOf(errorCode);
            } else {
                Object c = body.get("code");
                if (c != null) {
                    code = String.valueOf(c);
                }
            }
        }

        Map<String, String> firstErrors = extractFieldErrors(body);
        Map<String, List<String>> fieldErrors = extractAllFieldErrors(body);

        return switch (status) {
            case 401 -> new AuthenticationException(message, code, status, firstErrors, fieldErrors, body);
            case 403 -> new PermissionException(message, code, status, firstErrors, fieldErrors, body);
            case 404 -> new NotFoundException(message, code, status, firstErrors, fieldErrors, body);
            case 429 -> new RateLimitException(message, code, status, firstErrors, fieldErrors, body, retryAfter);
            case 400, 422 -> new InvalidRequestException(message, code, status, firstErrors, fieldErrors, body);
            default -> new WajubException(message, code, status, firstErrors, fieldErrors, body);
        };
    }

    /** Every message of each field, in the API's order; a field given as one string is a one-item list. */
    private static Map<String, List<String>> extractAllFieldErrors(Map<String, Object> body) {
        if (body == null || !(body.get("errors") instanceof Map<?, ?> errorMap)) {
            return Map.of();
        }
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : errorMap.entrySet()) {
            Object value = entry.getValue();
            List<String> messages = new ArrayList<>();
            // A field given [] or null has no message, and keeps its place as in getErrors().
            if (value instanceof List<?> list) {
                list.stream().filter(Objects::nonNull).forEach(message -> messages.add(String.valueOf(message)));
            } else if (value != null) {
                messages.add(String.valueOf(value));
            }
            fieldErrors.put(String.valueOf(entry.getKey()), messages);
        }
        return fieldErrors;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> extractFieldErrors(Map<String, Object> body) {
        if (body == null) {
            return Map.of();
        }
        Object rawErrors = body.get("errors");
        if (!(rawErrors instanceof Map<?, ?> errorMap)) {
            return Map.of();
        }
        Map<String, String> fieldErrors = new HashMap<>();
        for (Map.Entry<?, ?> entry : errorMap.entrySet()) {
            String field = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof List<?> list && !list.isEmpty()) {
                fieldErrors.put(field, String.valueOf(list.get(0)));
            } else {
                fieldErrors.put(field, String.valueOf(value));
            }
        }
        return fieldErrors;
    }
}
