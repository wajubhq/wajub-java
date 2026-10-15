# Changelog

All notable changes to `com.wajub:wajub-java` are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
The library uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.3.0] — 2026-10-15

### Added
- **`Payment.getAmountDecimal()`, the amount with its decimals.** Amounts are
  in the currency's main unit, so GHS 12.50 is `12.5`. `getAmountDecimal()`
  returns it as a `BigDecimal`: `12.5` for GHS 12.50, `15000` for XAF 15,000.
  Trailing zeros are dropped, so compare it with `compareTo`, not `equals`.
  Put it back in a request as is, for a refund for instance, and it goes out
  with its decimals; a `BigDecimal` you pass goes out digit for digit.

### Deprecated
- **`Payment.getAmount()`.** It returns a `long` and drops the decimals:
  GHS 12.50 reads `12`. It still returns that value, so code that calls it
  compiles and behaves as before. Call `getAmountDecimal()` instead.

### Fixed
- The README said amounts are passed in the smallest currency unit (cents).
  Amounts are in the currency's main unit: send `12.50` for GHS 12.50, not
  `1250`. Currencies without decimals, such as XAF and XOF, are not affected.
- **`webhooks().constructEvent()` with a negative tolerance** threw
  `WebhookSignatureVerificationException` for every delivery, even a fresh
  one. A negative tolerance now keeps the default 300 seconds, as `null` and
  zero do.

## [1.2.1] — 2026-10-12

### Changed
- **A create sent again with the same idempotency key while the first is still
  running** now returns the first request's result instead of a `409` error.

### Fixed
- `webhooks().constructEvent()` with a missing `X-Wajub-Timestamp` header
  (`null`) threw a `NullPointerException`. It now throws
  `WebhookSignatureVerificationException`, as a missing signature already did.

## [1.2.0] — 2026-10-01

Install 1.2.1, which carries this change.

### Removed
- `payments().initialize()`, an alias of `payments().create()`. Call `create()`.

## [1.1.1] — 2026-08-09

### Changed
- README corrections; no change to the library.

## [1.1.0] — 2026-08-09

### Added
- Initial release.
