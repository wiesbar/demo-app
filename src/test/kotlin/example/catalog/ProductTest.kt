package example.catalog

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class ProductTest :
    FunSpec({
        context("should reject blank fields") {
            withData(
                nameFn = { row -> "blank ${row.field}" },
                listOf(
                    BlankFieldCase(
                        field = "id",
                        expectedMessage = "product id must not be blank",
                        build = { productOf(id = " ") },
                    ),
                    BlankFieldCase(
                        field = "name",
                        expectedMessage = "product name must not be blank",
                        build = { productOf(name = " ") },
                    ),
                    BlankFieldCase(
                        field = "description",
                        expectedMessage = "product description must not be blank",
                        build = { productOf(description = " ") },
                    ),
                ),
            ) { case ->
                val thrown = shouldThrow<InvalidProductException> { case.build() }
                with(thrown) {
                    assertSoftly {
                        message shouldBe case.expectedMessage
                        cause shouldBe null
                    }
                }
            }
        }
    })

private data class BlankFieldCase(
    val field: String,
    val expectedMessage: String,
    val build: () -> Product,
)

private fun productOf(
    id: String = "p-1",
    name: String = "Oak table",
    description: String = "Solid oak.",
): Product =
    Product(
        id = id,
        category = Category.TABLE,
        name = name,
        description = description,
        dimensions =
            Dimensions(
                width = 180.centimeters,
                height = 75.centimeters,
                depth = 90.centimeters,
            ),
    )
