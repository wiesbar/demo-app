package example.otp

import example.web.IntegrationSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldNotBe
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
        "one-time-password.rate-limit.generate.short.limit=1000",
        "one-time-password.rate-limit.generate.long.limit=1000",
        "one-time-password.rate-limit.verify.long.limit=1000",
    ],
)
@AutoConfigureRestTestClient
@ActiveProfiles("one-time-password")
@Import(InMemorySmsService.TestConfig::class)
class OtpGenerateEndpointTest internal constructor(
    @Autowired private val restClient: RestTestClient,
    @Autowired private val sms: InMemorySmsService,
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
