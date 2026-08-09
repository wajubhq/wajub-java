package com.wajub;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Configures a single API request. */
public class RequestOptions {

    private String idempotencyKey;
    private String sync;
    private Map<String, String> headers = new HashMap<>();

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public RequestOptions setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
        return this;
    }

    public String getSync() {
        return sync;
    }

    public RequestOptions setSync(String sync) {
        this.sync = sync;
        return this;
    }

    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    public RequestOptions setHeaders(Map<String, String> headers) {
        this.headers = new HashMap<>(headers);
        return this;
    }

    public RequestOptions addHeader(String key, String value) {
        this.headers.put(key, value);
        return this;
    }
}
