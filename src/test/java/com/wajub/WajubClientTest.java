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
import java.math.BigDecimal;
import java.math.BigInteger;
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

    // getErrors() keeps the first message of each field only: a field the API
    // answers with two messages lost the second.
    @Test
    void invalidRequestKeepsEveryMessageOfAField() throws Exception {
        enqueueResponses(req -> new MockResponse()
                .setResponseCode(422)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Validation failed\",\"code\":\"validation_error\",\"errors\":{"
                        + "\"amount\":[\"The amount must be at least 1.\",\"The amount must be a number.\"],"
                        + "\"currency\":[\"The currency is required.\"],"
                        + "\"email\":\"The email is invalid.\"}}"));

        Wajub client = newClient();
        com.wajub.exception.InvalidRequestException err = assertThrows(
                com.wajub.exception.InvalidRequestException.class,
                () -> client.payments().create(Map.of("amount", -1), null));

        assertEquals(List.of("The amount must be at least 1.", "The amount must be a number."),
                err.getFieldErrors().get("amount"));
        assertEquals(List.of("The currency is required."), err.getFieldErrors().get("currency"));
        assertEquals(List.of("The email is invalid."), err.getFieldErrors().get("email"));
        assertEquals(List.of("amount", "currency", "email"), List.copyOf(err.getFieldErrors().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> err.getFieldErrors().get("amount").add("x"));
        assertThrows(UnsupportedOperationException.class, () -> err.getFieldErrors().remove("amount"));

        // Unchanged: the first message of each field.
        assertEquals("The amount must be at least 1.", err.getErrors().get("amount"));
        assertEquals("The currency is required.", err.getErrors().get("currency"));
        assertEquals("The email is invalid.", err.getErrors().get("email"));
    }

    @Test
    void anExceptionBuiltWithFirstMessagesListsThemAsFieldErrors() {
        com.wajub.exception.InvalidRequestException err = new com.wajub.exception.InvalidRequestException(
                "Validation failed", "validation_error", 422, Map.of("amount", "Must be positive"), null);

        assertEquals(Map.of("amount", List.of("Must be positive")), err.getFieldErrors());
        assertEquals(Map.of(), new com.wajub.exception.ApiConnectionException("offline").getFieldErrors());
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
    void retriesA409ForAnIdempotencyKeyStillBeingProcessed() throws Exception {
        // A call sent again after a timeout can reach the API while the first
        // one is still running: the answer is on its way, so ask again.
        String message = "A request with this Idempotency-Key is still being processed.";
        enqueueResponses(
                req -> jsonResponse(409, Map.of(
                        "message", message,
                        "errors", Map.of("idempotency_key", List.of(message)))),
                req -> jsonResponse(201, Map.of("transaction", Map.of("id", "trx_1"))));

        Wajub client = newClient();
        client.payments().create(Map.of("amount", 1000), new RequestOptions().setIdempotencyKey("ORDER-42"));

        assertEquals(2, callCount.get());
        assertEquals("ORDER-42", server.takeRequest().getHeader("Idempotency-Key"));
        assertEquals("ORDER-42", server.takeRequest().getHeader("Idempotency-Key"));
        assertEquals(java.time.Duration.ofSeconds(2), Retry.idempotencyConflictDelay(1));
        assertEquals(java.time.Duration.ofSeconds(4), Retry.idempotencyConflictDelay(2));
    }

    // The API answers 410 Gone on /listen/config and /listen/auth: integrators
    // forward webhooks with the Wajub CLI's `wajub listen`.
    @Test
    void listenIsDeprecated() throws Exception {
        for (java.lang.reflect.Method method : new java.lang.reflect.Method[] {
                Wajub.class.getMethod("listen"),
                ListenResource.class.getMethod("config"),
                ListenResource.class.getMethod("auth", Map.class, RequestOptions.class)}) {
            assertTrue(method.isAnnotationPresent(Deprecated.class), method.toString());
            assertEquals("1.3.0", method.getAnnotation(Deprecated.class).since(), method.toString());
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void listenSendsTheSameRequestsAsBefore() throws Exception {
        enqueueResponses(
                req -> jsonResponse(200, Map.of("config", Map.of("url", "wss://example.test"))),
                req -> jsonResponse(200, Map.of("auth", Map.of("token", "tok_1"))));

        Wajub client = newClient();
        assertEquals("wss://example.test", client.listen().config().get("url"));
        assertEquals("tok_1", client.listen().auth(Map.of("channel", "c"), null).get("token"));

        RecordedRequest config = server.takeRequest();
        assertEquals("GET", config.getMethod());
        assertEquals("/listen/config", config.getRequestUrl().encodedPath().replaceFirst("^/v1", ""));
        RecordedRequest auth = server.takeRequest();
        assertEquals("POST", auth.getMethod());
        assertEquals("/listen/auth", auth.getRequestUrl().encodedPath().replaceFirst("^/v1", ""));
        assertEquals(Map.of("channel", "c"), MAPPER.readValue(auth.getBody().readUtf8(), Map.class));
    }

    @Test
    void raisesAnyOther409AtOnce() {
        enqueueResponses(
                req -> jsonResponse(409, Map.of(
                        "error_code", "payment_in_progress",
                        "message", "A charge is already in progress.")),
                req -> jsonResponse(200, Map.of("ok", true)));

        Wajub client = newClient();
        assertThrows(com.wajub.exception.WajubException.class,
                () -> client.payments().process("trx_1", Map.of("channel", "cm.mtn"), null));
        assertEquals(1, callCount.get());
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

    @Test
    @SuppressWarnings("deprecation")
    void paymentAmountKeepsItsDecimals() throws Exception {
        // The amount as the API sends it, the currency, getAmountDecimal(), getAmount().
        String[][] cases = {
                {"15000", "XAF", "15000", "15000"},
                {"12.5", "GHS", "12.5", "12"},
                {"\"12.50\"", "GHS", "12.5", "12"},
                {"12.1", "GHS", "12.1", "12"},
                {"0.01", "USD", "0.01", "0"},
                {"1.234", "KWD", "1.234", "1"},
                {"9876543210.99", "NGN", "9876543210.99", "9876543210"},
        };

        Wajub client = newClient();
        for (String[] c : cases) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"transaction\":{\"id\":\"pay_1\",\"amount\":" + c[0]
                            + ",\"currency\":\"" + c[1] + "\"}}"));

            Payment payment = client.payments().retrieve("pay_1");
            assertEquals(c[2], payment.getAmountDecimal().toPlainString(), c[0]);
            assertEquals(Long.parseLong(c[3]), payment.getAmount(), c[0]);
        }
    }

    @Test
    void decimalValueReadsEveryFormAnAmountTakes() {
        assertEquals("12.5", Payment.decimalValue("12.50").toPlainString());
        assertEquals("7.25", Payment.decimalValue(" 7.25 ").toPlainString());
        assertEquals("12.1", Payment.decimalValue(12.1f).toPlainString());
        assertEquals("15000", Payment.decimalValue(15000).toPlainString());
        assertEquals("15000", Payment.decimalValue(15000L).toPlainString());
        assertEquals("15000", Payment.decimalValue(15000.0).toPlainString());
        assertEquals("1000", Payment.decimalValue("1E+3").toPlainString());
        assertEquals("12345678901234567890", Payment.decimalValue(new BigInteger("12345678901234567890")).toPlainString());
        assertEquals(0, BigDecimal.ZERO.compareTo(Payment.decimalValue(null)));
        assertEquals(0, BigDecimal.ZERO.compareTo(Payment.decimalValue("not a number")));
        assertEquals(0, BigDecimal.ZERO.compareTo(Payment.decimalValue(Double.NaN)));
    }

    @Test
    void amountIsSentWithItsDecimals() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"transaction\":{\"id\":\"pay_1\",\"amount\":12.1,\"currency\":\"GHS\"}}"));
        server.enqueue(jsonResponse(200, Map.of("refund", Map.of("id", "ref_1"))));
        server.enqueue(jsonResponse(200, Map.of("transaction", Map.of("id", "pay_2"))));

        Wajub client = newClient();
        Payment payment = client.payments().create(Map.of(
                "amount", new BigDecimal("12.10"), "currency", "GHS", "email", "buyer@example.com"), null);
        // The amount read back goes out again unchanged.
        client.refunds().create(Map.of("payment_id", payment.getId(), "amount", payment.getAmountDecimal()), null);
        client.payments().create(Map.of(
                "amount", new BigDecimal("1.234"), "currency", "KWD", "email", "buyer@example.com"), null);

        // The body as written, digit for digit.
        for (String want : new String[] {"\"amount\":12.10", "\"amount\":12.1", "\"amount\":1.234"}) {
            String body = server.takeRequest().getBody().readUtf8();
            assertTrue(body.contains(want + ",") || body.contains(want + "}"), body);
        }
    }

    private interface IdCall {
        void call(String id) throws Exception;
    }

    private static Map<String, IdCall> idPathMethods(Wajub client) {
        Map<String, Object> p = Map.of();
        Map<String, IdCall> calls = new java.util.LinkedHashMap<>();
        calls.put("payments().retrieve", id -> client.payments().retrieve(id));
        calls.put("payments().cancel", id -> client.payments().cancel(id, null));
        calls.put("payments().process", id -> client.payments().process(id, p, null));
        calls.put("payments().processSplit", id -> client.payments().processSplit(id, p, null));
        calls.put("payments().listRefunds", id -> client.payments().listRefunds(id, p));
        for (Map.Entry<String, CrudResource> crud : Map.<String, CrudResource>of(
                "customers()", client.customers(), "refunds()", client.refunds(),
                "transfers()", client.transfers(), "beneficiaries()", client.beneficiaries(),
                "links()", client.links(), "accounts()", client.accounts(),
                "webhookEndpoints()", client.webhookEndpoints(), "invoices()", client.invoices()).entrySet()) {
            CrudResource r = crud.getValue();
            calls.put(crud.getKey() + ".retrieve", id -> r.retrieve(id));
            calls.put(crud.getKey() + ".update", id -> r.update(id, p, null));
            calls.put(crud.getKey() + ".delete", id -> r.delete(id, null));
        }
        calls.put("customers().block", id -> client.customers().block(id, p, null));
        calls.put("customers().unblock", id -> client.customers().unblock(id, null));
        calls.put("customers().activate", id -> client.customers().activate(id, null));
        calls.put("customers().deactivate", id -> client.customers().deactivate(id, null));
        calls.put("customers().listTaxIds", id -> client.customers().listTaxIds(id));
        calls.put("customers().createTaxId", id -> client.customers().createTaxId(id, p, null));
        calls.put("customers().deleteTaxId (customer)", id -> client.customers().deleteTaxId(id, "txi_1", null));
        calls.put("customers().deleteTaxId (tax id)", id -> client.customers().deleteTaxId("cus_1", id, null));
        calls.put("accounts().regenerateToken", id -> client.accounts().regenerateToken(id, null));
        calls.put("webhookEndpoints().rotateSecret", id -> client.webhookEndpoints().rotateSecret(id, null));
        calls.put("invoices().send", id -> client.invoices().send(id, null));
        calls.put("invoices().markPaid", id -> client.invoices().markPaid(id, p, null));
        calls.put("invoices().cancel", id -> client.invoices().cancel(id, null));
        calls.put("events().retrieve", id -> client.events().retrieve(id));
        calls.put("events().resend", id -> client.events().resend(id, null));
        calls.put("disputes().retrieve", id -> client.disputes().retrieve(id));
        calls.put("disputes().submitEvidence", id -> client.disputes().submitEvidence(id, p, null));
        calls.put("disputes().accept", id -> client.disputes().accept(id, null));
        calls.put("disputes().close", id -> client.disputes().close(id, null));
        calls.put("disputes().sendMessage", id -> client.disputes().sendMessage(id, p, null));
        calls.put("tax().retrieveCode", id -> client.tax().retrieveCode(id));
        calls.put("tax().retrieveRegistration", id -> client.tax().retrieveRegistration(id));
        calls.put("tax().updateRegistration", id -> client.tax().updateRegistration(id, p, null));
        calls.put("tax().deleteRegistration", id -> client.tax().deleteRegistration(id, null));
        calls.put("shield().removeFromBlocklist", id -> client.shield().removeFromBlocklist(id, null));
        return calls;
    }

    // An id is one segment of the path. With a "/" in it, or empty, "." or "..",
    // the request went to another route of the API.
    @Test
    void anIdThatWouldLeaveItsPathSegmentIsRefusedBeforeAnythingIsSent() {
        enqueueResponses();
        Wajub client = newClient();

        for (Map.Entry<String, IdCall> method : idPathMethods(client).entrySet()) {
            for (String id : new String[] {"pay_1/../../customers", "cus_1/block", "", ".", "..", null}) {
                IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                        () -> method.getValue().call(id), method.getKey() + " with " + id);
                assertTrue(e.getMessage().startsWith("wajub: invalid id"), e.getMessage());
            }
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void anIdIsSentAsOnePathSegment() throws Exception {
        enqueueResponses();
        Wajub client = newClient();

        client.payments().processSplit("pay_1#x?y=1", Map.of(), null);
        client.payments().retrieve("pay_1%2Fcancel");
        client.customers().deleteTaxId("cus_1", "txi 1\\x", null);

        assertEquals("/payments/pay_1%23x%3Fy=1/splits", server.takeRequest().getPath());
        assertEquals("/payments/pay_1%252Fcancel", server.takeRequest().getPath());
        assertEquals("/customers/cus_1/tax_ids/txi%201%5Cx", server.takeRequest().getPath());
    }

    private static String signWebhook(String secret, String payload, long timestamp) {
        return "v1=" + Crypto.hmacSha256(secret, timestamp + "." + payload);
    }
}
