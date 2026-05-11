package example.otp

import example.web.IntegrationSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = [
        "one-time-password.length=4",
        "one-time-password.max-attempts=2",
        "one-time-password.expire-time=3s",
    ],
)
@AutoConfigureRestTestClient
@ActiveProfiles("one-time-password")
@Import(InMemorySmsService.TestConfig::class)
class OtpExpiryEndpointTest internal constructor(
    @Autowired private val restClient: RestTestClient,
    @Autowired private val sms: InMemorySmsService,
) : IntegrationSpec({
        extension(SpringExtension)

        test("second attempt with correct OTP fails because the OTP has expired") {
            restClient.generateOtp("user-exp")
            val otp = sms.lastOtpFor("user-exp").shouldNotBeNull()

            restClient.verifyOtp("user-exp", "WRNG").expectStatus().isUnauthorized
            Thread.sleep(WAIT_PAST_EXPIRY_MILLIS)

            restClient.verifyOtp("user-exp", otp).expectStatus().isUnauthorized
        }
    })

private const val WAIT_PAST_EXPIRY_MILLIS = 3_500L
