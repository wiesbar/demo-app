package example.catalog

import example.web.IntegrationSpec
import io.kotest.assertions.assertSoftly
import io.kotest.datatest.withData
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.expectBody

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("catalog")
class SemanticSearchValidationTest(
    @Autowired private val restClient: RestTestClient,
) : IntegrationSpec({
        extension(SpringExtension)

        context("should reject invalid semantic-search bodies") {
            withData(
                nameFn = { (label, _, _) -> label },
                listOf(
                    Case("blank query", """{"query": ""}""", "query 'query' must not be blank"),
                    Case(
                        "limit too small",
                        """{"query": "oak", "limit": 0}""",
                        "query 'limit' must be in 1..100, got 0",
                    ),
                    Case(
                        "limit too large",
                        """{"query": "oak", "limit": 101}""",
                        "query 'limit' must be in 1..100, got 101",
                    ),
                    Case(
                        "minScore negative",
                        """{"query": "oak", "minScore": -0.1}""",
                        "query 'minScore' must be in 0.0..1.0, got -0.1",
                    ),
                    Case(
                        "minScore above one",
                        """{"query": "oak", "minScore": 1.5}""",
                        "query 'minScore' must be in 0.0..1.0, got 1.5",
                    ),
                ),
            ) { (_, body, expectedMessage) ->
                restClient
                    .post()
                    .uri("/products/semantic-search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange()
                    .expectStatus()
                    .isBadRequest
                    .expectBody<Map<String, String>>()
                    .value { responseBody ->
                        assertSoftly {
                            with(responseBody.shouldNotBeNull()) {
                                this["status"] shouldBe "400"
                                this["message"] shouldBe expectedMessage
                            }
                        }
                    }
            }
        }
    })

private data class Case(
    val label: String,
    val body: String,
    val expectedMessage: String,
)
