package example.otp

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
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
internal class OtpRequestValidationTest(
    @Autowired restClient: RestTestClient,
) : OtpRequestValidationEndpointSpec(restClient)
