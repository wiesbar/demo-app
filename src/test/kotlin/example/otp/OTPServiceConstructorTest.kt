package example.otp

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.reflect.full.primaryConstructor

class OTPServiceConstructorTest :
    FunSpec({
        test("OTPService constructor parameters are all required (no defaults)") {
            val params = OTPService::class.primaryConstructor!!.parameters

            params
                .shouldHaveSize(3)
                .shouldForAll { param -> param.isOptional shouldBe false }
        }

        test("DefaultPasswordRepository requires maxAttempts and otpExpireTime, clock stays optional") {
            val params = DefaultPasswordRepository::class.primaryConstructor!!.parameters
            val byName = params.associateBy { it.name }

            mapOf(
                "maxAttempts" to false,
                "otpExpireTime" to false,
                "clock" to true,
            ).forEach { (paramName, expected) ->
                withClue("Parameter $paramName should be ${if (expected) "optional" else "required"}") {
                    byName[paramName]!!.isOptional shouldBe expected
                }
            }
        }
    })
