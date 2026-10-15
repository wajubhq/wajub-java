# Changelog

All notable changes to `com.wajub:wajub-java` are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
The library uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.3.0] — 2026-10-15

### Added
- **`Payment.getAmountDecimal()`, the amount with its decimals.** The API sends
  amounts in the major unit of the currency, so GHS 12.50 arrives as `12.5`.
  `getAmountDecimal()` returns it as a `BigDecimal`: `12.5` for GHS 12.50,
  `15000` for XAF 15,000. An amount sent as a string (`"12.50"`) reads the
  same. Trailing zeros are dropped, so compare it with `compareTo`, not
  `equals`. Put it back in a request as is, for a refund for instance, and it
  goes out with its cents; a `BigDecimal` you pass goes out digit for digit.

### Deprecated
- **`Payment.getAmount()`.** It returns a `long` and drops the decimals:
  GHS 12.50 reads `12`. It still returns that value, so code that calls it
  compiles and behaves as before. Call `getAmountDecimal()` instead.

### Fixed
- The README said amounts are passed in the smallest currency unit (cents).
  The API reads them in the major unit: send `12.50` for GHS 12.50, not `1250`.
  Zero-decimal currencies such as XAF and XOF are not affected.

## [1.2.1] — 2026-10-12

### Changed
- **The `409` for an `Idempotency-Key` still being processed is retried.** A
  call sent again after a timeout can reach the API while the first one is
  still running; the API answers `409` with `errors.idempotency_key`. The
  client now waits 2 then 4 seconds and asks again with the same key, within
  its usual 2 retries, and returns the first request's result. Every other
  `409` is still thrown at once.

### Fixed
- `webhooks().constructEvent()` with a missing `X-Wajub-Timestamp` header
  (`null`) threw a `NullPointerException`. It now throws
  `WebhookSignatureVerificationException`, as a missing signature already did.

## [1.2.0] — 2026-10-01

Tagged, never published to Maven Central. Its change ships with 1.2.1.

### Removed
- `payments().initialize()`, an alias of `payments().create()`. Call `create()`.

## [1.1.1] — 2026-08-09

Last version published before this changelog was started.
