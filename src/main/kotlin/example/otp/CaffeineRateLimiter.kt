package example.otp

import com.github.benmanes.caffeine.cache.Cache
import example.config.OperationWindows
import example.config.RateLimitProperties
import example.config.WindowSpec
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal class CaffeineRateLimiter(
    properties: RateLimitProperties,
    private val clock: Clock,
) : RateLimiter {
    private val windows: Map<RateLimitedOperation, List<WindowState>> =
        mapOf(
            RateLimitedOperation.GENERATE to buildWindowStates(properties.generate),
            RateLimitedOperation.VERIFY to buildWindowStates(properties.verify),
        )

    override fun tryAcquire(
        userId: String,
        operation: RateLimitedOperation,
    ): TryAcquireResult {
        val states = windows.getValue(operation)
        val acquired = mutableListOf<WindowState>()
        for (state in states) {
            when (val outcome = state.tryAcquireOne(userId)) {
                is WindowOutcome.Acquired -> acquired += state
                is WindowOutcome.Denied -> {
                    acquired.forEach { it.releaseOne(userId) }
                    return TryAcquireResult.Denied(outcome.retryAfter)
                }
            }
        }
        return TryAcquireResult.Acquired
    }

    private fun buildWindowStates(windows: OperationWindows): List<WindowState> =
        listOfNotNull(windows.short, windows.long).map { WindowState(it) }

    private sealed interface WindowOutcome {
        object Acquired : WindowOutcome

        data class Denied(
            val retryAfter: Duration,
        ) : WindowOutcome
    }

    private inner class WindowState(
        private val spec: WindowSpec,
    ) {
        private val cache: Cache<String, AtomicInteger> = cacheWith(clock, WindowExpiry(spec.window))

        // todo refactoring
        fun tryAcquireOne(userId: String): WindowOutcome {
            var denied = false
            cache.asMap().compute(userId) { _, existing ->
                if (existing == null) {
                    AtomicInteger(1)
                } else if (existing.get() >= spec.limit) {
                    denied = true
                    existing
                } else {
                    existing.also { it.incrementAndGet() }
                }
            }
            return if (denied) WindowOutcome.Denied(remainingWindow(userId)) else WindowOutcome.Acquired
        }

        fun releaseOne(userId: String) {
            cache.asMap().computeIfPresent(userId) { _, counter ->
                if (counter.get() > 1) counter.also { it.decrementAndGet() } else null
            }
        }

        private fun remainingWindow(userId: String): Duration =
            cache
                .policy()
                .expireVariably()
                .map { policy ->
                    policy.getExpiresAfter(userId, TimeUnit.NANOSECONDS).orElse(0L)
                }.map { expiresAfter ->
                    if (expiresAfter > 0L) expiresAfter.nanoseconds else null
                }.orElse(null) ?: spec.window
    }
}
