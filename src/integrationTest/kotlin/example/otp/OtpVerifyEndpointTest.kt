package example.otp

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
internal class OtpVerifyEndpointTest(
    @Autowired restClient: RestTestClient,
    @Autowired sms: InMemorySmsService,
) : OtpVerifyEndpointSpec(restClient, sms)
