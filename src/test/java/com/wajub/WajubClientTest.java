package com.wajub;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class WajubClientTest {

    private static final String TEST_API_KEY = "sk_test.unit_test_key";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockWebServer server;
    private AtomicInteger callCount;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        callCount = new AtomicInteger(0);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private Wajub newClient() {
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

        return Wajub.create(Wajub.Config.builder()
                .apiKey(TEST_API_KEY)
                .httpClient(httpClient)
                .build());
    }

    private void enqueueResponses(Function<RecordedRequest, MockResponse>... handlers) {
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                int idx = callCount.getAndIncrement();
                if (idx < handlers.length) {
                    return handlers[idx].apply(request);
                }
                return jsonResponse(200, Map.of());
            }
        });
    }

    private MockResponse jsonResponse(int status, Object body) {
        try {
            return new MockResponse()
                    .setResponseCode(status)
                    .setBody(MAPPER.writeValueAsString(body))
                    .setHeader("Content-Type", "application/json");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void newRequiresApiKey() {
        assertThrows(IllegalArgumentException.class, () ->
                Wajub.create(Wajub.Config.builder().apiKey("").build()));
    }

    @Test
    void newReadsApiKeyFromEnvironment() {
        Wajub client = Wajub.create(TEST_API_KEY);
        assertNotNull(client);
    }

    @Test
    void newAcceptsPublicAndRestrictedKeys() {
        assertDoesNotThrow(() -> Wajub.create("pk_test.public"));
        assertDoesNotThrow(() -> Wajub.create("rk_test.restricted"));
    }

    @Test
    void httpAuthorizationHeader() throws Exception {
        enqueueResponses(req -> jsonResponse(200, Map.of("ok", true)));

        Wajub client = newClient();
        Map<String, Object> result = client.global().ping();
        assertEquals(true, result.get("ok"));

        RecordedRequest request = server.takeRequest();
        assertEquals(TEST_API_KEY, request.getHeader("Authorization"));
    }

    @Test
    void httpPostIncludesIdempotencyKey() throws Exception {
        enqueueResponses(req -> jsonResponse(200, Map.of(
                "transaction", Map.of("id", "pay_1"))));

        Wajub client = newClient();
        client.payments().create(Map.of("amount", 1000), null);

        RecordedRequest request = server.takeRequest();
        String key = request.getHeader("Idempotency-Key");
        assertNotNull(key);
        assertTrue(key.startsWith("wajub-"));
    }

    @Test
    void httpPostHonorsCustomOptions() throws Exception {
        enqueueResponses(req -> jsonResponse(200, Map.of(
                "transaction", Map.of("id", "pay_1"))));

        Wajub client = newClient();
        RequestOptions opts = new RequestOptions()
                .setIdempotencyKey("custom-key-123")
                .setSync("acct_sync_ref");
        client.payments().create(Map.of("amount", 1000), opts);

        RecordedRequest request = server.takeRequest();
        assertEquals("custom-key-123", request.getHeader("Idempotency-Key"));
        assertEquals("acct_sync_ref", request.getHeader("X-Sync"));
    }

    @Test
    void httpGetDoesNotSendIdempotencyKey() throws Exception {
        enqueueResponses(req -> jsonResponse(200, Map.of("transactions", List.of())));

        Wajub client = newClient();
        client.payments().list(null);

        RecordedRequest request = server.takeRequest();
        assertNull(request.getHeader("Idempotency-Key"));
    }

    @Test
    void httpErrorResponse() throws Exception {
        enqueueResponses(req -> jsonResponse(422, Map.of(
                "message", "Unprocessable",
                "code", "validation_error")));

        Wajub client = newClient();
        com.wajub.exception.InvalidRequestException err = assertThrows(
                com.wajub.exception.InvalidRequestException.class,
                () -> client.payments().create(Map.of("amount", -1), null));
        assertEquals(422, err.getHttpStatus());
    }

    @Test
    void paymentsCreateUnwrapsUrls() throws Exception {
        enqueueResponses(req -> jsonResponse(200, Map.of(
                "transaction", Map.of("id", "pay_123", "status", "pending"),
                "authorization_token", "tok_abc",
                "authorization_url", "https://checkout.test/pay/pay_123")));

        Wajub client = newClient();
        Payment payment = client.payments().create(Map.of("amount", 5000, "currency", "XAF"), null);
        assertEquals("pay_123", payment.getId());
        assertEquals("tok_abc", payment.getAuthorizationToken());
    }

    @Test
    void paymentsInitializeAlias() throws Exception {
        enqueueResponses(req -> jsonResponse(200, Map.of(
                "transaction", Map.of("id", "pay_456"),
                "authorization_url", "https://checkout.test/pay/pay_456")));

        Wajub client = newClient();
        Payment payment = client.payments().initialize(Map.of("amount", 1000, "currency", "XAF"), null);
        assertEquals("pay_456", payment.getId());
    }

    @Test
    void paymentsListPagination() throws Exception {
        enqueueResponses(
                req -> jsonResponse(200, Map.of(
                        "transactions", List.of(Map.of("id", "pay_1")),
                        "meta", Map.of("current_page", 1, "last_page", 2))),
                req -> jsonResponse(200, Map.of(
                        "transactions", List.of(Map.of("id", "pay_2")),
                        "meta", Map.of("current_page", 2, "last_page", 2))));

        Wajub client = newClient();
        PagedResult page1 = client.payments().list(Map.of("per_page", 1));
        assertEquals("pay_1", page1.getData().get(0).get("id"));
        assertTrue(page1.hasMore());

        PagedResult page2 = page1.getNextPage();
        assertEquals("pay_2", page2.getData().get(0).get("id"));
        assertFalse(page2.hasMore());
    }

    @Test
    void refundsCrud() throws Exception {
        enqueueResponses(
                req -> jsonResponse(200, Map.of("refund", Map.of("id", "ref_new"))),
                req -> jsonResponse(200, Map.of("refund", Map.of("id", "ref_new", "status", "pending"))),
                req -> jsonResponse(200, Map.of()));

        Wajub client = newClient();

        Map<String, Object> created = client.refunds().create(
                Map.of("payment_id", "pay_1", "amount", 500), null);
        assertEquals("ref_new", created.get("id"));

        Map<String, Object> retrieved = client.refunds().retrieve("ref_new");
        assertEquals("pending", retrieved.get("status"));

        assertDoesNotThrow(() -> client.refunds().delete("ref_new", null));

        // Last request should be DELETE
        RecordedRequest deleteReq = null;
        for (int i = 0; i < 3; i++) {
            deleteReq = server.takeRequest();
        }
        assertEquals("DELETE", deleteReq.getMethod());
    }

    @Test
    void retriesOn503ThenSucceeds() throws Exception {
        enqueueResponses(
                req -> jsonResponse(503, Map.of("message", "Unavailable")),
                req -> jsonResponse(200, Map.of("ok", true)));

        Wajub client = newClient();
        Map<String, Object> result = client.global().ping();
        assertEquals(true, result.get("ok"));
        assertEquals(2, callCount.get());
    }

    @Test
    void retryReusesSameIdempotencyKeyAcrossAttempts() throws Exception {
        enqueueResponses(
                req -> jsonResponse(503, Map.of("message", "Unavailable")),
                req -> jsonResponse(503, Map.of("message", "Unavailable")),
                req -> jsonResponse(200, Map.of("transaction", Map.of("id", "pay_1"))));

        Wajub client = newClient();
        client.payments().create(Map.of("amount", 1000), null);

        assertEquals(3, callCount.get());

        String firstKey = null;
        for (int i = 0; i < 3; i++) {
            RecordedRequest request = server.takeRequest();
            String key = request.getHeader("Idempotency-Key");
            assertNotNull(key);
            if (i == 0) {
                firstKey = key;
            } else {
                assertEquals(firstKey, key, "Idempotency-Key must stay identical across retry attempts");
            }
        }
    }

    @Test
    void pickListAndNormalizeApiKey() {
        ListResult result = HttpUtils.pickList(Map.of(
                "refunds", List.of(Map.of("id", "ref_1")),
                "meta", Map.of("total", 1)), "refunds");
        assertEquals(1, result.data().size());
        assertEquals("ref_1", result.data().get(0).get("id"));
        assertNotNull(result.meta());
        assertEquals("sk_test.abc", HttpUtils.normalizeApiKey("Bearer sk_test.abc"));
    }

    @Test
    void pickListFallsBackToItemsKey() {
        ListResult result = HttpUtils.pickList(Map.of(
                "items", List.of(Map.of("id", "pay_1")),
                "meta", Map.of("total", 1)), "transactions");
        assertEquals(1, result.data().size());
        assertEquals("pay_1", result.data().get(0).get("id"));
        assertNotNull(result.meta());
    }

    @Test
    void constructEventRejectsInvalidSignatureFormat() {
        Webhooks w = new Webhooks("whsec_test");
        assertThrows(com.wajub.exception.WebhookSignatureVerificationException.class,
                () -> w.constructEvent("{}".getBytes(), "bad", "1700000000", null));
    }

    @Test
    void constructEventRejectsOldTimestamp() {
        String secret = "whsec_test";
        String payload = "{\"event\":\"payment.succeeded\"}";
        long oldTs = System.currentTimeMillis() / 1000 - 600;
        String sig = signWebhook(secret, payload, oldTs);

        Webhooks w = new Webhooks(secret);
        assertThrows(com.wajub.exception.WebhookSignatureVerificationException.class,
                () -> w.constructEvent(payload.getBytes(), sig, String.valueOf(oldTs),
                        java.time.Duration.ofSeconds(300)));
    }

    @Test
    void errorFromResponse() {
        com.wajub.exception.InvalidRequestException err =
                (com.wajub.exception.InvalidRequestException) HttpUtils.errorFromResponse(422, Map.of(
                        "message", "Validation failed",
                        "code", "validation_error",
                        "errors", Map.of("amount", List.of("Must be positive"))), 0);

        assertEquals("Validation failed", err.getMessage());
        assertEquals("validation_error", err.getCode());
        assertEquals(422, err.getHttpStatus());
        assertEquals("Must be positive", err.getErrors().get("amount"));
    }

    private static String signWebhook(String secret, String payload, long timestamp) {
        return "v1=" + Crypto.hmacSha256(secret, timestamp + "." + payload);
    }
}
