package example.otp

import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal class CaffeineRateLimiterTest :
    RateLimiterContract(
        newLimiter = { clock, properties -> CaffeineRateLimiter(properties, clock) },
    )
