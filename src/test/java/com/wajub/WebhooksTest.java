package com.wajub;

import com.wajub.exception.WebhookSignatureVerificationException;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

    // A tolerance under a second was cut to 0 s: a delivery was then accepted
    // up to 999 ms late, as long as the clock had not turned to the next second.
    @Test
    void constructEventKeepsASubSecondTolerance() throws Exception {
        String secret = "whsec_tolerance";
        String payload = "{\"event\":\"test\"}";
        Webhooks w = new Webhooks(secret);

        // 700 to 800 ms into the second: a timestamp of this second is 700 ms old or more.
        long now = waitUntilMillisIntoTheSecond(700, 800);
        String timestamp = String.valueOf(now / 1000);
        String signature = "v1=" + Crypto.hmacSha256(secret, timestamp + "." + payload);
        WebhookSignatureVerificationException e = assertThrows(
                WebhookSignatureVerificationException.class,
                () -> w.constructEvent(payload.getBytes(StandardCharsets.UTF_8), signature, timestamp,
                        Duration.ofMillis(500)));
        assertTrue(e.getMessage().contains("allowed 0.5s"), e.getMessage());

        // The same delivery passes a 1.5 s tolerance.
        assertEquals("test", w.constructEvent(payload.getBytes(StandardCharsets.UTF_8), signature, timestamp,
                Duration.ofMillis(1500)).get("event"));
    }

    private static long waitUntilMillisIntoTheSecond(int from, int to) throws InterruptedException {
        while (true) {
            long now = System.currentTimeMillis();
            long into = now % 1000;
            if (into >= from && into < to) {
                return now;
            }
            Thread.sleep(into < from ? from - into : 1000 - into + from);
        }
    }
}
