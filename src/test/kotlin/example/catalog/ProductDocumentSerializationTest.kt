package example.catalog

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

class ProductDocumentSerializationTest :
    FunSpec({
        val mapper = jacksonObjectMapper()
        val serializer = ProductSerializer(mapper, FrozenTfIdfSemanticScorer())

        context("should round-trip product through ProductDocument") {
            withData(
                nameFn = { "${it.category}-${it.dimensions.width.unit}" },
                listOf(
                    Product(
                        id = "p-table-cm",
                        category = Category.TABLE,
                        name = "Oak dining table",
                        description = "Solid oak, seats six.",
                        dimensions =
                            Dimensions(
                                width = 180.centimeters,
                                height = 75.centimeters,
                                depth = 90.centimeters,
                            ),
                    ),
                    Product(
                        id = "p-chair-mm",
                        category = Category.CHAIR,
                        name = "Walnut chair",
                        description = "Elegant walnut dining chair.",
                        dimensions =
                            Dimensions(
                                width = 450.millimeters,
                                height = 900.millimeters,
                                depth = 500.millimeters,
                            ),
                    ),
                ),
            ) { product ->
                val json = serializer.serialize(product)
                val document = mapper.readValue<ProductDocument>(json)
                document.toDomain(product.id) shouldBe product
            }
        }

        test("should omit empty embedding via JsonInclude(NON_EMPTY)") {
            val doc = ProductDocument(category = "TABLE", name = "n", description = "d")
            val tree = mapper.readTree(mapper.writeValueAsString(doc))
            tree.has("embedding") shouldBe false
        }

        test("should keep non-empty embedding in JSON") {
            val doc = ProductDocument(category = "TABLE", name = "n", description = "d", embedding = listOf(0.1f, 0.2f))
            val tree = mapper.readTree(mapper.writeValueAsString(doc))
            tree.has("embedding") shouldBe true
        }
    })
