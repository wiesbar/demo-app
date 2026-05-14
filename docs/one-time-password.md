# One-time password

The `example.otp` package implements a per-user one-time-password (OTP) flow: a caller asks for an OTP for a given `userId` and then later verifies a candidate OTP for that same user. It exposes a single application-level service `OTPService`, which composes three internal ports — `OTPGenerator` (produces the OTP), `PasswordRepository` (stores it with an expiry and a remaining-attempts counter), and `SMSService` (delivers it out-of-band). A separate `RateLimiter` port enforces per-user, per-operation request limits. The HTTP surface lives in `example.web.OtpController`, with request payloads in `example.web.OtpDto`.

Each port has two implementations: an in-memory Caffeine implementation that is the production default, and a Postgres-backed jOOQ implementation activated by the opt-in `persistent-otp` Spring profile. See [Profiles](#profiles) and [Storage implementations](#storage-implementations).

## HTTP endpoints

Both endpoints are gated by the `one-time-password` Spring profile and rooted at `/one-time-password`. Both call the rate limiter before doing any work — a breached limit short-circuits with `429 Too Many Requests`.

### `POST /one-time-password/generate`

Generates an OTP for the given `userId`, stores it with an expiry and a fresh attempts counter, and hands it to the configured `SMSService` for delivery. Any previously-stored OTP for the same `userId` is replaced.

Request body (`application/json`):

```json
{
  "userId": "user-1"
}
```

Responses:

- `204 No Content` — OTP generated, stored, and dispatched.
- `400 Bad Request` — `userId` is blank, or the body is unparseable / missing required fields.
- `429 Too Many Requests` — the per-user `generate` rate limit was exceeded. See [Errors](#errors).

### `POST /one-time-password/verify`

Verifies a candidate OTP for the given `userId`. Each call consumes one attempt against the stored entry.

Request body (`application/json`):

```json
{
  "userId": "user-1",
  "otp": "ABCDEF"
}
```

Responses:

- `204 No Content` — the supplied OTP matches the stored OTP, has not expired, and at least one attempt remained. The entry is evicted on successful verification.
- `401 Unauthorized` — verification failed. This is returned for every non-success case: wrong OTP, expired OTP, attempts exhausted, or no OTP was ever generated for the user. The response is intentionally indistinguishable across these cases so the endpoint does not leak which one occurred.
- `400 Bad Request` — `userId` or `otp` is blank, or the body is unparseable / missing required fields.
- `429 Too Many Requests` — the per-user `verify` rate limit was exceeded. See [Errors](#errors).

## Domain / package layout

All types in `example.otp` are `internal` to keep Spring/HTTP concerns out of the package; wiring lives in `example.config.OtpConfig`.

| Type                            | Notes                                                                                                                                                  |
|---------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| `OTPService`                    | Application service. `generate(userId)` produces an OTP, sends it, and stores it. `verify(userId, otp)` delegates to `PasswordRepository.consumeAttempt` and returns the boolean as-is. |
| `OTPGenerator` / `DefaultOTPGenerator` | `fun interface OTPGenerator { fun generate(): String }`. The production implementation produces a 6-character string from `'A'..'Z'`, drawn with `SecureRandom().asKotlinRandom()`. Configurable `length`, `allowedChars`, and `random`. |
| `PasswordRepository` / `DefaultPasswordRepository` / `JooqPasswordRepository` | The storage port, with two implementations. `DefaultPasswordRepository` (default profile) is an in-memory Caffeine `Cache<String, OtpEntry>` with a custom `Expiry` and `Ticker` both driven by the injected `kotlin.time.Clock`. `JooqPasswordRepository` (`persistent-otp` profile) is Postgres-backed: OTPs are stored as peppered HMAC hashes in `otp_entries`. See [Storage implementations](#storage-implementations). |
| `RateLimiter` / `CaffeineRateLimiter` / `JooqRateLimiter` | The rate-limit port (`tryAcquire(userId, operation): TryAcquireResult`), with two implementations. `CaffeineRateLimiter` (default profile) keeps one Caffeine cache per window per operation, keyed by `userId`. `JooqRateLimiter` (`persistent-otp` profile) is Postgres-backed by the `otp_rate_limits` table. The `acquireOrThrow(userId, operation)` extension throws `RateLimitExceededException` on a `Denied` result. |
| `OtpHasher` / `Sha256HmacOtpHasher` | Hashes OTPs for storage at rest (`persistent-otp` only). `Sha256HmacOtpHasher` computes HMAC-SHA-256 with a server-side pepper, exposes an `algorithm` discriminator (`"HMAC-SHA-256"`, written to `otp_entries.otp_algo` for future rotation), and compares constant-time via `MessageDigest.isEqual`. |
| `OtpSweeper`                    | Deletes rows whose `expires_at` is in the past from both `otp_entries` and `otp_rate_limits` (`persistent-otp` only). Annotation-free; the periodic schedule is an `@Scheduled` method on `OtpSweepSchedule` in `example.config` that delegates to `OtpSweeper.sweep()`. |
| `PostgresClock`                 | A `kotlin.time.Clock` whose `now()` reads `statement_timestamp()` from Postgres (`persistent-otp` only), so all app instances share one authoritative clock instead of N skewing host wall clocks. |
| `SMSService` / `NoOpSmsService` | The delivery port. The production implementation, `NoOpSmsService`, only logs `"Sending one time password to user '<userId>'"` and does **not** send any real SMS. The OTP value itself is not logged. |
| `InvalidOtpRequestException`    | `RuntimeException` thrown by `OtpController` validation. `GlobalExceptionHandler` maps it to HTTP 400.                                                 |
| `RateLimitExceededException`    | `RuntimeException` carrying the soonest `retryAfter` duration, thrown by `RateLimiter.acquireOrThrow`. `GlobalExceptionHandler` maps it to HTTP 429.   |

## Storage implementations

The `PasswordRepository` and `RateLimiter` ports each have two implementations, selected by Spring profile.

### In-memory (default profile)

`DefaultPasswordRepository` and `CaffeineRateLimiter` are the production default and need no external infrastructure. The repository is an in-memory Caffeine `Cache<String, OtpEntry>`; each entry holds the plaintext OTP, an `expiresAt: kotlin.time.Instant`, and an `AtomicInteger attemptsLeft`. The rate limiter keeps one Caffeine `Cache<String, AtomicInteger>` per window per operation. Both wire `Clock.System` in production.

### Postgres-backed (`persistent-otp` profile)

`JooqPasswordRepository` and `JooqRateLimiter` persist state in Postgres via jOOQ's type-safe DSL (generated classes in package `example.otp.jooq`). They survive restarts and let multiple app instances share OTP state. Activated by the opt-in `persistent-otp` profile (composed with `one-time-password`), which also wires `Sha256HmacOtpHasher`, `OtpSweeper`/`OtpSweepSchedule`, and `PostgresClock`.

Schema (two Flyway migrations under `src/main/resources/db/migration`):

- `V1__create_otp_entries.sql` — `otp_entries (user_id PRIMARY KEY, otp_hash BYTEA, otp_algo TEXT, expires_at TIMESTAMPTZ, attempts_left INTEGER CHECK >= 0, created_at TIMESTAMPTZ)`.
- `V2__create_otp_rate_limits.sql` — `otp_rate_limits (user_id, operation, window_key, count, window_started_at, expires_at)` with `PRIMARY KEY (user_id, operation, window_key)`.

Key behaviours:

- **OTPs are hashed at rest.** `JooqPasswordRepository.store` writes `hasher.hash(otp)` and `hasher.algorithm`, never the plaintext. `consumeAttempt` re-hashes the candidate and compares with `OtpHasher.matches` (constant-time, algorithm-discriminated). The pepper is a secret supplied via configuration, never a DB column.
- **Attempt decrement is a single statement.** `consumeAttempt` issues `UPDATE otp_entries SET attempts_left = attempts_left - 1 WHERE user_id = ? AND expires_at > :now AND attempts_left > 0 RETURNING otp_hash, otp_algo, attempts_left`. The `WHERE attempts_left > 0` predicate plus Postgres' row lock guarantees at most `maxAttempts` updates return a row for one `store`, so concurrent verifies cannot over-consume. If the returned `attempts_left` is `0`, a follow-up `DELETE` removes the exhausted row.
- **`store` is an upsert.** `INSERT ... ON CONFLICT (user_id) DO UPDATE SET ...` — same overwrite-and-reset semantics as `Cache.put`.
- **Rate-limit increment is a single statement.** `JooqRateLimiter` issues `INSERT INTO otp_rate_limits (...) VALUES (..., 1, ...) ON CONFLICT (user_id, operation, window_key) DO UPDATE SET count = count + 1 RETURNING count`, then compares the returned count against the window limit. `window_key` encodes the window name plus a fixed wall-clock time bucket (`"<name>:<floor(now / window)>"`) so all instances agree on bucket boundaries.
- **Expiry is two layers.** The hot path filters `WHERE expires_at > :now`; `OtpSweeper.sweep()` (driven by `OtpSweepSchedule`'s `@Scheduled(fixedDelayString = "PT1M")`) deletes stale rows from both tables every minute.

## Configuration

Bound from `application.yaml` via `OtpProperties` (`@ConfigurationProperties(prefix = "one-time-password")`):

```yaml
one-time-password:
  length: 6
  max-attempts: 3
  expire-time: 5m
  hash-pepper: ${OTP_HASH_PEPPER:dev-only-otp-pepper-change-me}
  rate-limit:
    generate:
      short:
        limit: 1
        duration: 30s
      long:
        limit: 5
        duration: 1h
    verify:
      long:
        limit: 10
        duration: 5m
```

| Property                       | Default | Description                                                                                  |
|--------------------------------|---------|----------------------------------------------------------------------------------------------|
| `one-time-password.length`     | `6`     | Number of characters in each OTP. Passed to `DefaultOTPGenerator(length = …)`. Must be in `1..64`. |
| `one-time-password.max-attempts` | `3`   | Maximum number of `verify` attempts allowed per stored OTP, after which the entry is removed. Passed to both `DefaultPasswordRepository` and `JooqPasswordRepository`. Must be `> 0`. |
| `one-time-password.expire-time`  | `5m`  | How long a stored OTP stays valid after `generate`. Bound as `java.time.Duration` and converted to `kotlin.time.Duration` before reaching the repository. Must be `> 0`. |
| `one-time-password.hash-pepper`  | `${OTP_HASH_PEPPER:dev-only-otp-pepper-change-me}` | Server-side secret used to construct `Sha256HmacOtpHasher`. Bound to `OtpProperties.hashPepper` (Kotlin default `""`). **Consumed only under the `persistent-otp` profile** — ignored by the in-memory implementation. The default is a dev placeholder; override `OTP_HASH_PEPPER` for any real deployment. |
| `one-time-password.rate-limit`   | see below | Per-operation windowed request limits. Binds to `RateLimitProperties` holding `generate` and `verify` `OperationWindows`. |

The `rate-limit` block holds one `OperationWindows` per operation (`generate`, `verify`). Each `OperationWindows` has an optional `short` window and a required `long` window; each window is a `WindowSpec(limit, duration)` — `limit` must be `> 0` and `duration` must be `> 0`. Defaults:

| Operation  | `short`        | `long`         |
|------------|----------------|----------------|
| `generate` | `1` per `30s`  | `5` per `1h`   |
| `verify`   | *(none)*       | `10` per `5m`  |

A request must acquire a slot in **every** configured window for its operation; if any window denies, the request is rejected and any windows already acquired in that call are released.

The OTP alphabet, the `SecureRandom` source, and the `Clock` are not externally configurable — overriding them requires changing the bean wiring in `OtpConfig`.

## Profiles

### `one-time-password`

All OTP beans (`OTPGenerator`, `PasswordRepository`, `RateLimiter`, `SMSService`, `OTPService`, plus the `Clock`) and the HTTP controller are gated by the Spring profile `one-time-password`. With this profile inactive, neither endpoint is registered and no OTP state is held. The profile is active by default through `spring.profiles.default=calculator,catalog,one-time-password` in `application.yaml`. To disable it, set `SPRING_PROFILES_ACTIVE` to a profile set that omits `one-time-password`:

```sh
SPRING_PROFILES_ACTIVE=calculator,catalog ./gradlew bootRun
```

Note that the string `one-time-password` is used in three independent places: as the Spring profile name, as the configuration-property prefix, and as the URI root of the HTTP endpoints. These are intentionally aligned but conceptually distinct.

### `persistent-otp` (opt-in)

`persistent-otp` is **not** part of `spring.profiles.default`; it is activated explicitly and composes with `one-time-password` (you activate both). It swaps the in-memory beans for their Postgres-backed equivalents:

| Default profile bean       | `persistent-otp` bean        |
|----------------------------|------------------------------|
| `DefaultPasswordRepository` | `JooqPasswordRepository`     |
| `CaffeineRateLimiter`       | `JooqRateLimiter`            |
| `Clock.System`              | `PostgresClock`              |
| *(none)*                    | `Sha256HmacOtpHasher`        |
| *(none)*                    | `OtpSweeper` + `OtpSweepSchedule` |

Because it talks to Postgres, this profile **requires a running Postgres database** (and therefore Docker for local development). `application.yaml` excludes the JDBC / jOOQ / Flyway autoconfigurations in its default document and re-enables them — adding the datasource URL/credentials (`OTP_DATASOURCE_URL` / `OTP_DATASOURCE_USERNAME` / `OTP_DATASOURCE_PASSWORD`, defaulting to `jdbc:postgresql://localhost:5432/otp`) and `spring.flyway` config — only in the `persistent-otp` profile document. With `persistent-otp` inactive, OTP storage stays pure in-memory and no datasource is needed.

```sh
SPRING_PROFILES_ACTIVE=one-time-password,persistent-otp ./gradlew bootRun  # OTP only, backed by Postgres
```

## Behavioral notes

### OTP eviction

A stored OTP entry is removed in any of the following cases:

- **Expiry**: a `verify` after `expiresAt` returns 401 and does not consume an attempt — the in-memory implementation removes the entry on the racing read; the Postgres implementation filters it out with `WHERE expires_at > :now` and the sweeper deletes it.
- **Attempts exhausted**: on the call that decrements the counter to its last unit, the entry is removed regardless of whether the supplied OTP matched. In-memory this is the `before <= 1` branch in `decrementAndCheck`; in Postgres it is the follow-up `DELETE` when the `UPDATE`'s returned `attempts_left` is `0`.
- **Fresh `store`**: calling `generate` for a `userId` that already has a stored entry overwrites it with a brand-new OTP, expiry, and full attempts counter (`Cache.put` for in-memory, `INSERT ... ON CONFLICT DO UPDATE` for Postgres).

For the in-memory implementation, background eviction is driven by Caffeine's TTL housekeeping (its custom `Expiry` is driven by the same `Clock` the rest of the repository uses); lazy checks in `consumeAttempt` remain as defense-in-depth so a verify call racing a sweep still sees the entry as expired immediately. For the Postgres implementation, `OtpSweeper` (scheduled every minute) replaces Caffeine's eviction thread, and the hot-path `WHERE expires_at > :now` predicate is the defense-in-depth equivalent.

### Generation

`DefaultOTPGenerator` draws each character independently from `'A'..'Z'` using a `kotlin.random.Random` view of `java.security.SecureRandom`. The init block rejects non-positive lengths and empty alphabets. Only the length is exposed via `OtpProperties`. Generation is the same under both profiles — only storage and rate limiting differ.

### Rate limiting

Both endpoints call `RateLimiter.acquireOrThrow(userId, operation)` before doing any work. The limiter checks every configured window for the operation (see [Configuration](#configuration)); if any window is at its limit the call throws `RateLimitExceededException` carrying the soonest reset duration, and `GlobalExceptionHandler` maps that to HTTP 429. The in-memory `CaffeineRateLimiter` keeps per-window Caffeine caches keyed by `userId`; the Postgres `JooqRateLimiter` keeps one row per `(user, operation, window bucket)` in `otp_rate_limits` and increments atomically with `INSERT ... ON CONFLICT DO UPDATE ... RETURNING count`.

### Time

The repository and rate limiter read the current time exclusively through an injected `kotlin.time.Clock`. Under the default profile this is `Clock.System`. Under `persistent-otp` it is `PostgresClock`, which reads `statement_timestamp()` from the database so every app instance shares one authoritative clock — most importantly so skewed host clocks cannot disagree on rate-limiter time buckets. Tests substitute a controllable clock (see `MutableClock` in `src/test/kotlin/example/otp`).

### SMS delivery

The production `SMSService` implementation, `NoOpSmsService`, only writes a log line — it does not call any SMS gateway, and it never logs the OTP value. `OtpConfig.smsService(...)` `check(...)`s that the `prod` profile is **not** active: booting under `prod` without a real `SMSService` bean wired in its place fails fast at startup. Integrating a real provider means adding a `prod`-profile `SMSService` bean (or replacing the existing one) in `OtpConfig`.

## Errors

OTP errors are returned through `GlobalExceptionHandler` as a JSON body of shape `{ "status", "error", "message" }`:

- `400 Bad Request` — `InvalidOtpRequestException`. Messages:
  - *userId must not be blank* — `userId` is blank on either endpoint.
  - *otp must not be blank* — `otp` is blank on `/verify`.
- `400 Bad Request` — `HttpMessageNotReadableException`. Triggered by unparseable JSON or a missing required field (e.g. `{}` posted to `/generate`). The response message is the most-specific Jackson cause.
- `401 Unauthorized` — `POST /one-time-password/verify` did not return a match. No body. As described above, this status covers wrong OTP, expired OTP, attempts exhausted, and no-OTP-on-file.
- `429 Too Many Requests` — `RateLimitExceededException`, thrown when a per-user rate-limit window is exceeded on either endpoint. The response carries a `Retry-After` header set to the soonest window-reset duration in whole seconds, and a body of `{"status":"429","error":"Too Many Requests","message":"Rate limit exceeded"}`.

## Examples

Generate an OTP:

```sh
curl -X POST http://localhost:8080/one-time-password/generate \
  -H 'Content-Type: application/json' \
  --data '{"userId":"user-1"}'
# 204 No Content
```

Verify an OTP (success):

```sh
curl -X POST http://localhost:8080/one-time-password/verify \
  -H 'Content-Type: application/json' \
  --data '{"userId":"user-1","otp":"ABCDEF"}'
# 204 No Content
```

Verify an OTP (wrong / expired / exhausted):

```sh
curl -X POST http://localhost:8080/one-time-password/verify \
  -H 'Content-Type: application/json' \
  --data '{"userId":"user-1","otp":"WRONG1"}'
# 401 Unauthorized
```

Reject blank `userId`:

```sh
curl -X POST http://localhost:8080/one-time-password/generate \
  -H 'Content-Type: application/json' \
  --data '{"userId":" "}'
# 400 Bad Request, body: {"status":"400","error":"Bad Request","message":"userId must not be blank"}
```

Hit the rate limit (a second `generate` for the same user within the 30s `short` window):

```sh
curl -X POST http://localhost:8080/one-time-password/generate \
  -H 'Content-Type: application/json' \
  --data '{"userId":"user-1"}'
# 429 Too Many Requests, Retry-After: <seconds>
# body: {"status":"429","error":"Too Many Requests","message":"Rate limit exceeded"}
```
