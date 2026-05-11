package example.otp

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
internal class MutableClock(
    private var instant: Instant,
) : Clock {
    override fun now(): Instant = instant

    fun advance(duration: Duration) {
        instant += duration
    }
}
