package example.otp

import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

internal interface PasswordRepository {
    fun store(
        userId: String,
        otp: String,
    )

    fun consumeAttempt(
        userId: String,
        providedOtp: String,
    ): Boolean
}

// todo - temporary implementation
@OptIn(ExperimentalTime::class)
internal class DefaultPasswordRepository(
    private val maxAttempts: Int,
    private val otpExpireTime: Duration,
    private val clock: Clock = Clock.System,
) : PasswordRepository {
    private val entries = cacheWith(clock, EntryExpiry(clock))

    override fun store(
        userId: String,
        otp: String,
    ) {
        entries.put(userId, toOtpEntry(otp))
    }

    override fun consumeAttempt(
        userId: String,
        providedOtp: String,
    ): Boolean =
        entries.getIfPresent(userId)?.let { entry ->
            isLive(userId, entry) && decrementAndCheck(userId, entry, providedOtp)
        } ?: false

    internal fun forceCleanUp() {
        entries.cleanUp()
    }

    internal fun containsEntry(userId: String): Boolean = entries.getIfPresent(userId) != null

    private fun toOtpEntry(otp: String): OtpEntry =
        OtpEntry(
            otp,
            clock.now() + otpExpireTime,
            AtomicInteger(maxAttempts),
        )

    private fun isLive(
        userId: String,
        entry: OtpEntry,
    ): Boolean =
        (clock.now() < entry.expiresAt).also { alive ->
            if (!alive) entries.asMap().remove(userId, entry)
        }

    private fun decrementAndCheck(
        userId: String,
        entry: OtpEntry,
        providedOtp: String,
    ): Boolean {
        val before = entry.attemptsLeft.getAndDecrement()
        if (before <= 1) entries.asMap().remove(userId, entry)
        return ensureNoRaceCondition(before) && entry.otp == providedOtp
    }

    private fun ensureNoRaceCondition(before: Int): Boolean = before > 0
}
