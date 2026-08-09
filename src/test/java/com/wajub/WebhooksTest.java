package com.wajub;

import com.wajub.exception.WebhookSignatureVerificationException;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.Test;

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
}
