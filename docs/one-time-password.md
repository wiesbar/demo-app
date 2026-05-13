# One-time password

The `example.otp` package implements a per-user one-time-password (OTP) flow: a caller asks for an OTP for a given `userId` and then later verifies a candidate OTP for that same user. It exposes a single application-level service `OTPService`, which composes three internal ports — `OTPGenerator` (produces the OTP), `PasswordRepository` (stores it with an expiry and a remaining-attempts counter), and `SMSService` (delivers it out-of-band). The HTTP surface lives in `example.web.OtpController`, with request payloads in `example.web.OtpDto`.

## HTTP endpoints

Both endpoints are gated by the `one-time-password` Spring profile and rooted at `/one-time-password`.

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

## Domain / package layout

All types in `example.otp` are `internal` to keep Spring/HTTP concerns out of the package; wiring lives in `example.config.OtpConfig`.

| Type                            | Notes                                                                                                                                                  |
|---------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| `OTPService`                    | Application service. `generate(userId)` produces an OTP, stores it, and sends it. `verify(userId, otp)` delegates to `PasswordRepository.consumeAttempt`. |
| `OTPGenerator` / `DefaultOTPGenerator` | `fun interface OTPGenerator { fun generate(): String }`. The production implementation produces a 6-character string from `'A'..'Z'`, drawn with `SecureRandom().asKotlinRandom()`. Configurable `length`, `allowedChars`, and `random`. |
| `PasswordRepository` / `DefaultPasswordRepository` | In-memory Caffeine `Cache<String, OtpEntry>` with a custom `Expiry` and `Ticker` both driven by the injected `kotlin.time.Clock`. Each entry holds the OTP, an `expiresAt: Instant` (`kotlin.time.Instant`), and an `AtomicInteger attemptsLeft`. Production wires `Clock.System`. |
| `SMSService` / `LoggingSmsService` | The delivery port. The production implementation only logs `"Sending one time password to user '<userId>'"` and does **not** send any real SMS. The OTP value itself is not logged. |
| `InvalidOtpRequestException`    | `RuntimeException` thrown by `OtpController` validation. `GlobalExceptionHandler` maps it to HTTP 400.                                                 |

## Configuration

Bound from `application.yaml` via `OtpProperties` (`@ConfigurationProperties(prefix = "one-time-password")`):

```yaml
one-time-password:
  length: 6
  max-attempts: 3
  expire-time: 5m
```

| Property                       | Default | Description                                                                                  |
|--------------------------------|---------|----------------------------------------------------------------------------------------------|
| `one-time-password.length`     | `6`     | Number of characters in each OTP. Passed to `DefaultOTPGenerator(length = …)`.               |
| `one-time-password.max-attempts` | `3`   | Maximum number of `verify` attempts allowed per stored OTP. Passed to `DefaultPasswordRepository(maxAttempts = …)`. |
| `one-time-password.expire-time`  | `5m`  | How long a stored OTP stays valid after `generate`. Bound as `java.time.Duration` and converted to `kotlin.time.Duration` before reaching `DefaultPasswordRepository`. |

The OTP alphabet, the `SecureRandom` source, and the `Clock` are not externally configurable — overriding them requires changing the bean wiring in `OtpConfig`.

## Profile

All OTP beans (`OTPGenerator`, `PasswordRepository`, `SMSService`, `OTPService`) and the HTTP controller are gated by the Spring profile `one-time-password`. With this profile inactive, neither endpoint is registered and no OTP state is held. The profile is active by default through `spring.profiles.default=calculator,catalog,one-time-password` in `application.yaml`. To disable it, set `SPRING_PROFILES_ACTIVE` to a profile set that omits `one-time-password`:

```sh
SPRING_PROFILES_ACTIVE=calculator,catalog ./gradlew bootRun
```

Note that the string `one-time-password` is used in three independent places: as the Spring profile name, as the configuration-property prefix, and as the URI root of the HTTP endpoints. These are intentionally aligned but conceptually distinct.

## Behavioral notes

### OTP eviction

A stored OTP entry is removed from the in-memory map in any of the following cases:

- **Expiry**: the next `verify` after `expiresAt` removes the entry and returns 401.
- **Attempts exhausted**: on the call that decrements the counter to zero (`before <= 1` in `decrementAndCheck`), the entry is removed regardless of whether the supplied OTP matched.
- **Fresh `store`**: calling `generate` for a `userId` that already has a stored entry overwrites it with a brand-new OTP, expiry, and full attempts counter.

Eviction is driven by Caffeine's TTL housekeeping (its custom `Expiry` is driven by the same `Clock` the rest of the repository uses); 
lazy checks in `consumeAttempt` remain as defense-in-depth so a verify call racing a sweep still sees the entry as expired immediately. 
Eager removal still happens at decrement time when attempts are exhausted, and a successful verify call also drops the entry.

### Generation

`DefaultOTPGenerator` draws each character independently from `'A'..'Z'` using a `kotlin.random.Random` view of `java.security.SecureRandom`. The init block rejects non-positive lengths and empty alphabets. Only the length is exposed via `OtpProperties`.

### Time

`DefaultPasswordRepository` reads the current time exclusively through an injected `kotlin.time.Clock`. Production wires `Clock.System`; tests can substitute a controllable clock (see `MutableClock` in `src/test/kotlin/example/otp`).

### SMS delivery

The production `SMSService` implementation, `LoggingSmsService`, only writes a log line — it does not call any SMS gateway. Integrating a real provider means adding a new `SMSService` bean (or replacing the existing one) in `OtpConfig`.

## Errors

OTP errors are returned through `GlobalExceptionHandler` as a JSON body of shape `{ "status", "error", "message" }`:

- `400 Bad Request` — `InvalidOtpRequestException`. Messages:
  - *userId must not be blank* — `userId` is blank on either endpoint.
  - *otp must not be blank* — `otp` is blank on `/verify`.
- `400 Bad Request` — `HttpMessageNotReadableException`. Triggered by unparseable JSON or a missing required field (e.g. `{}` posted to `/generate`). The response message is the most-specific Jackson cause.
- `401 Unauthorized` — `POST /one-time-password/verify` did not return a match. No body. As described above, this status covers wrong OTP, expired OTP, attempts exhausted, and no-OTP-on-file.

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
