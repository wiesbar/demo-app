package example.otp

import example.web.IntegrationSpec
import io.kotest.assertions.assertSoftly
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.expectBody
import kotlin.time.Duration.Companion.seconds

internal abstract class OtpRateLimitEndpointSpec(
    restClient: RestTestClient,
    mutableClock: MutableClock,
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
                            this["error"] shouldBe "Too Many Requests"
                            this["message"] shouldBe "Rate limit exceeded"
                        }
                    }
                }
        }
    })

private const val RATE_LIMITED_STATUS = 429
private const val RETRY_AFTER_PATTERN = "^\\d+$"
