package example.otp

import example.web.IntegrationSpec
import io.kotest.assertions.assertSoftly
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.expectBody
import kotlin.time.Duration.Companion.seconds

@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = [
        "one-time-password.length=4",
        "one-time-password.max-attempts=3",
        "one-time-password.expire-time=10m",
        "one-time-password.rate-limit.generate.short.limit=1",
        "one-time-password.rate-limit.generate.short.window=30s",
        "one-time-password.rate-limit.generate.long.limit=5",
        "one-time-password.rate-limit.generate.long.window=1h",
        "one-time-password.rate-limit.verify.long.limit=2",
        "one-time-password.rate-limit.verify.long.window=5m",
    ],
)
@AutoConfigureRestTestClient
@ActiveProfiles("one-time-password")
@Import(InMemorySmsService.TestConfig::class, MutableClockTestConfig::class, NoRetryRestTestClientConfig::class)
class OtpRateLimitTest internal constructor(
    @Autowired private val restClient: RestTestClient,
    @Autowired private val mutableClock: MutableClock,
) : IntegrationSpec({
        extension(SpringExtension)

        test("second generate within the short window returns 429 with Retry-After header") {
            val userId = "user-1"
            restClient.generateOtp(userId)

            restClient
                .generateOtpExpecting(userId, RATE_LIMITED_STATUS)
                .expectHeader()
                .value(HttpHeaders.RETRY_AFTER) { value -> value shouldMatch RETRY_AFTER_PATTERN }
                .expectBody<Map<String, String>>()
                .value { responseBody ->
                    assertSoftly {
                        with(responseBody.shouldNotBeNull()) {
                            this["status"] shouldBe "429"
                            this["error"] shouldBe "Too Many Requests"
                            this["message"] shouldBe "Rate limit exceeded"
                        }
                    }
                }

            mutableClock.advance(31.seconds)
            restClient.generateOtp(userId)
        }

        test("third verify within the long window returns 429") {
            val userId = "user-2"
            restClient.generateOtp(userId)
            repeat(2) { restClient.verifyOtp(userId, "WRNG").expectStatus().isUnauthorized }

            restClient
                .verifyOtp(userId, "WRNG")
                .expectStatus()
                .isEqualTo(RATE_LIMITED_STATUS)
                .expectHeader()
                .value(HttpHeaders.RETRY_AFTER) { value -> value shouldMatch RETRY_AFTER_PATTERN }
                .expectBody<Map<String, String>>()
                .value { responseBody ->
                    assertSoftly {
                        with(responseBody.shouldNotBeNull()) {
                            this["status"] shouldBe "429"
                        }
                    }
                }
        }
    })

private const val RATE_LIMITED_STATUS = 429
private const val RETRY_AFTER_PATTERN = "^\\d+$"
