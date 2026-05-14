package example.otp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal class PostgresClockTest :
    FunSpec({
        val clock = PostgresClock(OtpPostgresContainer.dsl)

        test("now is monotonic across successive reads") {
            val first = clock.now()
            val second = clock.now()

            second shouldBeGreaterThanOrEqualTo first
        }

        test("now tracks the host wall clock within a minute") {
            val hostNow = Clock.System.now()
            val dbNow = clock.now()

            val skew = (dbNow - hostNow).absoluteValue
            skew shouldBeLessThan 1.minutes
        }
    })
