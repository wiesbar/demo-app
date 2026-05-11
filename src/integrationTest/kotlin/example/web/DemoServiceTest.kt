package example.web

import io.kotest.extensions.spring.SpringExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.expectBody

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("calculator")
class DemoServiceTest(
    @Autowired private val restClient: RestTestClient,
) : IntegrationSpec({
        extension(SpringExtension)

        test("should return expected text for GET method for root path") {
            restClient.get().uri("/").exchange().expectAll(
                { response -> response.expectStatus().isOk },
                { response -> response.expectBody<String>().isEqualTo("The Demo Service is running!") },
            )
        }
    })
