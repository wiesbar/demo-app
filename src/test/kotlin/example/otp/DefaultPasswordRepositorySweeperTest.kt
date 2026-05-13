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
class DefaultPasswordRepositorySweeperTest :
    FunSpec({
        val expireTime = 5.minutes
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))

        fun freshRepo(clock: MutableClock): DefaultPasswordRepository =
            DefaultPasswordRepository(
                maxAttempts = 3,
                otpExpireTime = expireTime,
                clock = clock,
            )

        test("containsEntry reflects store") {
            val repo = freshRepo(clock)

            repo.containsEntry("u") shouldBe false
            repo.store("u", "A")
            repo.containsEntry("u") shouldBe true
        }

        test("expired entry is removed by forceCleanUp") {
            val repo = freshRepo(clock)
            repo.store("u", "A")
            clock.advance(expireTime + 1.seconds)

            repo.forceCleanUp()

            repo.containsEntry("u") shouldBe false
        }

        test("forceCleanUp is harmless when no entries exist") {
            val repo = freshRepo(clock)

            repo.forceCleanUp()

            repo.containsEntry("u") shouldBe false
        }

        test("forceCleanUp keeps live entries") {
            val repo = freshRepo(clock)
            repo.store("u", "A")
            clock.advance(expireTime - 1.seconds)

            repo.forceCleanUp()

            repo.containsEntry("u") shouldBe true
        }

        test("consumeAttempt remains correct after expiry regardless of forceCleanUp") {
            val repo = freshRepo(clock)
            repo.store("u", "A")
            clock.advance(expireTime + 1.seconds)
            repo.forceCleanUp()

            repo.consumeAttempt("u", "A") shouldBe false
        }

        test("concurrent consumeAttempt with parallel forceCleanUp preserves attempt limit") {
            runTest {
                val iterations = 200
                val parallelCalls = 10
                val maxAttempts = 5
                val repo =
                    DefaultPasswordRepository(
                        maxAttempts = maxAttempts,
                        otpExpireTime = expireTime,
                        clock = clock,
                    )
                (1..iterations).forEach { iteration ->
                    val userId = "user-$iteration"
                    repo.store(userId, "A")
                    val results =
                        (1..parallelCalls)
                            .map {
                                async(Dispatchers.Default) {
                                    if (it == 1) {
                                        repo.forceCleanUp()
                                        false
                                    } else {
                                        repo.consumeAttempt(userId, "A")
                                    }
                                }
                            }.awaitAll()
                    withClue("[iteration=$iteration] results=$results") {
                        results.count { it } shouldBeEqual maxAttempts
                    }
                }
            }
        }
    })
