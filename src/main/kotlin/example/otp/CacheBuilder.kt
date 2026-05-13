package example.otp

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import com.github.benmanes.caffeine.cache.Ticker
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.Duration as KotlinDuration

private const val NANOS_PER_MILLI = 1_000_000L

@OptIn(ExperimentalTime::class)
internal inline fun <reified T : Any> cacheWith(
    clock: Clock,
    expiry: Expiry<String, T>,
): Cache<String, T> =
    Caffeine
        .newBuilder()
        .ticker(clock.toCaffeineTicker())
        .expireAfter(expiry)
        .build()

@OptIn(ExperimentalTime::class)
private fun Clock.toCaffeineTicker(): Ticker = Ticker { now().toEpochMilliseconds() * NANOS_PER_MILLI }

@OptIn(ExperimentalTime::class)
internal class EntryExpiry(
    private val clock: Clock,
) : Expiry<String, OtpEntry> {
    override fun expireAfterCreate(
        key: String,
        value: OtpEntry,
        currentTime: Long,
    ): Long = nanosUntilExpiry(value)

    override fun expireAfterUpdate(
        key: String,
        value: OtpEntry,
        currentTime: Long,
        currentDuration: Long,
    ): Long = nanosUntilExpiry(value)

    override fun expireAfterRead(
        key: String,
        value: OtpEntry,
        currentTime: Long,
        currentDuration: Long,
    ): Long = currentDuration

    private fun nanosUntilExpiry(entry: OtpEntry): Long {
        val remaining = entry.expiresAt - clock.now()
        return remaining.inWholeNanoseconds.coerceAtLeast(0L)
    }
}

@OptIn(ExperimentalTime::class)
internal data class OtpEntry(
    val otp: String,
    val expiresAt: Instant,
    val attemptsLeft: AtomicInteger,
)

internal class WindowExpiry(
    window: KotlinDuration,
) : Expiry<String, AtomicInteger> {
    private val windowNanos: Long = window.inWholeNanoseconds

    override fun expireAfterCreate(
        key: String,
        value: AtomicInteger,
        currentTime: Long,
    ): Long = windowNanos

    override fun expireAfterUpdate(
        key: String,
        value: AtomicInteger,
        currentTime: Long,
        currentDuration: Long,
    ): Long = currentDuration

    override fun expireAfterRead(
        key: String,
        value: AtomicInteger,
        currentTime: Long,
        currentDuration: Long,
    ): Long = currentDuration
}
