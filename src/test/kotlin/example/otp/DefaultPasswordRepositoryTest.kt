package example.otp

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
@Suppress("MagicNumber")
class DefaultPasswordRepositoryTest :
    FunSpec({
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val repo =
            DefaultPasswordRepository(
                maxAttempts = 3,
                otpExpireTime = 5.minutes,
                clock = clock,
            )

        test("consumeAttempt returns false when no entry stored") {
            repo.consumeAttempt("some-user", "A") shouldBe false
        }

        test("store then consumeAttempt with correct otp returns true") {
            repo.store("some-user", "A")

            repo.consumeAttempt("some-user", "A") shouldBe true
        }

        test("store then consumeAttempt with wrong otp returns false") {
            repo.store("some-user", "A")

            repo.consumeAttempt("some-user", "wrong") shouldBe false
        }

        test("store resets the attempt counter") {
            val repo =
                DefaultPasswordRepository(
                    maxAttempts = 2,
                    otpExpireTime = 5.minutes,
                    clock = clock,
                )
            repo.store("some-user", "A")
            repo.consumeAttempt("some-user", "wrong")
            repo.consumeAttempt("some-user", "wrong")

            repo.store("some-user", "B")

            repo.consumeAttempt("some-user", "B") shouldBe true
            repo.consumeAttempt("some-user", "B") shouldBe true
        }

        test("consumeAttempt returns false after maxAttempts exhausted, even with correct otp") {
            val repo =
                DefaultPasswordRepository(
                    maxAttempts = 2,
                    otpExpireTime = 5.minutes,
                    clock = clock,
                )
            repo.store("some-user", "A")

            repo.consumeAttempt("some-user", "wrong") shouldBe false
            repo.consumeAttempt("some-user", "wrong") shouldBe false
            repo.consumeAttempt("some-user", "A") shouldBe false
        }

        test("consumeAttempt returns false once otpExpireTime has elapsed") {
            repo.store("some-user", "A")
            clock.advance(5.minutes)

            repo.consumeAttempt("some-user", "A") shouldBe false
        }

        test("consumeAttempt returns true just before expiry") {
            repo.store("some-user", "A")
            clock.advance(5.minutes - 1.seconds)

            repo.consumeAttempt("some-user", "A") shouldBe true
        }

        test("attempt counters are independent per user") {
            val repo =
                DefaultPasswordRepository(
                    maxAttempts = 2,
                    otpExpireTime = 5.minutes,
                    clock = clock,
                )
            repo.store("user-a", "A")
            repo.store("user-b", "B")
            repeat(2) { repo.consumeAttempt("user-a", "wrong") }

            repo.consumeAttempt("user-a", "A") shouldBe false
            repo.consumeAttempt("user-b", "B") shouldBe true
        }

        test("expiry is tracked independently per user") {
            repo.store("user-a", "A")
            clock.advance(2.minutes)
            repo.store("user-b", "B")
            clock.advance(3.minutes + 1.seconds)

            repo.consumeAttempt("user-a", "A") shouldBe false
            repo.consumeAttempt("user-b", "B") shouldBe true
        }

        test("parallel consumeAttempt calls never over-consumes due to race conditions") {
            runTest {
                val iterations = 500
                val parallelCalls = 10
                val maxAttempts = 5
                val repo =
                    DefaultPasswordRepository(maxAttempts = maxAttempts, otpExpireTime = 5.minutes, clock = clock)
                (1..iterations).forEach { iteration ->
                    val userId = "some-user-$iteration"
                    repo.store(userId, "A")
                    val results =
                        (1..parallelCalls)
                            .map { async(Dispatchers.Default) { repo.consumeAttempt(userId, "A") } }
                            .awaitAll()
                    withClue("[iteration=$iteration] results=$results") {
                        results.count { it } shouldBeEqual maxAttempts
                    }
                }
            }
        }
    })
