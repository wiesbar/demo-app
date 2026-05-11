package example.catalog

import example.web.IntegrationSpec
import io.kotest.assertions.assertSoftly
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
class ProductPayloadValidationTest(
    @Autowired private val restClient: RestTestClient,
) : IntegrationSpec({
        extension(SpringExtension)

        test("should return 400 when dimension value is not positive") {
            val body =
                """
                {
                  "category": "TABLE",
                  "name": "Bad table",
                  "description": "negative width",
                  "dimensions": {
                    "width":  { "value": 0, "unit": "Centimeter" },
                    "height": { "value": 75, "unit": "Centimeter" },
                    "depth":  { "value": 90, "unit": "Centimeter" }
                  }
                }
                """.trimIndent()
            restClient
                .put()
                .uri("/products/p-bad")
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
                            this["message"] shouldBe "dimension value must be positive, got 0"
                        }
                    }
                }
        }

        test("should return 400 when unit is unknown") {
            val body =
                """
                {
                  "category": "TABLE",
                  "name": "Bad units",
                  "description": "unsupported unit",
                  "dimensions": {
                    "width":  { "value": 10, "unit": "Inches" },
                    "height": { "value": 10, "unit": "Centimeter" },
                    "depth":  { "value": 10, "unit": "Centimeter" }
                  }
                }
                """.trimIndent()
            restClient
                .put()
                .uri("/products/p-units")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isBadRequest
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("400")
        }

        test("should return 400 when name is blank") {
            val body = oakDiningTablePayload(name = " ")
            restClient
                .put()
                .uri("/products/p-blank-name")
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
                            this["message"] shouldBe "product name must not be blank"
                        }
                    }
                }
        }

        test("should return 400 when description is blank") {
            val body = oakDiningTablePayload(description = " ")
            restClient
                .put()
                .uri("/products/p-blank-desc")
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
                            this["message"] shouldBe "product description must not be blank"
                        }
                    }
                }
        }
    })
