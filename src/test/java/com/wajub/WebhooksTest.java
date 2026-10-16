package com.wajub;

import com.wajub.exception.WebhookSignatureVerificationException;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebhooksTest {

    @Test
    void constructEventVerifiesSignature() throws WebhookSignatureVerificationException {
        String secret = "whsec_test_secret";
        byte[] payload = "{\"event\":\"payment.succeeded\",\"data\":{}}".getBytes();
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = "v1=" + Crypto.hmacSha256(secret, timestamp + "." + new String(payload));

        Webhooks w = new Webhooks(secret);
        Map<String, Object> event = w.constructEvent(payload, signature, timestamp, null);
        assertEquals("payment.succeeded", event.get("event"));
    }

    @Test
    void constructEventRejectsWrongSignature() {
        Webhooks w = new Webhooks("whsec_test");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        assertThrows(WebhookSignatureVerificationException.class,
                () -> w.constructEvent("{}".getBytes(), "v1=deadbeef", timestamp, null));
    }

    @Test
    void constructEventAnswersAMissingHeaderWithASignatureError() {
        Webhooks w = new Webhooks("whsec_test");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        assertThrows(WebhookSignatureVerificationException.class,
                () -> w.constructEvent("{}".getBytes(), null, timestamp, null));
        assertThrows(WebhookSignatureVerificationException.class,
                () -> w.constructEvent("{}".getBytes(), "v1=deadbeef", null, null));
    }

    @Test
    void pickResource() {
        Map<String, Object> body = Map.of(
                "transaction", Map.of("id", "pay_123"));
        Map<String, Object> resource = HttpUtils.pickResource(body, "transaction");
        assertEquals("pay_123", resource.get("id"));
    }

    @Test
    void autoPagingCollectsAllPages() throws Exception {
        okhttp3.mockwebserver.MockWebServer server = new okhttp3.mockwebserver.MockWebServer();
        server.start();
        try {
            server.enqueue(new okhttp3.mockwebserver.MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"transactions\":[{\"id\":\"pay_1\"}],\"meta\":{\"current_page\":1,\"last_page\":2}}"));
            server.enqueue(new okhttp3.mockwebserver.MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"transactions\":[{\"id\":\"pay_2\"}],\"meta\":{\"current_page\":2,\"last_page\":2}}"));

            HttpUrl mockBase = server.url("/");
            OkHttpClient httpClient = new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        Request request = chain.request();
                        HttpUrl redirected = request.url().newBuilder()
                                .scheme(mockBase.scheme())
                                .host(mockBase.host())
                                .port(mockBase.port())
                                .build();
                        return chain.proceed(request.newBuilder().url(redirected).build());
                    })
                    .build();

            Wajub client = Wajub.create(Wajub.Config.builder()
                    .apiKey("sk_test.key")
                    .httpClient(httpClient)
                    .build());

            PagedResult page1 = client.payments().list(Map.of("per_page", 1));
            var all = page1.autoPaging();
            assertEquals(2, all.size());
            assertEquals("pay_1", all.get(0).get("id"));
            assertEquals("pay_2", all.get(1).get("id"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    void constructEventUsesDefaultTolerance() throws WebhookSignatureVerificationException {
        String secret = "whsec_tolerance";
        String payload = "{\"event\":\"test\"}";
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = "v1=" + Crypto.hmacSha256(secret, timestamp + "." + payload);

        Webhooks w = new Webhooks(secret);
        Map<String, Object> event = w.constructEvent(payload.getBytes(), signature, timestamp, Duration.ZERO);
        assertEquals("test", event.get("event"));
    }

    @Test
    void constructEventAcceptsAFreshDeliveryWithANegativeTolerance() throws WebhookSignatureVerificationException {
        String secret = "whsec_tolerance";
        String payload = "{\"event\":\"test\"}";
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = "v1=" + Crypto.hmacSha256(secret, timestamp + "." + payload);

        Webhooks w = new Webhooks(secret);
        Map<String, Object> event = w.constructEvent(
                payload.getBytes(), signature, timestamp, Duration.ofSeconds(-60));
        assertEquals("test", event.get("event"));
    }

    @Test
    void constructEventKeepsTheDefaultWindowForAZeroOrNegativeTolerance() {
        String secret = "whsec_tolerance";
        String payload = "{\"event\":\"test\"}";
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000 - 600);
        String signature = "v1=" + Crypto.hmacSha256(secret, timestamp + "." + payload);

        Webhooks w = new Webhooks(secret);
        for (Duration tolerance : new Duration[] {null, Duration.ZERO, Duration.ofSeconds(-1)}) {
            WebhookSignatureVerificationException e = assertThrows(
                    WebhookSignatureVerificationException.class,
                    () -> w.constructEvent(payload.getBytes(), signature, timestamp, tolerance),
                    "tolerance " + tolerance);
            assertTrue(e.getMessage().contains("allowed 300s"), e.getMessage());
        }
    }

    // The body was decoded with the JVM's default charset: where it is not
    // UTF-8 (the tests run that way, see pom.xml), a delivery with an accent
    // in it failed its signature check.
    @Test
    void constructEventReadsTheBodyAsUtf8() throws WebhookSignatureVerificationException {
        String secret = "whsec_utf8";
        String body = "{\"event\":\"payment.succeeded\",\"data\":{\"customer\":{\"name\":\"Éloïse Ndé\"},\"country\":\"Côte d’Ivoire\"}}";
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = "v1=" + Crypto.hmacSha256(secret, timestamp + "." + body);

        Map<String, Object> event = new Webhooks(secret).constructEvent(payload, signature, timestamp, null);

        assertEquals("Côte d’Ivoire", ((Map<?, ?>) event.get("data")).get("country"));
        assertEquals("Éloïse Ndé", ((Map<?, ?>) ((Map<?, ?>) event.get("data")).get("customer")).get("name"));
    }

    @Test
    void constructEventRefusesANullPayloadWithASignatureError() {
        Webhooks w = new Webhooks("whsec_test");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        WebhookSignatureVerificationException e = assertThrows(WebhookSignatureVerificationException.class,
                () -> w.constructEvent(null, "v1=deadbeef", timestamp, null));
        assertEquals("Webhook payload is required for constructEvent.", e.getMessage());
    }

    @Test
    void constructEventNamesAMissingSignatureHeader() {
        Webhooks w = new Webhooks("whsec_test");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        for (String signature : new String[] {null, "", " "}) {
            WebhookSignatureVerificationException e = assertThrows(
                    WebhookSignatureVerificationException.class,
                    () -> w.constructEvent("{}".getBytes(StandardCharsets.UTF_8), signature, timestamp, null));
            assertEquals("Missing X-Wajub-Signature or X-Wajub-Timestamp header.", e.getMessage(),
                    "signature " + signature);
        }
    }

    // Timestamps are whole seconds. A tolerance under a second was cut to 0 s, so
    // a delivery was refused once the clock turned to the next second; counted to
    // the millisecond, it was refused once the timestamp's second was 500 ms old.
    // Rounded up to whole seconds, 500 ms is one second.
    @Test
    void constructEventRoundsATolerancePartOfASecondUpToWholeSeconds() {
        long now = 1_760_000_000L;
        Webhooks w = new Webhooks("whsec_tolerance",
                Clock.fixed(Instant.ofEpochSecond(now, 750_000_000L), ZoneOffset.UTC));
        Duration halfASecond = Duration.ofMillis(500);

        assertAccepted(w, now, halfASecond);
        assertAccepted(w, now - 1, halfASecond);
        assertAccepted(w, now + 1, halfASecond);
        assertRefused(w, now - 2, halfASecond, "Timestamp outside tolerance zone (2s drift, allowed 1s).");
        assertRefused(w, now + 2, halfASecond, "Timestamp outside tolerance zone (2s drift, allowed 1s).");

        Duration oneAndAHalf = Duration.ofMillis(1500);
        assertAccepted(w, now - 2, oneAndAHalf);
        assertRefused(w, now - 3, oneAndAHalf, "Timestamp outside tolerance zone (3s drift, allowed 2s).");

        assertAccepted(w, now - 300, null);
        assertRefused(w, now - 301, null, "Timestamp outside tolerance zone (301s drift, allowed 300s).");
    }

    private static void assertAccepted(Webhooks w, long timestamp, Duration tolerance) {
        String payload = "{\"event\":\"test\"}";
        String ts = String.valueOf(timestamp);
        String signature = "v1=" + Crypto.hmacSha256("whsec_tolerance", ts + "." + payload);
        assertEquals("test", w.constructEvent(payload.getBytes(StandardCharsets.UTF_8), signature, ts, tolerance)
                .get("event"), "timestamp " + timestamp + ", tolerance " + tolerance);
    }

    private static void assertRefused(Webhooks w, long timestamp, Duration tolerance, String message) {
        String payload = "{\"event\":\"test\"}";
        String ts = String.valueOf(timestamp);
        String signature = "v1=" + Crypto.hmacSha256("whsec_tolerance", ts + "." + payload);
        WebhookSignatureVerificationException e = assertThrows(WebhookSignatureVerificationException.class,
                () -> w.constructEvent(payload.getBytes(StandardCharsets.UTF_8), signature, ts, tolerance),
                "timestamp " + timestamp + ", tolerance " + tolerance);
        assertEquals(message, e.getMessage());
    }

    // The signature was checked over the body decoded as text: bytes that are not
    // valid UTF-8 all decode to U+FFFD, so bodies with different bytes shared a
    // signature.
    @Test
    void constructEventChecksTheSignatureOverTheBodysBytes() throws Exception {
        String secret = "whsec_bytes";
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        Webhooks w = new Webhooks(secret);

        // U+FFFD sent as such (EF BF BD), signed by Wajub, then the same body with
        // FF in its place: both decode to the same text.
        byte[] sent = "{\"event\":\"test\",\"note\":\"\uFFFD\"}".getBytes(StandardCharsets.UTF_8);
        byte[] forged = replace(sent, new byte[] {(byte) 0xEF, (byte) 0xBF, (byte) 0xBD}, new byte[] {(byte) 0xFF});
        assertEquals(new String(sent, StandardCharsets.UTF_8), new String(forged, StandardCharsets.UTF_8));

        String signature = signBytes(secret, timestamp, sent);
        assertEquals("test", w.constructEvent(sent, signature, timestamp, null).get("event"));
        WebhookSignatureVerificationException e = assertThrows(WebhookSignatureVerificationException.class,
                () -> w.constructEvent(forged, signature, timestamp, null));
        assertEquals("Webhook signature verification failed.", e.getMessage());

        // Two invalid sequences, FF and FE: the body signed with FF passes the
        // signature check (its JSON is then refused), the one with FE does not.
        byte[] withFf = "{\"event\":\"test\",\"note\":\"#\"}".getBytes(StandardCharsets.UTF_8);
        withFf = replace(withFf, new byte[] {'#'}, new byte[] {(byte) 0xFF});
        byte[] withFe = replace(withFf, new byte[] {(byte) 0xFF}, new byte[] {(byte) 0xFE});
        String ffSignature = signBytes(secret, timestamp, withFf);
        byte[] ff = withFf;
        assertEquals("Invalid webhook payload JSON.", assertThrows(WebhookSignatureVerificationException.class,
                () -> w.constructEvent(ff, ffSignature, timestamp, null)).getMessage());
        assertEquals("Webhook signature verification failed.", assertThrows(WebhookSignatureVerificationException.class,
                () -> w.constructEvent(withFe, ffSignature, timestamp, null)).getMessage());
    }

    /** Signs as Wajub does: HMAC-SHA256 of the timestamp, ".", and the body's bytes. */
    private static String signBytes(String secret, String timestamp, byte[] body) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
        return "v1=" + java.util.HexFormat.of().formatHex(mac.doFinal(body));
    }

    private static byte[] replace(byte[] in, byte[] from, byte[] to) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < in.length; ) {
            if (i + from.length <= in.length
                    && java.util.Arrays.equals(in, i, i + from.length, from, 0, from.length)) {
                out.writeBytes(to);
                i += from.length;
            } else {
                out.write(in[i++]);
            }
        }
        return out.toByteArray();
    }
}
