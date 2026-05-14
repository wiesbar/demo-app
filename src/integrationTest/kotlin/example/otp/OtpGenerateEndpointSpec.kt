package example.otp

import example.web.IntegrationSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldNotBe
import org.springframework.test.web.servlet.client.RestTestClient

internal abstract class OtpGenerateEndpointSpec(
    restClient: RestTestClient,
    sms: InMemorySmsService,
) : IntegrationSpec({
        extension(SpringExtension)

        beforeEach { sms.reset() }

        test("should return 204 No Content when generate succeeds for a non-blank userId") {
            restClient.generateOtp("user-1")

            sms.lastOtpFor("user-1").shouldNotBeNull()
        }

        test("should return 204 No Content when generate is called twice for the same user") {
            restClient.generateOtp("user-2")
            val firstOtp = sms.lastOtpFor("user-2").shouldNotBeNull()

            restClient.generateOtp("user-2")
            val secondOtp = sms.lastOtpFor("user-2").shouldNotBeNull()

            firstOtp shouldNotBe ""
            secondOtp shouldNotBe ""
        }
    })
