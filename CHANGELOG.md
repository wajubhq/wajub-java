# Changelog

All notable changes to `com.wajub:wajub-java` are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
The library uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.3.0] — 2026-10-15

1.2.0 and 1.2.1 were never released: their changes ship in this release, and
are listed here for an upgrade from 1.1.1.

### Added
- **`Payment.getAmountDecimal()`, the amount with its decimals.** Amounts are
  in the currency's main unit, so GHS 12.50 is `12.5`. `getAmountDecimal()`
  returns it as a `BigDecimal`: `12.5` for GHS 12.50, `15000` for XAF 15,000.
  Trailing zeros are dropped, so compare it with `compareTo`, not `equals`.
  Put it back in a request as is, for a refund for instance, and it goes out
  with its decimals; a `BigDecimal` you pass goes out digit for digit.
- **`getFieldErrors()` on API exceptions, every validation message of each
  field.** A `422` can give a field several messages; `getErrors()` keeps only
  the first one. `e.getFieldErrors().get("amount")` lists them all, in the
  order the API sent them. `getErrors()` is unchanged.

### Changed
- **A create sent again with the same idempotency key while the first is still
  running** returns the first request's result instead of a `409` error. If
  the first has not finished, the `409` is still thrown.
- **Ids passed to a method.** A `null` id now throws
  `IllegalArgumentException`; 1.1.1 sent a request for the id `null`. Spaces
  around an id are dropped by every method, where 1.1.1 dropped only some, and
  an id with a character such as `#` or `?` is sent whole instead of being cut
  there.

### Deprecated
- **`Payment.getAmount()`.** It returns a `long` and drops the decimals:
  GHS 12.50 reads `12`. It still returns that value, so code that calls it
  compiles and behaves as before. Call `getAmountDecimal()` instead.
- **`listen()`, `listen().config()` and `listen().auth()`.** The API no
  longer offers them and answers `410 Gone`. To receive webhooks on your
  machine, forward them with the Wajub CLI: `wajub listen`.
- **`payments().initialize()`.** It does what `payments().create()` does, and
  code that calls it compiles and behaves as before. Call `create()`, which
  takes the same parameters.

### Fixed
- **An API error whose body holds a JSON `null`**, such as `"data": null`,
  threw a `NullPointerException` instead of the `WajubException`. It now
  throws the exception, with the body as sent in `getRaw()`.
- The README said amounts are passed in the smallest currency unit (cents).
  Amounts are in the currency's main unit: send `12.50` for GHS 12.50, not
  `1250`. Currencies without decimals, such as XAF and XOF, are not affected.
- **`webhooks().constructEvent()` with a missing `X-Wajub-Signature` or
  `X-Wajub-Timestamp` header** (`null` or empty) now throws
  `WebhookSignatureVerificationException` with `Missing X-Wajub-Signature or
  X-Wajub-Timestamp header.` It reported a bad signature format or a failed
  signature check.
- `webhooks().constructEvent()` with a `null` body threw a
  `NullPointerException`. It now throws `WebhookSignatureVerificationException`.
- **`webhooks().constructEvent()` with a negative tolerance** threw
  `WebhookSignatureVerificationException` for every delivery, even a fresh
  one. A negative tolerance now keeps the default 300 seconds, as `null` and
  zero do.
- **`webhooks().constructEvent()` with a tolerance under one second**, such as
  `Duration.ofMillis(500)`, counted as 0 seconds: a delivery was refused as
  soon as the clock turned to the next second. Timestamps are whole seconds,
  so the tolerance is now rounded up to whole seconds: 500 ms counts as one.
- **`webhooks().constructEvent()` on a server whose default charset is not
  UTF-8**, as with Java 17 on Windows: a delivery with an accented character
  failed its signature check. The signature is now checked over the body's
  bytes, whatever the default charset.

### Security
- **`webhooks().constructEvent()` checks the signature over the body's bytes
  as received.** It checked the body decoded as text, where bytes that are not
  valid UTF-8 all read alike, so a body changed in those bytes could pass the
  signature check.
- **An id that is not a Wajub id is refused before anything is sent.** A
  Wajub id is never empty, `.` or `..` and never contains `/`; such an id,
  passed to a method like `payments().retrieve(id)`, sent the request to
  another path. It now throws `IllegalArgumentException`.

## [1.1.1] — 2026-08-09

### Changed
- README corrections; no change to the library.

## [1.1.0] — 2026-08-09

### Added
- Initial release.
