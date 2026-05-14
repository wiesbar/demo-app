package example.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration
import kotlin.time.Duration as KotlinDuration

@ConfigurationProperties(prefix = "one-time-password")
internal data class OtpProperties(
    val length: Int,
    val maxAttempts: Int,
    val expireTime: Duration,
    val rateLimit: RateLimitProperties,
    val hashPepper: String = "",
) {
    init {
        require(length in 1..MAX_LENGTH) {
            "one-time-password.length must be in 1..$MAX_LENGTH but was $length"
        }
        require(maxAttempts > 0) {
            "one-time-password.max-attempts must be > 0 but was $maxAttempts"
        }
        require(!expireTime.isZero && !expireTime.isNegative) {
            "one-time-password.expire-time must be > 0 but was $expireTime"
        }
    }
}

data class RateLimitProperties(
    val generate: OperationWindows,
    val verify: OperationWindows,
)

data class OperationWindows(
    val short: WindowSpec? = null,
    val long: WindowSpec,
)

data class WindowSpec(
    val limit: Int,
    private val duration: Duration,
) {
    init {
        require(limit > 0) {
            "rate-limit window limit must be > 0 but was $limit"
        }
        require(!duration.isZero && !duration.isNegative) {
            "rate-limit window duration must be > 0 but was $duration"
        }
    }

    val window: KotlinDuration get() = duration.toKotlinDuration()

    internal companion object {
        internal fun of(
            limit: Int,
            window: KotlinDuration,
        ): WindowSpec = WindowSpec(limit, window.toJavaDuration())
    }
}

internal const val MAX_LENGTH: Int = 64
