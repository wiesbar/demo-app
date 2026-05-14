package example.otp

import example.otp.jooq.Tables.OTP_RATE_LIMITS
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal class JooqRateLimiterTest :
    RateLimiterContract(
        reset = { OtpPostgresContainer.dsl.truncate(OTP_RATE_LIMITS).execute() },
        newLimiter = { clock, properties ->
            JooqRateLimiter(
                dsl = OtpPostgresContainer.dsl,
                properties = properties,
                clock = clock,
            )
        },
    )
