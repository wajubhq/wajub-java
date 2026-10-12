# Changelog

All notable changes to `com.wajub:wajub-java` are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
The library uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

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
