package com.wajub;

import com.wajub.exception.ApiConnectionException;
import com.wajub.exception.WajubException;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

class BaseClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String apiKey;
    private final String baseUrl;
    private final String idempotencyKeyPrefix;
    private final OkHttpClient httpClient;

    BaseClient(String apiKey, String baseUrl, String idempotencyKeyPrefix, OkHttpClient httpClient) {
        this.apiKey = HttpUtils.normalizeApiKey(apiKey);
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.idempotencyKeyPrefix = (idempotencyKeyPrefix == null || idempotencyKeyPrefix.isBlank())
                ? "wajub" : idempotencyKeyPrefix;
        this.httpClient = httpClient != null ? httpClient : defaultHttpClient();
    }

    private static OkHttpClient defaultHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    Map<String, Object> get(String path, Map<String, Object> params) throws WajubException {
        return request("GET", path, null, params, null);
    }

    Map<String, Object> post(String path, Map<String, Object> body, RequestOptions opts) throws WajubException {
        return request("POST", path, body, null, opts);
    }

    Map<String, Object> put(String path, Map<String, Object> body, RequestOptions opts) throws WajubException {
        return request("PUT", path, body, null, opts);
    }

    Map<String, Object> delete(String path, RequestOptions opts) throws WajubException {
        return request("DELETE", path, null, null, opts);
    }

    private Map<String, Object> request(
            String method,
            String path,
            Map<String, Object> body,
            Map<String, Object> query,
            RequestOptions opts) throws WajubException {

        String endpoint = buildEndpoint(path, query);
        String payload = body != null ? HttpUtils.encodeJson(body) : null;

        String idempotencyKey = opts != null ? opts.getIdempotencyKey() : null;
        if ((idempotencyKey == null || idempotencyKey.isBlank())
                && !"GET".equals(method) && !"DELETE".equals(method)) {
            idempotencyKey = HttpUtils.createIdempotencyKey(idempotencyKeyPrefix);
        }

        int attempt = 0;

        while (true) {
            Request.Builder builder = new Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/json")
                    .header("Authorization", apiKey)
                    .header("User-Agent", "wajub-java/" + Version.VERSION);

            if (opts != null) {
                if (opts.getHeaders() != null) {
                    opts.getHeaders().forEach(builder::header);
                }
                if (opts.getSync() != null && !opts.getSync().isBlank()) {
                    builder.header("X-Sync", opts.getSync());
                }
            }

            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                builder.header("Idempotency-Key", idempotencyKey);
            }

            if (payload != null) {
                builder.method(method, RequestBody.create(payload, JSON));
            } else {
                builder.method(method, "GET".equals(method) || "DELETE".equals(method)
                        ? null : RequestBody.create("", JSON));
            }

            try (Response response = httpClient.newCall(builder.build()).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                Map<String, Object> parsed = HttpUtils.decodeJson(responseBody);
                int status = response.code();

                if (status >= 200 && status < 300) {
                    return parsed;
                }

                if (Retry.shouldRetryStatus(status) && attempt < Retry.MAX_RETRIES) {
                    sleep(Retry.retryDelay(attempt + 1, response.headers()));
                    attempt++;
                    continue;
                }

                int retryAfter = Retry.parseRetryAfterSeconds(response.headers());
                throw HttpUtils.errorFromResponse(status, parsed, retryAfter);

            } catch (IOException e) {
                if (attempt < Retry.MAX_RETRIES) {
                    sleep(Retry.retryDelay(attempt + 1, null));
                    attempt++;
                    continue;
                }
                throw new ApiConnectionException(e.getMessage());
            }
        }
    }

    private String buildEndpoint(String path, Map<String, Object> query) {
        HttpUrl.Builder builder = HttpUrl.parse(baseUrl + path).newBuilder();
        if (query != null) {
            for (Map.Entry<String, Object> entry : query.entrySet()) {
                if (entry.getValue() != null) {
                    builder.addQueryParameter(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
        }
        return builder.build().toString();
    }

    private static void sleep(java.time.Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    Map<String, Object> copyParams(Map<String, Object> params) {
        return params != null ? new HashMap<>(params) : new HashMap<>();
    }
}
