package example.otp

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = [
        "one-time-password.length=4",
        "one-time-password.max-attempts=2",
        "one-time-password.expire-time=3s",
        "one-time-password.rate-limit.generate.short.limit=1000",
        "one-time-password.rate-limit.generate.long.limit=1000",
        "one-time-password.rate-limit.verify.long.limit=1000",
    ],
)
@AutoConfigureRestTestClient
@ActiveProfiles("one-time-password", "persistent-otp")
@Import(InMemorySmsService.TestConfig::class, MutableClockTestConfig::class)
internal class OtpExpiryEndpointPersistentTest(
    @Autowired restClient: RestTestClient,
    @Autowired sms: InMemorySmsService,
    @Autowired mutableClock: MutableClock,
) : OtpExpiryEndpointSpec(restClient, sms, mutableClock) {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            registerOtpDatasource(registry)
        }
    }
}
