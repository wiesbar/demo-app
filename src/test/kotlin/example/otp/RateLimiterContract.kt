package example.otp

import example.config.OperationWindows
import example.config.RateLimitProperties
import example.config.WindowSpec
import example.otp.RateLimitedOperation.GENERATE
import example.otp.RateLimitedOperation.VERIFY
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
abstract class RateLimiterContract(
    reset: suspend () -> Unit = {},
    newLimiter: (clock: Clock, properties: RateLimitProperties) -> RateLimiter,
) : FunSpec({
        val generateShort = WindowSpec.of(limit = 1, window = 30.seconds)
        val generateLong = WindowSpec.of(limit = 5, window = 1.hours)
        val verifyLong = WindowSpec.of(limit = 10, window = 5.minutes)
        val props =
            RateLimitProperties(
                generate = OperationWindows(short = generateShort, long = generateLong),
                verify = OperationWindows(short = null, long = verifyLong),
            )

        fun newClock() = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))

        beforeTest { reset() }

        test("short window denies a second generate within 30s then resets") {
            val clock = newClock()
            val limiter = newLimiter(clock, props)

            limiter.tryAcquire("u", GENERATE).shouldBeInstanceOf<TryAcquireResult.Acquired>()
            limiter.tryAcquire("u", GENERATE).shouldBeInstanceOf<TryAcquireResult.Denied>()

            clock.advance(30.seconds)
            limiter.tryAcquire("u", GENERATE).shouldBeInstanceOf<TryAcquireResult.Acquired>()
        }

        test("long window denies the sixth generate within an hour") {
            val clock = newClock()
            val limiter = newLimiter(clock, props)

            repeat(5) {
                clock.advance(31.seconds)
                limiter
                    .tryAcquire("u", GENERATE)
                    .shouldBeInstanceOf<TryAcquireResult.Acquired>()
            }
            clock.advance(31.seconds)
            limiter.tryAcquire("u", GENERATE).shouldBeInstanceOf<TryAcquireResult.Denied>()

            clock.advance(1.hours)
            limiter.tryAcquire("u", GENERATE).shouldBeInstanceOf<TryAcquireResult.Acquired>()
        }

        test("short window resets independently of long window") {
            val clock = newClock()
            val limiter = newLimiter(clock, props)

            limiter.tryAcquire("u", GENERATE).shouldBeInstanceOf<TryAcquireResult.Acquired>()
            clock.advance(31.seconds)
            limiter.tryAcquire("u", GENERATE).shouldBeInstanceOf<TryAcquireResult.Acquired>()
        }

        test("limits are per-user") {
            val clock = newClock()
            val limiter = newLimiter(clock, props)

            limiter.tryAcquire("a", GENERATE).shouldBeInstanceOf<TryAcquireResult.Acquired>()
            limiter.tryAcquire("a", GENERATE).shouldBeInstanceOf<TryAcquireResult.Denied>()
            limiter.tryAcquire("b", GENERATE).shouldBeInstanceOf<TryAcquireResult.Acquired>()
        }

        test("limits are per-operation") {
            val clock = newClock()
            val limiter = newLimiter(clock, props)

            limiter.tryAcquire("u", GENERATE).shouldBeInstanceOf<TryAcquireResult.Acquired>()
            limiter.tryAcquire("u", GENERATE).shouldBeInstanceOf<TryAcquireResult.Denied>()
            limiter.tryAcquire("u", VERIFY).shouldBeInstanceOf<TryAcquireResult.Acquired>()
        }

        test("concurrent tryAcquire enforces the limit exactly") {
            runTest {
                val clock = newClock()
                val parallelCalls = 10
                val verifyOnly =
                    RateLimitProperties(
                        generate = OperationWindows(short = null, long = WindowSpec.of(limit = 100, window = 1.hours)),
                        verify = OperationWindows(short = null, long = WindowSpec.of(limit = 5, window = 5.minutes)),
                    )
                val limiter = newLimiter(clock, verifyOnly)

                val results =
                    (1..parallelCalls)
                        .map { async(Dispatchers.Default) { limiter.tryAcquire("user", VERIFY) } }
                        .awaitAll()

                withClue("results=$results") {
                    results.count { it is TryAcquireResult.Acquired } shouldBeEqual 5
                    results.count { it is TryAcquireResult.Denied } shouldBeEqual 5
                }
            }
        }

        test("denied retryAfter is the shortest tripped window") {
            val clock = newClock()
            val limiter = newLimiter(clock, props)

            limiter.tryAcquire("u", GENERATE).shouldBeInstanceOf<TryAcquireResult.Acquired>()
            val denied =
                limiter
                    .tryAcquire("u", GENERATE)
                    .shouldBeInstanceOf<TryAcquireResult.Denied>()

            denied.retryAfter shouldBeLessThanOrEqualTo 30.seconds
            denied.retryAfter shouldBeGreaterThan 0.seconds
        }

        test("generate short window with limit greater than one allows the configured burst") {
            val clock = newClock()
            val burstProps =
                RateLimitProperties(
                    generate =
                        OperationWindows(
                            short = WindowSpec.of(limit = 2, window = 30.seconds),
                            long = WindowSpec.of(limit = 100, window = 1.hours),
                        ),
                    verify = OperationWindows(short = null, long = verifyLong),
                )
            val limiter = newLimiter(clock, burstProps)

            limiter.tryAcquire("u", GENERATE).shouldBeInstanceOf<TryAcquireResult.Acquired>()
            limiter.tryAcquire("u", GENERATE).shouldBeInstanceOf<TryAcquireResult.Acquired>()
            limiter.tryAcquire("u", GENERATE).shouldBeInstanceOf<TryAcquireResult.Denied>()

            clock.advance(30.seconds)
            limiter.tryAcquire("u", GENERATE).shouldBeInstanceOf<TryAcquireResult.Acquired>()
        }

        test("long window is the binding constraint when it is tighter than the short window") {
            val clock = newClock()
            val tightLongProps =
                RateLimitProperties(
                    generate =
                        OperationWindows(
                            short = WindowSpec.of(limit = 5, window = 30.seconds),
                            long = WindowSpec.of(limit = 3, window = 1.hours),
                        ),
                    verify = OperationWindows(short = null, long = verifyLong),
                )
            val limiter = newLimiter(clock, tightLongProps)

            repeat(3) {
                limiter.tryAcquire("u", GENERATE).shouldBeInstanceOf<TryAcquireResult.Acquired>()
                clock.advance(31.seconds)
            }
            val denied =
                limiter
                    .tryAcquire("u", GENERATE)
                    .shouldBeInstanceOf<TryAcquireResult.Denied>()

            withClue("retryAfter=${denied.retryAfter}") {
                denied.retryAfter shouldBeGreaterThan 30.seconds
                denied.retryAfter shouldBeLessThanOrEqualTo 1.hours
            }
        }

        test("operation without a short window only enforces the long window") {
            val clock = newClock()
            val limiter = newLimiter(clock, props)

            repeat(10) {
                limiter.tryAcquire("u", VERIFY).shouldBeInstanceOf<TryAcquireResult.Acquired>()
            }
            limiter.tryAcquire("u", VERIFY).shouldBeInstanceOf<TryAcquireResult.Denied>()
        }

        test("retryAfter falls within the long window when only long is tripped") {
            val clock = newClock()
            val limiter = newLimiter(clock, props)

            repeat(5) {
                limiter.tryAcquire("u", GENERATE).shouldBeInstanceOf<TryAcquireResult.Acquired>()
                clock.advance(31.seconds)
            }
            val denied =
                limiter
                    .tryAcquire("u", GENERATE)
                    .shouldBeInstanceOf<TryAcquireResult.Denied>()

            withClue("retryAfter=${denied.retryAfter}") {
                denied.retryAfter shouldBeGreaterThan 0.seconds
                denied.retryAfter shouldBeLessThanOrEqualTo 1.hours
            }
        }

        test("advancing past the long window resets the entire per-operation budget") {
            val clock = newClock()
            val limiter = newLimiter(clock, props)

            repeat(5) {
                limiter.tryAcquire("u", GENERATE).shouldBeInstanceOf<TryAcquireResult.Acquired>()
                clock.advance(31.seconds)
            }
            limiter.tryAcquire("u", GENERATE).shouldBeInstanceOf<TryAcquireResult.Denied>()

            clock.advance(1.hours)
            repeat(5) {
                limiter.tryAcquire("u", GENERATE).shouldBeInstanceOf<TryAcquireResult.Acquired>()
                clock.advance(31.seconds)
            }
        }

        test("interleaved generate and verify for the same user stay independent") {
            val clock = newClock()
            val limiter = newLimiter(clock, props)

            limiter.tryAcquire("u", GENERATE).shouldBeInstanceOf<TryAcquireResult.Acquired>()
            limiter.tryAcquire("u", VERIFY).shouldBeInstanceOf<TryAcquireResult.Acquired>()
            limiter.tryAcquire("u", GENERATE).shouldBeInstanceOf<TryAcquireResult.Denied>()
            repeat(9) {
                limiter.tryAcquire("u", VERIFY).shouldBeInstanceOf<TryAcquireResult.Acquired>()
            }
            limiter.tryAcquire("u", VERIFY).shouldBeInstanceOf<TryAcquireResult.Denied>()
        }

        test("acquireOrThrow raises RateLimitExceededException with the same retryAfter as tryAcquire") {
            val clock = newClock()
            val limiter = newLimiter(clock, props)

            limiter.acquireOrThrow("u", GENERATE)
            val denied =
                limiter
                    .tryAcquire("u", GENERATE)
                    .shouldBeInstanceOf<TryAcquireResult.Denied>()
            val parallelLimiter = newLimiter(clock, props)
            parallelLimiter.acquireOrThrow("v", GENERATE)
            val thrown =
                shouldThrow<RateLimitExceededException> {
                    parallelLimiter.acquireOrThrow("v", GENERATE)
                }

            withClue("thrown.retryAfter=${thrown.retryAfter} denied.retryAfter=${denied.retryAfter}") {
                thrown.retryAfter shouldBeGreaterThan 0.seconds
                thrown.retryAfter shouldBeLessThanOrEqualTo 30.seconds
                denied.retryAfter shouldBeGreaterThan 0.seconds
            }
        }
    })
