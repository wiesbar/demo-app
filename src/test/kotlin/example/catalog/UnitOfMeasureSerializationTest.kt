package example.catalog

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

class UnitOfMeasureSerializationTest :
    FunSpec({
        val mapper: ObjectMapper = jacksonObjectMapper()

        context("should serialize unit of measure") {
            withData(
                nameFn = { (unit, _) -> unit.name },
                listOf(
                    UnitOfMeasure.MILLIMETER to """"Millimeter"""",
                    UnitOfMeasure.CENTIMETER to """"Centimeter"""",
                ),
            ) { (unit, expectedJson) ->
                mapper.writeValueAsString(unit) shouldBe expectedJson
            }
        }

        context("should deserialize unit of measure") {
            withData(
                nameFn = { (json, _) -> json },
                listOf(
                    """"Millimeter"""" to UnitOfMeasure.MILLIMETER,
                    """"Centimeter"""" to UnitOfMeasure.CENTIMETER,
                ),
            ) { (json, expected) ->
                mapper.readValue<UnitOfMeasure>(json) shouldBe expected
            }
        }

        test("should reject unknown unit") {
            shouldThrow<Exception> {
                mapper.readValue<UnitOfMeasure>(""""Inches"""")
            }
        }
    })
