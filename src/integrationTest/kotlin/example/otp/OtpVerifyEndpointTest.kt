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
    ],
)
@AutoConfigureRestTestClient
@ActiveProfiles("one-time-password")
@Import(InMemorySmsService.TestConfig::class)
class OtpVerifyEndpointTest internal constructor(
    @Autowired private val restClient: RestTestClient,
    @Autowired private val sms: InMemorySmsService,
) : IntegrationSpec({
        extension(SpringExtension)

        beforeEach { sms.reset() }

        test("should return 204 No Content when verifying with the correct OTP") {
            restClient.generateOtp("user-v1")
            val otp = sms.lastOtpFor("user-v1").shouldNotBeNull()

            restClient.verifyOtp("user-v1", otp).expectStatus().isNoContent
        }

        test("should return 401 Unauthorized when verifying with the wrong OTP") {
            restClient.generateOtp("user-v2")
            sms.lastOtpFor("user-v2").shouldNotBeNull()

            restClient.verifyOtp("user-v2", "WRONG1").expectStatus().isUnauthorized
        }

        test("should return 401 Unauthorized when no OTP was generated for the user") {
            restClient.verifyOtp("user-v3", "ANYOTP").expectStatus().isUnauthorized
        }

        test("should return 401 Unauthorized after attempts are exhausted") {
            restClient.generateOtp("user-v4")
            val otp = sms.lastOtpFor("user-v4").shouldNotBeNull()
            repeat(MAX_ATTEMPTS) { restClient.verifyOtp("user-v4", "WRONG1").expectStatus().isUnauthorized }

            restClient.verifyOtp("user-v4", otp).expectStatus().isUnauthorized
        }
    })

private const val MAX_ATTEMPTS = 2
