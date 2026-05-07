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
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.expectBody

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ProductSearchValidationTest(
    @Autowired private val restClient: RestTestClient,
) : IntegrationSpec({
        extension(SpringExtension)

        context("should reject invalid search parameters") {
            withData(
                nameFn = { (uri, _) -> uri },
                listOf(
                    "/products/search?q=" to "query 'q' must not be blank",
                    "/products/search?q=oak&size=0" to "query 'size' must be in 1..100, got 0",
                    "/products/search?q=oak&size=101" to "query 'size' must be in 1..100, got 101",
                ),
            ) { (uri, expectedMessage) ->
                restClient
                    .get()
                    .uri(uri)
                    .exchange()
                    .expectStatus()
                    .isBadRequest
                    .expectBody<Map<String, String>>()
                    .value { body ->
                        assertSoftly {
                            with(body.shouldNotBeNull()) {
                                this["status"] shouldBe "400"
                                this["message"] shouldBe expectedMessage
                            }
                        }
                    }
            }
        }

        test("should return 404 when deleting unknown product") {
            restClient
                .delete()
                .uri("/products/does-not-exist-xyz")
                .exchange()
                .expectStatus()
                .isNotFound
                .expectBody<Map<String, String>>()
                .value { body ->
                    assertSoftly {
                        with(body.shouldNotBeNull()) {
                            this["status"] shouldBe "404"
                            this["error"] shouldBe "Not Found"
                            this["message"] shouldBe "product with id 'does-not-exist-xyz' not found"
                        }
                    }
                }
        }
    })
