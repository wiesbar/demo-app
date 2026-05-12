package example.web

import example.catalog.Category
import example.catalog.centimeters
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import tools.jackson.module.kotlin.jacksonObjectMapper

class ProductDtoSerializationTest :
    FunSpec({
        val mapper = jacksonObjectMapper()

        test("should flatten ProductDto fields into ProductWithIdDto via JsonUnwrapped") {
            val dto =
                ProductWithIdDto(
                    id = "p-1",
                    product =
                        ProductDto(
                            category = Category.TABLE,
                            name = "Oak",
                            description = "Solid oak.",
                            dimensions =
                                DimensionsDto(
                                    width = 180.centimeters.toDto(),
                                    height = 75.centimeters.toDto(),
                                    depth = 90.centimeters.toDto(),
                                ),
                        ),
                )
            val tree = mapper.readTree(mapper.writeValueAsString(dto))

            with(tree) {
                has("product") shouldBe false
                get("id").asString() shouldBe "p-1"
                get("name").asString() shouldBe "Oak"
                get("category").asString() shouldBe "TABLE"
            }
        }
    })
