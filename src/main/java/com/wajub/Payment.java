package com.wajub;

import java.util.Map;

/** Represents a Wajub payment (transaction). */
public class Payment {

    private final String id;
    private final String status;
    private final long amount;
    private final String currency;
    private final String authorizationUrl;
    private final String authorizationToken;
    private final Map<String, Object> raw;

    Payment(String id, String status, long amount, String currency,
            String authorizationUrl, String authorizationToken, Map<String, Object> raw) {
        this.id = id;
        this.status = status;
        this.amount = amount;
        this.currency = currency;
        this.authorizationUrl = authorizationUrl;
        this.authorizationToken = authorizationToken;
        this.raw = raw != null ? Map.copyOf(raw) : Map.of();
    }

    public String getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getAuthorizationUrl() {
        return authorizationUrl;
    }

    public String getAuthorizationToken() {
        return authorizationToken;
    }

    public Map<String, Object> getRaw() {
        return raw;
    }

    static Payment parsePayment(Map<String, Object> body) {
        Map<String, Object> transaction = HttpUtils.pickResource(body, "transaction");

        String id = stringValue(transaction.get("id"));
        String status = stringValue(transaction.get("status"));
        long amount = numberValue(transaction.get("amount"));
        String currency = stringValue(transaction.get("currency"));

        Object token = body.get("authorization_token");
        if (token == null) {
            token = transaction.get("authorization_token");
        }
        if (token == null) {
            token = transaction.get("id");
        }

        Object url = body.get("authorization_url");
        if (url == null) {
            url = transaction.get("authorization_url");
        }

        return new Payment(
                id,
                status,
                amount,
                currency,
                stringValue(url),
                stringValue(token),
                transaction);
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private static long numberValue(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }
}
