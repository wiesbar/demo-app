package example.catalog

import example.web.IntegrationSpec
import io.kotest.assertions.assertSoftly
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.expectBody

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ProductIndexingTest(
    @Autowired private val restClient: RestTestClient,
    @Autowired private val template: ElasticsearchOperations,
) : IntegrationSpec({
        extension(SpringExtension)

        beforeSpec { resetFurnitureIndex(template) }

        beforeTest { template.indexOps(IndexCoordinates.of(FURNITURE_INDEX)).refresh() }

        test("should index a product and find it via search") {
            val id = "p-oak-table"
            putProduct(restClient, id, oakDiningTablePayload())
            template.indexOps(IndexCoordinates.of(FURNITURE_INDEX)).refresh()

            restClient
                .get()
                .uri("/products/search?q=oak")
                .exchange()
                .expectSearchHits(listOf(oakDiningTableDto(id)))
        }

        test("should remove a product on DELETE") {
            val id = "p-delete-me"
            putProduct(restClient, id, oakDiningTablePayload(name = "Pine bench", description = "soft pine bench"))
            template.indexOps(IndexCoordinates.of(FURNITURE_INDEX)).refresh()

            restClient
                .delete()
                .uri("/products/$id")
                .exchange()
                .expectStatus()
                .isNoContent
            template.indexOps(IndexCoordinates.of(FURNITURE_INDEX)).refresh()

            restClient
                .get()
                .uri("/products/search?q=pine")
                .exchange()
                .expectSearchHits(emptyList())
        }

        test("should return 404 when deleting twice") {
            val id = "p-twice-deleted"
            putProduct(restClient, id, oakDiningTablePayload(name = "Twice deleted", description = "delete me twice"))
            template.indexOps(IndexCoordinates.of(FURNITURE_INDEX)).refresh()

            restClient
                .delete()
                .uri("/products/$id")
                .exchange()
                .expectStatus()
                .isNoContent
            template.indexOps(IndexCoordinates.of(FURNITURE_INDEX)).refresh()

            restClient
                .delete()
                .uri("/products/$id")
                .exchange()
                .expectStatus()
                .isNotFound
                .expectBody<Map<String, String>>()
                .value { body ->
                    assertSoftly {
                        with(checkNotNull(body)) {
                            this["status"] shouldBe "404"
                            this["error"] shouldBe "Not Found"
                            this["message"] shouldBe "product with id '$id' not found"
                        }
                    }
                }
        }
    })
