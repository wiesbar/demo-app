package example.otp

import example.otp.jooq.tables.OtpEntries.OTP_ENTRIES
import example.otp.jooq.tables.OtpRateLimits.OTP_RATE_LIMITS
import org.jooq.DSLContext
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

@OptIn(ExperimentalTime::class)
internal class OtpSweeper(
    private val dsl: DSLContext,
    private val clock: Clock,
) {
    fun sweep() {
        val now = OffsetDateTime.ofInstant(clock.now().toJavaInstant(), ZoneOffset.UTC)
        dsl.deleteFrom(OTP_ENTRIES).where(OTP_ENTRIES.EXPIRES_AT.le(now)).execute()
        dsl.deleteFrom(OTP_RATE_LIMITS).where(OTP_RATE_LIMITS.EXPIRES_AT.le(now)).execute()
    }
}
