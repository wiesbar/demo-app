package example.otp

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
@TestConfiguration
internal class MutableClockTestConfig {
    @Bean
    @Primary
    internal fun mutableClock(): MutableClock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))

    @Bean
    internal fun clock(mutableClock: MutableClock): Clock = mutableClock
}
