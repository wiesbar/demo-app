package example.web

import io.kotest.extensions.spring.SpringExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.expectBody

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class UnsupportedOperationsTest(
    @Autowired private val restClient: RestTestClient,
) : IntegrationSpec({
        extension(SpringExtension)

        test("should return expected error for unsupported request") {
            restClient.get().uri("/calculate").exchange().expectAll(
                { response -> response.expectStatus().is5xxServerError },
                { response ->
                    response.expectBody<Map<String, String>>().isEqualTo(
                        mapOf(
                            "status" to "500",
                            "error" to "Internal Server Error",
                            "message" to "Request method 'GET' is not supported",
                        ),
                    )
                },
            )
        }
    })
