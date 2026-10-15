package com.wajub;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

/** Represents a Wajub payment (transaction). */
public class Payment {

    private final String id;
    private final String status;
    private final BigDecimal amount;
    private final String currency;
    private final String authorizationUrl;
    private final String authorizationToken;
    private final Map<String, Object> raw;

    Payment(String id, String status, BigDecimal amount, String currency,
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

    /**
     * The amount without its decimals: GHS 12.50 reads {@code 12}.
     *
     * @deprecated drops the decimals of every currency that has them. Use
     *     {@link #getAmountDecimal()}.
     */
    @Deprecated(since = "1.3.0")
    public long getAmount() {
        return amount.longValue();
    }

    /**
     * The amount in the currency's major unit, decimals included, as the API
     * sends it: {@code 15000} for XAF 15,000, {@code 12.5} for GHS 12.50.
     * Trailing zeros are dropped, so compare it with {@link BigDecimal#compareTo},
     * not {@code equals}. Never {@code null}: a payment without an amount reads
     * {@code 0}.
     */
    public BigDecimal getAmountDecimal() {
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
        BigDecimal amount = decimalValue(transaction.get("amount"));
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

    /**
     * An amount as the API sends it, a JSON number or a numeric string
     * ({@code "12.50"}), in the currency's major unit. A double is read through
     * its shortest decimal form, so {@code 12.1} stays {@code 12.1}. Anything
     * else, or no amount, reads {@code 0}.
     */
    static BigDecimal decimalValue(Object value) {
        BigDecimal decimal = null;
        try {
            if (value instanceof BigDecimal d) {
                decimal = d;
            } else if (value instanceof BigInteger i) {
                decimal = new BigDecimal(i);
            } else if (value instanceof Double || value instanceof Float) {
                double d = ((Number) value).doubleValue();
                if (Double.isFinite(d)) {
                    decimal = value instanceof Float f ? new BigDecimal(Float.toString(f)) : BigDecimal.valueOf(d);
                }
            } else if (value instanceof Number n) {
                decimal = BigDecimal.valueOf(n.longValue());
            } else if (value instanceof String s) {
                decimal = new BigDecimal(s.trim());
            }
        } catch (NumberFormatException ignored) {
            decimal = null;
        }
        if (decimal == null) {
            return BigDecimal.ZERO;
        }
        decimal = decimal.stripTrailingZeros();
        return decimal.scale() < 0 ? decimal.setScale(0) : decimal;
    }
}
