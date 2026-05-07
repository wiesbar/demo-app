package example.catalog

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

@Suppress("MagicNumber")
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
    })
