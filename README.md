# Wajub Java SDK

[![Maven Central](https://img.shields.io/maven-central/v/com.wajub/wajub-java)](https://central.sonatype.com/artifact/com.wajub/wajub-java)
[![Java](https://img.shields.io/badge/Java-17%2B-blue)](https://openjdk.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Official **server-side** SDK for the [Wajub merchant API](https://docs.wajub.com). Accept mobile-money and card payments across Africa with a Stripe-inspired, resource-oriented client.

Use **[Wajub.js](https://docs.wajub.com/libraries/components/js)** for embedded checkout in the browser. Use this SDK on your backend with a secret (`sk_`) or restricted (`rk_`) API key — never expose secret keys in client-side code.

This SDK covers the **merchant API**. It does not wrap checkout session endpoints (`/pay/*`), public link checkout (`/q/*`, `/i/*`), or sandbox simulation — those belong to Wajub.js or direct HTTP during the payment flow.

## Features

- Resource-oriented API (`client.payments()`, `client.customers()`, …)
- Automatic `Idempotency-Key` on mutating requests (override per call)
- Typed exceptions per HTTP status (`AuthenticationException`, `RateLimitException`, …)
- Automatic retries on 429 and 5xx (max 2, exponential backoff)
- Page-based pagination with `autoPaging()` and `getNextPage()`
- Webhook signature verification (HMAC-SHA256, timestamp tolerance)

## Requirements

| Requirement | Version |
|-------------|---------|
| Java | 17 or later |
| Build tool | Maven or Gradle |
| HTTP / JSON | [OkHttp](https://square.github.io/okhttp/) 4.12+, [Jackson](https://github.com/FasterXML/jackson) 2.18+ (installed automatically) |

## Installation

**Maven:**

```xml
<dependency>
    <groupId>com.wajub</groupId>
    <artifactId>wajub-java</artifactId>
    <version>1.1.0</version>
</dependency>
```

**Gradle:**

```kotlin
implementation("com.wajub:wajub-java:1.1.0")
```

## Quick start

Amounts are passed in the **smallest currency unit** (e.g. cents for EUR/USD; whole francs for XAF).

### Redirect checkout

```java
import com.wajub.Wajub;
import com.wajub.Payment;

Wajub client = Wajub.create(System.getenv("WAJUB_API_KEY"));

Payment payment = client.payments().create(java.util.Map.of(
    "amount", 15000,
    "currency", "XAF",
    "email", "buyer@example.com",
    "callback", "https://shop.example.com/order/complete"
), null);

System.out.println(payment.getAuthorizationUrl());
```

### Inline / overlay (embed token)

```java
Payment embed = client.payments().create(java.util.Map.of(
    "amount", 15000,
    "currency", "XAF",
    "metadata", java.util.Map.of("mode", "embed")
), null);

// Pass to Wajub.js: embed.getAuthorizationToken()
```

## Spring Boot

```java
@Configuration
public class WajubConfig {
    @Bean
    Wajub wajubClient(@Value("${wajub.api-key}") String apiKey,
                      @Value("${wajub.webhook-secret:}") String webhookSecret) {
        return Wajub.create(Wajub.Config.builder()
            .apiKey(apiKey)
            .webhookSecret(webhookSecret.isBlank() ? null : webhookSecret)
            .build());
    }
}
```

### Webhook controller

Use the **raw request body**:

```java
@PostMapping("/webhooks/wajub")
public ResponseEntity<Void> handleWebhook(
        @RequestBody byte[] body,
        @RequestHeader("X-Wajub-Signature") String signature,
        @RequestHeader("X-Wajub-Timestamp") String timestamp) {
    try {
        Map<String, Object> event = wajub.webhooks().constructEvent(
            body, signature, timestamp, null);

        if ("payment.succeeded".equals(event.get("type"))) {
            // fulfill order
        }
    } catch (WebhookSignatureVerificationException e) {
        return ResponseEntity.badRequest().build();
    }
    return ResponseEntity.ok().build();
}
```

## Configuration

| Variable | Description |
|----------|-------------|
| `WAJUB_API_KEY` | Secret or restricted API key (`sk_`, `sk_test.`, `rk_`, …) |
| `WAJUB_WEBHOOK_SECRET` | Webhook signing secret (`whsec_`) for `constructEvent()` |

Test mode is selected by your API key prefix (`sk_test.…`), not by the API URL. Production calls always go to `https://api.wajub.com`.

Explicit configuration:

```java
Wajub client = Wajub.create(Wajub.Config.builder()
    .apiKey(System.getenv("WAJUB_API_KEY"))
    .webhookSecret(System.getenv("WAJUB_WEBHOOK_SECRET"))
    .idempotencyKeyPrefix("myshop")
    .build());
```

## Resources (merchant API)

| Service | Methods |
|---------|---------|
| `client.global()` | `ping`, `channels`, `countries`, `currencies` |
| `client.payments()` | `create`, `initialize`, `retrieve`, `list`, `cancel`, `process`, `processSplit`, `listRefunds` |
| `client.customers()` | `create`, `retrieve`, `update`, `delete`, `list`, `block`, `unblock`, `activate`, `deactivate`, `listTaxIds`, `createTaxId`, `deleteTaxId` |
| `client.refunds()` | `create`, `retrieve`, `list` |
| `client.transfers()` | `create`, `retrieve`, `list` |
| `client.beneficiaries()` | `create`, `retrieve`, `update`, `delete`, `list` |
| `client.links()` | `create`, `retrieve`, `update`, `delete`, `list` |
| `client.invoices()` | `create`, `retrieve`, `update`, `delete`, `list`, `send`, `markPaid`, `cancel` |
| `client.accounts()` | `create`, `retrieve`, `update`, `delete`, `list`, `regenerateToken` |
| `client.webhookEndpoints()` | `create`, `retrieve`, `update`, `delete`, `list`, `rotateSecret` |
| `client.balance()` | `retrieve` |
| `client.events()` | `list`, `retrieve`, `resend` |
| `client.disputes()` | `list`, `retrieve`, `submitEvidence`, `accept`, `close`, `sendMessage` |
| `client.identity()` | `resolve`, `validate` |
| `client.tax()` | `getSettings`, `updateSettings`, `rates`, `calculate`, `reports`, `listCodes`, `retrieveCode`, `listRegistrations`, `createRegistration`, `retrieveRegistration`, `updateRegistration`, `deleteRegistration`, `jurisdictions`, `thresholds`, `thresholdAlerts` |
| `client.shield()` | `getSettings`, `updateSettings`, `stats`, `listBlocklist`, `addToBlocklist`, `removeFromBlocklist` |
| `client.listen()` | `config`, `auth` |
| `client.webhooks()` | `constructEvent` (local — no HTTP) |

## Sync (Connect)

```java
import com.wajub.RequestOptions;

client.payments().create(params, new RequestOptions().setSync("acct_sync_ref"));
```

## Webhooks

```java
import java.util.Map;

Map<String, Object> event = client.webhooks().constructEvent(
    body,    // byte[] — raw body, not parsed JSON
    request.getHeader("X-Wajub-Signature"),
    request.getHeader("X-Wajub-Timestamp"),
    null
);

if ("payment.succeeded".equals(event.get("type"))) {
    // fulfill order
}
```

During local development, use the [Wajub CLI](https://github.com/wajubhq/wajub-cli) to forward webhooks to your machine.

## Pagination

```java
PagedResult page = client.payments().list(Map.of("per_page", 50));

List<Map<String, Object>> all = page.autoPaging();

// Manual page control
PagedResult first = client.payments().list(Map.of());
if (first.hasMore()) {
    PagedResult second = first.getNextPage();
}
```

## Idempotency

POST and PUT requests automatically receive an `Idempotency-Key` header. Pass your own:

```java
client.payments().create(params, new RequestOptions().setIdempotencyKey("order-" + orderId));
```

## Error handling

```java
import com.wajub.exception.InvalidRequestException;
import com.wajub.exception.AuthenticationException;
import com.wajub.exception.RateLimitException;

try {
    client.payments().create(params, null);
} catch (InvalidRequestException e) {
    System.out.println(e.getErrors()); // field-level validation errors
} catch (AuthenticationException e) {
    // 401 — bad API key
} catch (RateLimitException e) {
    // 429 — back off and retry
}
```

## Development

```bash
mvn test
```

## Documentation & support

- Full API reference: [docs.wajub.com/libraries/sdks/java](https://docs.wajub.com/libraries/sdks/java)
- Report issues: [github.com/wajubhq/wajub-java/issues](https://github.com/wajubhq/wajub-java/issues)

## License

MIT — see [LICENSE](LICENSE).
