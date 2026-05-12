package example.web

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.maps.shouldContainAll
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.springframework.http.HttpStatus

class GlobalExceptionHandlerTest :
    FunSpec({
        val handler = GlobalExceptionHandler()

        test("handleGeneralError returns 500 with a static message that does not leak the cause message") {
            val expectedBody =
                mapOf(
                    "status" to "500",
                    "error" to "Internal Server Error",
                    "message" to "Internal Server Error",
                )

            val response = handler.handleGeneralError(RuntimeException("contains secret=abc"))

            assertSoftly(response) {
                statusCode shouldBe HttpStatus.INTERNAL_SERVER_ERROR
                body.shouldNotBeNull().also {
                    it shouldHaveSize (3)
                    it shouldContainAll expectedBody
                }
            }
        }

        test("handleGeneralError does not include the original exception message in any field") {
            val secret = "secret=super-confidential"

            val response = handler.handleGeneralError(IllegalStateException(secret))

            response.body.shouldNotBeNull().values.shouldForAll { value ->
                value shouldNotContain secret
            }
        }
    })
