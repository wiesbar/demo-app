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
                                width = Dimension(180, UnitOfMeasure.CENTIMETER),
                                height = Dimension(75, UnitOfMeasure.CENTIMETER),
                                depth = Dimension(90, UnitOfMeasure.CENTIMETER),
                            ),
                    ),
                    Product(
                        id = "p-chair-mm",
                        category = Category.CHAIR,
                        name = "Walnut chair",
                        description = "Elegant walnut dining chair.",
                        dimensions =
                            Dimensions(
                                width = Dimension(450, UnitOfMeasure.MILLIMETER),
                                height = Dimension(900, UnitOfMeasure.MILLIMETER),
                                depth = Dimension(500, UnitOfMeasure.MILLIMETER),
                            ),
                    ),
                ),
            ) { product ->
                val json = ProductSerializer(mapper).serialize(product)
                val document = mapper.readValue<ProductDocument>(json)
                document.toDomain(product.id) shouldBe product
            }
        }
    })
