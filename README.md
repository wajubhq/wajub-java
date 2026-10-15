# Wajub Java SDK

[![Maven Central](https://img.shields.io/maven-central/v/com.wajub/wajub-java)](https://central.sonatype.com/artifact/com.wajub/wajub-java)
[![Java](https://img.shields.io/badge/Java-17%2B-blue)](https://openjdk.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Official server-side SDK for the [Wajub API](https://docs.wajub.com): create payments, confirm them, pay out and verify webhooks from your Java backend.

It runs on your server with a secret (`sk.…`, `sk_test.…`) or restricted (`rk.…`, `rk_test.…`) API key. Never expose these keys in client-side code: in the browser, use [Wajub.js](https://docs.wajub.com/libraries/components/js) with your publishable key (`pk.…`, `pk_test.…`).

## Requirements

| Requirement | Version |
|-------------|---------|
| Java | 17 or later |

The SDK brings [OkHttp](https://square.github.io/okhttp/) 4.12 and [Jackson](https://github.com/FasterXML/jackson) 2.18 with it.

## Installation

**Maven:**

```xml
<dependency>
    <groupId>com.wajub</groupId>
    <artifactId>wajub-java</artifactId>
    <version>1.3.0</version>
</dependency>
```

**Gradle:**

```kotlin
implementation("com.wajub:wajub-java:1.3.0")
```

## Quick start

Amounts are in the currency's **main unit**: `12.50` for GHS 12.50, `5000` for XAF 5,000. Pass a decimal as a `BigDecimal`, `new BigDecimal("12.50")`.

```java
import com.wajub.Payment;
import com.wajub.Wajub;
import java.util.Map;

Wajub client = Wajub.create(Wajub.Config.builder()
    .apiKey(System.getenv("WAJUB_API_KEY"))
    .webhookSecret(System.getenv("WAJUB_WEBHOOK_SECRET"))
    .build());

Payment payment = client.payments().create(Map.of(
    "amount", 15000,
    "currency", "XAF",
    "email", "buyer@example.com",
    "reference", "order-1042",
    "callback", "https://shop.example.com/order/complete"
), null);

// Redirect the customer to payment.getAuthorizationUrl(),
// or mount payment.getAuthorizationToken() with Wajub.js in your page.
```

Then confirm the result on your server: handle the `payment.succeeded` webhook, or call `client.payments().retrieve(payment.getId())`.

A `Payment` gives `getId()`, `getStatus()`, `getAmountDecimal()` (the amount with its decimals, a `BigDecimal`: compare it with `compareTo`, not `equals`), `getCurrency()`, `getAuthorizationUrl()` and `getAuthorizationToken()`; every other field the API returned is in `getRaw()`. `getAmount()` drops the decimals and is deprecated.

## Configuration

| Builder method | Description |
|----------------|-------------|
| `apiKey` | Secret or restricted API key (`sk.…`, `sk_test.…`, `rk.…`, `rk_test.…`). Falls back to `WAJUB_API_KEY`. |
| `webhookSecret` | Webhook signing secret (`whsec_…`), needed only for `webhooks().constructEvent()`. Falls back to `WAJUB_WEBHOOK_SECRET`. |
| `httpClient` | Your own `OkHttpClient`, for a proxy, an interceptor or timeouts. |

`Wajub.create(apiKey)` is a shortcut for a client with just a key. Without a key, `Wajub.create` throws an `IllegalArgumentException`. The client is thread safe: build it once and share it.

## Resources

| Purpose | Service | Methods |
|---------|---------|---------|
| Payments | `client.payments()` | `create`, `retrieve`, `list`, `cancel`, `process`, `processSplit`, `listRefunds` |
| Refunds | `client.refunds()` | `create`, `retrieve`, `list` |
| Customers | `client.customers()` | `create`, `retrieve`, `update`, `delete`, `list`, `block`, `unblock`, `activate`, `deactivate`, `listTaxIds`, `createTaxId`, `deleteTaxId` |
| Payment links | `client.links()` | `create`, `retrieve`, `update`, `delete`, `list` |
| Invoices | `client.invoices()` | `create`, `retrieve`, `update`, `delete`, `list`, `send`, `markPaid`, `cancel` |
| Payouts | `client.transfers()` | `create`, `retrieve`, `list` |
| Payout recipients | `client.beneficiaries()` | `create`, `retrieve`, `update`, `delete`, `list` |
| Destination checks | `client.identity()` | `resolve`, `validate` |
| Balance | `client.balance()` | `retrieve` |
| Disputes | `client.disputes()` | `list`, `retrieve`, `submitEvidence`, `accept`, `close`, `sendMessage` |
| Events | `client.events()` | `list`, `retrieve`, `resend` |
| Webhook endpoints | `client.webhookEndpoints()` | `create`, `retrieve`, `update`, `delete`, `list`, `rotateSecret` |
| Webhook verification | `client.webhooks()` | `constructEvent` |
| Connected accounts (Sync) | `client.accounts()` | `create`, `retrieve`, `update`, `delete`, `list`, `regenerateToken` |
| Tax | `client.tax()` | `getSettings`, `updateSettings`, `rates`, `calculate`, `reports`, `listCodes`, `retrieveCode`, `listRegistrations`, `createRegistration`, `retrieveRegistration`, `updateRegistration`, `deleteRegistration`, `jurisdictions`, `thresholds`, `thresholdAlerts` |
| Fraud (Shield) | `client.shield()` | `getSettings`, `updateSettings`, `stats`, `listBlocklist`, `addToBlocklist`, `removeFromBlocklist` |
| Reference data | `client.global()` | `ping`, `channels`, `countries`, `currencies` |

Parameters are a `Map<String, Object>` with the API's field names. The last argument of a mutating call is a `RequestOptions`, usually `null`.

## Acting for a connected account

Pass the connected account with `setSync` on the call:

```java
import com.wajub.RequestOptions;

client.payments().create(params, new RequestOptions().setSync("acct_sync_ref"));
```

## Webhooks

Pass the **raw request body**, not parsed JSON, with the `X-Wajub-Signature` and `X-Wajub-Timestamp` headers. With Spring:

```java
@PostMapping("/webhooks/wajub")
public ResponseEntity<Void> handleWebhook(
        @RequestBody byte[] body,
        @RequestHeader("X-Wajub-Signature") String signature,
        @RequestHeader("X-Wajub-Timestamp") String timestamp) {
    try {
        Map<String, Object> event = client.webhooks().constructEvent(body, signature, timestamp, null);

        if ("payment.succeeded".equals(event.get("event"))) {
            // fulfil the order
        }
    } catch (WebhookSignatureVerificationException e) {
        return ResponseEntity.badRequest().build();
    }
    return ResponseEntity.ok().build();
}
```

A delivery that does not verify throws `com.wajub.exception.WebhookSignatureVerificationException`. If your server's clock drifts, pass a tolerance such as `Duration.ofMinutes(10)` as the last argument; `null` keeps the default.

## Pagination

`list` returns a page you can walk one page at a time, or collect across all pages:

```java
import com.wajub.PagedResult;
import java.util.List;

PagedResult page = client.payments().list(Map.of("per_page", 50));
System.out.println(page.getData() + " " + page.getMeta());

if (page.hasMore()) {
    PagedResult next = page.getNextPage();
}

List<Map<String, Object>> all = page.autoPaging();
```

Rows are `Map<String, Object>`, read by key.

## Idempotency

Temporary failures are retried for you. To make a create safe to repeat on your side too, after a restart or in a job retry, pass your own key:

```java
client.payments().create(params, new RequestOptions().setIdempotencyKey("order-" + orderId));
```

## Error handling

API errors throw unchecked subclasses of `com.wajub.exception.WajubException`:

```java
import com.wajub.exception.AuthenticationException;
import com.wajub.exception.InvalidRequestException;
import com.wajub.exception.WajubException;

try {
    client.payments().create(params, null);
} catch (InvalidRequestException e) {
    System.out.println(e.getErrors()); // field-level validation errors
} catch (AuthenticationException e) {
    // check your API key
} catch (WajubException e) {
    System.out.println(e.getCode() + " " + e.getHttpStatus() + " " + e.getMessage());
}
```

| Exception | Thrown on |
|-----------|-----------|
| `AuthenticationException` | `401` |
| `PermissionException` | `403` |
| `NotFoundException` | `404` |
| `InvalidRequestException` | `400`, `422` |
| `RateLimitException` | `429`, with `getRetryAfter()` in seconds |
| `WajubException` | Any other error status |
| `ApiConnectionException` | No response: network error or timeout |

Every exception carries `getMessage()`, `getCode()`, `getHttpStatus()`, `getErrors()` (field-level messages) and `getRaw()` (the response body).

## Testing

Use a sandbox key (`sk_test.…`): the key chooses the environment, and no real money moves. The test phone numbers and cards are on [Test scenarios](https://docs.wajub.com/developer-tools/testing/test-scenarios). To receive webhooks on your machine, forward them with the [Wajub CLI](https://docs.wajub.com/developer-tools/cli/listen).

## Documentation & support

- Documentation: [docs.wajub.com/libraries/sdks/java](https://docs.wajub.com/libraries/sdks/java)
- Report issues: [github.com/wajubhq/wajub-java/issues](https://github.com/wajubhq/wajub-java/issues)

## License

MIT — see [LICENSE](LICENSE).
