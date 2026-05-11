package example.otp

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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

@OptIn(ExperimentalTime::class)
internal class DefaultPasswordRepository(
    private val maxAttempts: Int = 3,
    private val otpExpireTime: Duration = 5.minutes,
    private val clock: Clock = Clock.System,
) : PasswordRepository {
    private val entries = ConcurrentHashMap<String, OtpEntry>()

    override fun store(
        userId: String,
        otp: String,
    ) {
        entries[userId] = toOtpEntry(otp)
    }

    override fun consumeAttempt(
        userId: String,
        providedOtp: String,
    ): Boolean =
        entries[userId]?.let { entry ->
            isLive(userId, entry) && decrementAndCheck(userId, entry, providedOtp)
        } ?: false

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
            if (!alive) entries.remove(userId, entry)
        }

    private fun decrementAndCheck(
        userId: String,
        entry: OtpEntry,
        providedOtp: String,
    ): Boolean {
        val before = entry.attemptsLeft.getAndDecrement()
        if (before <= 1) entries.remove(userId, entry)
        return ensureNoRaceCondition(before) && entry.otp == providedOtp
    }

    private fun ensureNoRaceCondition(before: Int): Boolean = before > 0

    private data class OtpEntry(
        val otp: String,
        val expiresAt: Instant,
        val attemptsLeft: AtomicInteger,
    )
}
