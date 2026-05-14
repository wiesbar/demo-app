package example.otp

import example.web.IntegrationSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import org.springframework.test.web.servlet.client.RestTestClient
import kotlin.time.Duration.Companion.milliseconds

internal abstract class OtpExpiryEndpointSpec(
    restClient: RestTestClient,
    sms: InMemorySmsService,
    mutableClock: MutableClock,
) : IntegrationSpec({
        extension(SpringExtension)

        beforeEach { sms.reset() }

        test("second attempt with correct OTP fails because the OTP has expired") {
            restClient.generateOtp("user-exp")
            val otp = sms.lastOtpFor("user-exp").shouldNotBeNull()

            restClient.verifyOtp("user-exp", "WRNG").expectStatus().isUnauthorized
            mutableClock.advance(PAST_EXPIRY)

            restClient.verifyOtp("user-exp", otp).expectStatus().isUnauthorized
        }
    })

private val PAST_EXPIRY = 3_500.milliseconds
