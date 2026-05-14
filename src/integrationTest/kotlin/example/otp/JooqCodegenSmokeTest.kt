package example.otp

import example.otp.jooq.Tables.OTP_ENTRIES
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class JooqCodegenSmokeTest :
    FunSpec({
        test("Flyway-migrated schema is queryable through the generated jOOQ tables") {
            val dsl = OtpPostgresContainer.dsl

            val rowCount = dsl.fetchCount(OTP_ENTRIES)

            rowCount shouldBe 0
        }
    })
