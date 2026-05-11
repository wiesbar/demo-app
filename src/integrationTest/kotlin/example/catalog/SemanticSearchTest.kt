package example.catalog

import example.web.IntegrationSpec
import example.web.ProductWithIdDto
import example.web.SearchResponseDto
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.datatest.withData
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.get
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.expectBody

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("catalog")
class SemanticSearchTest(
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

        context("should rank the most semantically similar product first") {
            withData(
                nameFn = { it.label },
                listOf(
                    RankCase("matching 'comfortable armchair' to Oak armchair", "comfortable armchair", oakArmchair),
                    RankCase("treating 'couch' as a synonym for 'sofa'", "couch", velvetSofa),
                ),
            ) { (_, query, expected) ->
                postSemantic(restClient, """{"query": "$query"}""")
                    .expectStatus()
                    .isOk
                    .expectBody<SearchResponseDto>()
                    .value { body ->
                        body
                            .shouldNotBeNull()
                            .hits
                            .firstOrNull()
                            .shouldNotBeNull()
                            .product shouldBe expected
                    }
            }
        }

        test("should return no hits when the query has only out-of-vocab tokens") {
            postSemantic(restClient, """{"query": "xyzzy plover qux"}""")
                .expectStatus()
                .isOk
                .expectBody<SearchResponseDto>()
                .value { body ->
                    body.shouldNotBeNull().hits.shouldBeEmpty()
                }
        }

        test("should cap results by limit") {
            postSemantic(restClient, """{"query": "oak chair table sofa", "limit": 2}""")
                .expectStatus()
                .isOk
                .expectBody<SearchResponseDto>()
                .value { body ->
                    body.shouldNotBeNull().hits shouldHaveSize 2
                }
        }

        test("should drop hits below minScore") {
            withClue(
                """
                Partial matches cannot reach cosine 1.0 because document embeddings carry NAME_BOOST=3
                absent in queries. ES "similarity": "cosine" returns (1 + cos)/2 in [0, 1]. The empirical
                best score for 'oak chair' against the seeded corpus is ~0.97, so 0.99 drops everything.
                """.trimIndent(),
            ) {
                postSemantic(restClient, """{"query": "oak chair", "minScore": 0.99}""")
                    .expectStatus()
                    .isOk
                    .expectBody<SearchResponseDto>()
                    .value { body ->
                        body.shouldNotBeNull().hits shouldHaveSize 0
                    }
            }
        }

        test("should store an embedding of scorer dimension with at least one non-zero entry") {
            withClue("missing indexed document for ${oakArmchair.id}") {
                template
                    .get<ProductDocument>(oakArmchair.id, IndexCoordinates.of(FURNITURE_INDEX))
                    .shouldNotBeNull()
            }.run {
                assertSoftly {
                    embedding shouldHaveSize EmbeddingVocabulary.size
                    embedding.count { it != 0.0f } shouldBeGreaterThan 0
                }
            }
        }
    })

private data class RankCase(
    val label: String,
    val query: String,
    val expectedProduct: ProductWithIdDto,
)

private val oakDiningTable =
    catalogProductDto("tbl-oak", Category.TABLE, "Oak dining table", "Solid oak table, seats six.")
private val pineWorkbench =
    catalogProductDto("tbl-pine", Category.TABLE, "Pine workbench", "Sturdy pine surface for crafts.")
private val oakArmchair =
    catalogProductDto("chr-oak", Category.CHAIR, "Oak armchair", "Comfortable seating in oak.")
private val walnutChair =
    catalogProductDto("chr-walnut", Category.CHAIR, "Walnut chair", "Elegant walnut dining chair.")
private val velvetSofa =
    catalogProductDto("sofa-velvet", Category.CHAIR, "Velvet sofa", "Plush three-seater sofa.")

private fun seedProducts(client: RestTestClient) {
    listOf(oakDiningTable, pineWorkbench, oakArmchair, walnutChair, velvetSofa).forEach {
        putProduct(client, it.id, buildPayload(it))
    }
}

private fun postSemantic(
    client: RestTestClient,
    body: String,
): RestTestClient.ResponseSpec =
    client
        .post()
        .uri("/products/semantic-search")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()

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
