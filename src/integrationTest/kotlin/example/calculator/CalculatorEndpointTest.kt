package example.calculator

import example.web.IntegrationSpec
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
class CalculatorEndpointTest(
    @Autowired private val restClient: RestTestClient,
) : IntegrationSpec({
        extension(SpringExtension)

        test("should return calculated result for valid expression") {
            restClient.post().uri("/calculate").body("1+2*3").exchange().expectAll(
                { response -> response.expectStatus().isOk },
                { response -> response.expectBody<String>().isEqualTo("7.0") },
            )
        }

        test("should return bad request for invalid expression") {
            restClient.post().uri("/calculate").body("abc").exchange().expectAll(
                { response -> response.expectStatus().isBadRequest },
                { response ->
                    response.expectBody<Map<String, String>>().isEqualTo(
                        mapOf(
                            "status" to "400",
                            "error" to "Bad Request",
                            "message" to "Invalid character 'a' at position '0' in expression.",
                        ),
                    )
                },
            )
        }
    })
