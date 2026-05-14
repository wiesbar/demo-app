package example.otp

import example.otp.jooq.tables.OtpEntries.OTP_ENTRIES
import example.otp.jooq.tables.records.OtpEntriesRecord
import org.jooq.DSLContext
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant

@OptIn(ExperimentalTime::class)
internal class JooqPasswordRepository(
    private val dsl: DSLContext,
    private val maxAttempts: Int,
    private val otpExpireTime: Duration,
    private val clock: Clock,
    private val hasher: OtpHasher,
) : PasswordRepository {
    override fun store(
        userId: String,
        otp: String,
    ) {
        val expiresAt = (clock.now() + otpExpireTime).toOffsetDateTime()
        dsl
            .insertInto(OTP_ENTRIES)
            .set(OTP_ENTRIES.USER_ID, userId)
            .set(OTP_ENTRIES.OTP_HASH, hasher.hash(otp))
            .set(OTP_ENTRIES.OTP_ALGO, hasher.algorithm)
            .set(OTP_ENTRIES.EXPIRES_AT, expiresAt)
            .set(OTP_ENTRIES.ATTEMPTS_LEFT, maxAttempts)
            .onConflict(OTP_ENTRIES.USER_ID)
            .doUpdate()
            .set(OTP_ENTRIES.OTP_HASH, hasher.hash(otp))
            .set(OTP_ENTRIES.OTP_ALGO, hasher.algorithm)
            .set(OTP_ENTRIES.EXPIRES_AT, expiresAt)
            .set(OTP_ENTRIES.ATTEMPTS_LEFT, maxAttempts)
            .execute()
    }

    override fun consumeAttempt(
        userId: String,
        providedOtp: String,
    ): Boolean {
        val row = decrement(userId) ?: return false
        if (row.attemptsLeft == 0) deleteExhausted(userId)
        return hasher.matches(providedOtp, row.otpHash, row.otpAlgo)
    }

    private fun decrement(userId: String): OtpEntriesRecord? =
        dsl
            .update(OTP_ENTRIES)
            .set(OTP_ENTRIES.ATTEMPTS_LEFT, OTP_ENTRIES.ATTEMPTS_LEFT.minus(1))
            .where(OTP_ENTRIES.USER_ID.eq(userId))
            .and(OTP_ENTRIES.EXPIRES_AT.gt(clock.now().toOffsetDateTime()))
            .and(OTP_ENTRIES.ATTEMPTS_LEFT.gt(0))
            .returning(OTP_ENTRIES.OTP_HASH, OTP_ENTRIES.OTP_ALGO, OTP_ENTRIES.ATTEMPTS_LEFT)
            .fetchOne()

    private fun deleteExhausted(userId: String) {
        dsl
            .deleteFrom(OTP_ENTRIES)
            .where(OTP_ENTRIES.USER_ID.eq(userId))
            .and(OTP_ENTRIES.ATTEMPTS_LEFT.eq(0))
            .execute()
    }

    private fun Instant.toOffsetDateTime(): OffsetDateTime = OffsetDateTime.ofInstant(toJavaInstant(), ZoneOffset.UTC)
}
