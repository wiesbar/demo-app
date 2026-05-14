package example.otp

import example.config.OperationWindows
import example.config.RateLimitProperties
import example.config.WindowSpec
import example.otp.jooq.tables.OtpRateLimits.OTP_RATE_LIMITS
import org.jooq.DSLContext
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant

@OptIn(ExperimentalTime::class)
internal class JooqRateLimiter(
    private val dsl: DSLContext,
    properties: RateLimitProperties,
    private val clock: Clock,
) : RateLimiter {
    private val windows: Map<RateLimitedOperation, List<NamedWindow>> =
        mapOf(
            RateLimitedOperation.GENERATE to namedWindows(properties.generate),
            RateLimitedOperation.VERIFY to namedWindows(properties.verify),
        )

    override fun tryAcquire(
        userId: String,
        operation: RateLimitedOperation,
    ): TryAcquireResult {
        val now = clock.now()
        val acquired = mutableListOf<NamedWindow>()
        for (window in windows.getValue(operation)) {
            if (incrementAndCheck(userId, operation, window, now)) {
                acquired += window
            } else {
                acquired.forEach { release(userId, operation, it, now) }
                return TryAcquireResult.Denied(window.retryAfter(now))
            }
        }
        return TryAcquireResult.Acquired
    }

    private fun incrementAndCheck(
        userId: String,
        operation: RateLimitedOperation,
        window: NamedWindow,
        now: Instant,
    ): Boolean {
        val count =
            dsl
                .insertInto(OTP_RATE_LIMITS)
                .set(OTP_RATE_LIMITS.USER_ID, userId)
                .set(OTP_RATE_LIMITS.OPERATION, operation.name)
                .set(OTP_RATE_LIMITS.WINDOW_KEY, window.key(now))
                .set(OTP_RATE_LIMITS.COUNT, 1)
                .set(OTP_RATE_LIMITS.WINDOW_STARTED_AT, window.startedAt(now).toOffsetDateTime())
                .set(OTP_RATE_LIMITS.EXPIRES_AT, window.expiresAt(now).toOffsetDateTime())
                .onConflict(OTP_RATE_LIMITS.USER_ID, OTP_RATE_LIMITS.OPERATION, OTP_RATE_LIMITS.WINDOW_KEY)
                .doUpdate()
                .set(OTP_RATE_LIMITS.COUNT, OTP_RATE_LIMITS.COUNT.plus(1))
                .returning(OTP_RATE_LIMITS.COUNT)
                .fetchOne(OTP_RATE_LIMITS.COUNT) ?: 0
        return count <= window.spec.limit
    }

    private fun release(
        userId: String,
        operation: RateLimitedOperation,
        window: NamedWindow,
        now: Instant,
    ) {
        dsl
            .update(OTP_RATE_LIMITS)
            .set(OTP_RATE_LIMITS.COUNT, OTP_RATE_LIMITS.COUNT.minus(1))
            .where(OTP_RATE_LIMITS.USER_ID.eq(userId))
            .and(OTP_RATE_LIMITS.OPERATION.eq(operation.name))
            .and(OTP_RATE_LIMITS.WINDOW_KEY.eq(window.key(now)))
            .execute()
    }

    private fun namedWindows(windows: OperationWindows): List<NamedWindow> =
        listOfNotNull(
            windows.short?.let { NamedWindow("short", it) },
            NamedWindow("long", windows.long),
        )

    private fun Instant.toOffsetDateTime(): OffsetDateTime = OffsetDateTime.ofInstant(toJavaInstant(), ZoneOffset.UTC)

    private data class NamedWindow(
        val name: String,
        val spec: WindowSpec,
    ) {
        private val windowMillis: Long = spec.window.inWholeMilliseconds

        private fun bucket(now: Instant): Long = now.toEpochMilliseconds() / windowMillis

        fun key(now: Instant): String = "$name:${bucket(now)}"

        fun startedAt(now: Instant): Instant = Instant.fromEpochMilliseconds(bucket(now) * windowMillis)

        fun expiresAt(now: Instant): Instant = startedAt(now) + spec.window

        fun retryAfter(now: Instant): Duration = expiresAt(now) - now
    }
}
