package example.catalog

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class DimensionTest :
    FunSpec({
        context("should reject non-positive value") {
            withData(
                nameFn = { value -> "value=$value" },
                listOf(0, -1, -100),
            ) { value ->
                shouldThrow<InvalidProductException> {
                    value.millimeters
                }.run {
                    assertSoftly {
                        message shouldBe "dimension value must be positive, got $value"
                        cause shouldBe null
                    }
                }
            }
        }

        context("should accept positive value") {
            withData(
                nameFn = { value -> "value=$value" },
                listOf(1, 50, 1000),
            ) { value ->
                value.centimeters.value shouldBe value
            }
        }
    })
