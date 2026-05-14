package example.otp

import example.otp.jooq.Tables.OTP_ENTRIES
import example.otp.jooq.Tables.OTP_RATE_LIMITS
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant

@OptIn(ExperimentalTime::class)
internal class OtpSweeperTest :
    FunSpec({
        val dsl = OtpPostgresContainer.dsl
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val clock = MutableClock(now)

        fun insertEntry(
            userId: String,
            expiresAt: Instant,
        ) {
            dsl
                .insertInto(OTP_ENTRIES)
                .set(OTP_ENTRIES.USER_ID, userId)
                .set(OTP_ENTRIES.OTP_HASH, byteArrayOf(1, 2, 3))
                .set(OTP_ENTRIES.OTP_ALGO, "HMAC-SHA-256")
                .set(OTP_ENTRIES.EXPIRES_AT, expiresAt.toOffsetDateTime())
                .set(OTP_ENTRIES.ATTEMPTS_LEFT, 3)
                .execute()
        }

        fun insertRateLimit(
            userId: String,
            expiresAt: Instant,
        ) {
            dsl
                .insertInto(OTP_RATE_LIMITS)
                .set(OTP_RATE_LIMITS.USER_ID, userId)
                .set(OTP_RATE_LIMITS.OPERATION, "GENERATE")
                .set(OTP_RATE_LIMITS.WINDOW_KEY, "short:1")
                .set(OTP_RATE_LIMITS.COUNT, 1)
                .set(OTP_RATE_LIMITS.WINDOW_STARTED_AT, now.toOffsetDateTime())
                .set(OTP_RATE_LIMITS.EXPIRES_AT, expiresAt.toOffsetDateTime())
                .execute()
        }

        beforeTest {
            dsl.truncate(OTP_ENTRIES).execute()
            dsl.truncate(OTP_RATE_LIMITS).execute()
        }

        test("sweep deletes expired rows from both tables") {
            insertEntry("expired-user", now - 1.minutes)
            insertRateLimit("expired-user", now - 1.minutes)

            OtpSweeper(dsl, clock).sweep()

            dsl.fetchCount(OTP_ENTRIES) shouldBe 0
            dsl.fetchCount(OTP_RATE_LIMITS) shouldBe 0
        }

        test("sweep keeps live rows in both tables") {
            insertEntry("live-user", now + 5.minutes)
            insertRateLimit("live-user", now + 5.minutes)

            OtpSweeper(dsl, clock).sweep()

            dsl.fetchCount(OTP_ENTRIES) shouldBe 1
            dsl.fetchCount(OTP_RATE_LIMITS) shouldBe 1
        }

        test("sweep is harmless when both tables are empty") {
            OtpSweeper(dsl, clock).sweep()

            dsl.fetchCount(OTP_ENTRIES) shouldBe 0
            dsl.fetchCount(OTP_RATE_LIMITS) shouldBe 0
        }
    })

@OptIn(ExperimentalTime::class)
private fun Instant.toOffsetDateTime(): OffsetDateTime = OffsetDateTime.ofInstant(toJavaInstant(), ZoneOffset.UTC)
