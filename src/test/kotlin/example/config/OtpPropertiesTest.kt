package example.config

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import java.time.Duration

class OtpPropertiesTest :
    FunSpec({
        test("happy path: a fully-valid OtpProperties does not throw") {
            shouldNotThrow<IllegalArgumentException> { createProps() }
        }

        test("verify.short = null is allowed") {
            shouldNotThrow<IllegalArgumentException> { createProps(verifyShort = null) }
        }

        context("rejects invalid fields") {
            withData(
                nameFn = { it.label },
                InvalidCase("length = 0", "length") { createProps(length = 0) },
                InvalidCase("length = -1", "length") { createProps(length = -1) },
                InvalidCase("length above upper bound (65)", "length") {
                    createProps(length = MAX_LENGTH + 1)
                },
                InvalidCase("maxAttempts = 0", "max-attempts") { createProps(maxAttempts = 0) },
                InvalidCase("maxAttempts = -3", "max-attempts") { createProps(maxAttempts = -3) },
                InvalidCase("expireTime = 0", "expire-time") {
                    createProps(expireTime = Duration.ZERO)
                },
                InvalidCase("expireTime negative", "expire-time") {
                    createProps(expireTime = Duration.ofSeconds(-1))
                },
                InvalidCase("rateLimit.generate.short.limit = 0", "limit") {
                    createProps(generateShort = WindowSpec(limit = 0, duration = Duration.ofSeconds(30)))
                },
                InvalidCase("rateLimit.generate.short.window = 0", "duration") {
                    createProps(generateShort = WindowSpec(limit = 1, duration = Duration.ZERO))
                },
                InvalidCase("rateLimit.generate.short.window negative", "duration") {
                    createProps(generateShort = WindowSpec(limit = 1, duration = Duration.ofSeconds(-1)))
                },
                InvalidCase("rateLimit.generate.long.limit = 0", "limit") {
                    createProps(generateLong = WindowSpec(limit = 0, duration = Duration.ofHours(1)))
                },
                InvalidCase("rateLimit.generate.long.window = 0", "duration") {
                    createProps(generateLong = WindowSpec(limit = 5, duration = Duration.ZERO))
                },
                InvalidCase("rateLimit.verify.long.limit = 0", "limit") {
                    createProps(verifyLong = WindowSpec(limit = 0, duration = Duration.ofMinutes(5)))
                },
                InvalidCase("rateLimit.verify.long.window = 0", "duration") {
                    createProps(verifyLong = WindowSpec(limit = 10, duration = Duration.ZERO))
                },
                InvalidCase("rateLimit.verify.long.window negative", "duration") {
                    createProps(verifyLong = WindowSpec(limit = 10, duration = Duration.ofSeconds(-1)))
                },
            ) { case ->
                val ex =
                    shouldThrow<IllegalArgumentException> {
                        case.test()
                    }
                withClue("message=${ex.message}") {
                    ex.message.shouldNotBeNull() shouldContain case.expectedFragment
                }
            }
        }

        test("WindowSpec.of routes through init and rejects non-positive limit") {
            val ex =
                shouldThrow<IllegalArgumentException> {
                    WindowSpec.of(limit = 0, window = kotlin.time.Duration.parse("30s"))
                }
            ex.message.shouldNotBeNull() shouldBeEqual "rate-limit window limit must be > 0 but was 0"
        }

        test("WindowSpec.of routes through init and rejects zero window") {
            val ex =
                shouldThrow<IllegalArgumentException> {
                    WindowSpec.of(limit = 1, window = kotlin.time.Duration.ZERO)
                }
            ex.message.shouldNotBeNull() shouldBeEqual "rate-limit window duration must be > 0 but was PT0S"
        }
    })

@Suppress("LongParameterList")
private fun createProps(
    length: Int = 6,
    maxAttempts: Int = 3,
    expireTime: Duration = Duration.ofMinutes(5),
    generateShort: WindowSpec? = WindowSpec(limit = 1, duration = Duration.ofSeconds(30)),
    generateLong: WindowSpec = WindowSpec(limit = 5, duration = Duration.ofHours(1)),
    verifyShort: WindowSpec? = null,
    verifyLong: WindowSpec = WindowSpec(limit = 10, duration = Duration.ofMinutes(5)),
): OtpProperties =
    OtpProperties(
        length = length,
        maxAttempts = maxAttempts,
        expireTime = expireTime,
        rateLimit =
            RateLimitProperties(
                generate = OperationWindows(short = generateShort, long = generateLong),
                verify = OperationWindows(short = verifyShort, long = verifyLong),
            ),
    )

private data class InvalidCase(
    val label: String,
    val expectedFragment: String,
    val test: () -> OtpProperties,
)
