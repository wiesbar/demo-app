package example.catalog

import example.web.IntegrationSpec
import example.web.ProductWithIdDto
import io.kotest.extensions.spring.SpringExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ProductSearchTest(
    @Autowired private val restClient: RestTestClient,
    @Autowired private val template: ElasticsearchOperations,
) : IntegrationSpec({
        extension(SpringExtension)

        beforeSpec {
            resetFurnitureIndex(template)
            seedProducts(restClient)
            template.indexOps(IndexCoordinates.of(FURNITURE_INDEX)).refresh()
        }

        beforeTest { template.indexOps(IndexCoordinates.of(FURNITURE_INDEX)).refresh() }

        test("should filter results by category") {
            restClient
                .get()
                .uri("/products/search?q=oak&category=CHAIR")
                .exchange()
                .expectSearchHits(listOf(oakArmchair))
        }

        test("should boost name matches over description matches") {
            restClient
                .get()
                .uri("/products/search?q=oak%20dining")
                .exchange()
                .expectFirstSearchHit(oakDiningTable)
        }
    })

private val oakDiningTable =
    catalogProductDto("tbl-oak", Category.TABLE, "Oak dining table", "Solid oak table, seats six.")
private val pineWorkbench =
    catalogProductDto("tbl-pine", Category.TABLE, "Pine workbench", "Sturdy pine surface for crafts.")
private val oakArmchair =
    catalogProductDto("chr-oak", Category.CHAIR, "Oak armchair", "Comfortable seating in oak.")
private val walnutChair =
    catalogProductDto("chr-walnut", Category.CHAIR, "Walnut chair", "Elegant walnut dining chair.")

private fun seedProducts(client: RestTestClient) {
    listOf(oakDiningTable, pineWorkbench, oakArmchair, walnutChair).forEach {
        putProduct(client, it.id, buildPayload(it))
    }
}

private fun buildPayload(p: ProductWithIdDto): String =
    """
    {
      "category": "${p.product.category.name}",
      "name": "${p.product.name}",
      "description": "${p.product.description}",
      "dimensions": {
        "width":  { "value": ${p.product.dimensions.width.value}, "unit": "Centimeter" },
        "height": { "value": ${p.product.dimensions.height.value}, "unit": "Centimeter" },
        "depth":  { "value": ${p.product.dimensions.depth.value}, "unit": "Centimeter" }
      }
    }
    """.trimIndent()
