package example.otp

import kotlin.time.Duration

enum class RateLimitedOperation { GENERATE, VERIFY }

interface RateLimiter {
    fun tryAcquire(
        userId: String,
        operation: RateLimitedOperation,
    ): TryAcquireResult
}

sealed interface TryAcquireResult {
    object Acquired : TryAcquireResult

    data class Denied(
        val retryAfter: Duration,
    ) : TryAcquireResult
}

internal class RateLimitExceededException(
    val retryAfter: Duration,
) : RuntimeException()

internal fun RateLimiter.acquireOrThrow(
    userId: String,
    operation: RateLimitedOperation,
) {
    val result = tryAcquire(userId, operation)
    if (result is TryAcquireResult.Denied) throw RateLimitExceededException(result.retryAfter)
}
