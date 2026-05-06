package example.catalog

import example.web.IntegrationSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.data.elasticsearch.core.query.Query
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class IndexInitializerTest internal constructor(
    @Autowired private val restClient: RestTestClient,
    @Autowired private val template: ElasticsearchOperations,
    @Autowired private val initializer: IndexInitializer,
) : IntegrationSpec({
        extension(SpringExtension)

        beforeSpec {
            resetFurnitureIndex(template)
            putProduct(restClient, "p-marker", oakDiningTablePayload(name = "Marker", description = "marker product"))
            template.indexOps(IndexCoordinates.of(FURNITURE_INDEX)).refresh()
        }

        test("should not recreate an existing index") {
            val coordinates = IndexCoordinates.of(FURNITURE_INDEX)
            val ops = template.indexOps(coordinates)
            ops.exists() shouldBe true
            val countBefore = template.count(Query.findAll(), coordinates)

            initializer.run(DefaultApplicationArguments())
            template.indexOps(coordinates).refresh()

            ops.exists() shouldBe true
            template.count(Query.findAll(), coordinates) shouldBe countBefore
        }
    })
