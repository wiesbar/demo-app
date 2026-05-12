package example.web

import example.calculator.InvalidArithmeticExpressionException
import example.catalog.InvalidProductException
import example.catalog.ProductNotFoundException
import example.otp.InvalidOtpRequestException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

private typealias ErrorBody = ResponseEntity<Map<String, String?>>

private val logger = KotlinLogging.logger {}

private const val GENERIC_ERROR_MESSAGE = "Internal Server Error"

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(InvalidArithmeticExpressionException::class)
    internal fun handleInvalidExpression(ex: InvalidArithmeticExpressionException): ErrorBody = badRequest(ex.message)

    @ExceptionHandler(InvalidProductException::class)
    internal fun handleInvalidProduct(ex: InvalidProductException): ErrorBody = badRequest(ex.message)

    @ExceptionHandler(InvalidOtpRequestException::class)
    internal fun handleInvalidOtpRequest(ex: InvalidOtpRequestException): ErrorBody = badRequest(ex.message)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(ex: HttpMessageNotReadableException): ErrorBody {
        val message = ex.mostSpecificCause.message ?: ex.message
        return badRequest(message)
    }

    @ExceptionHandler(ProductNotFoundException::class)
    internal fun handleNotFound(ex: ProductNotFoundException): ErrorBody {
        val body = mapOf("status" to "404", "error" to "Not Found", "message" to ex.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body)
    }

    @ExceptionHandler(CancellationException::class)
    fun handleCancellation(ex: CancellationException): Nothing = throw ex

    @ExceptionHandler(Exception::class)
    fun handleGeneralError(ex: Exception): ErrorBody {
        logger.error(ex) { "Unhandled exception reached GlobalExceptionHandler" }
        val body = mapOf("status" to "500", "error" to GENERIC_ERROR_MESSAGE, "message" to GENERIC_ERROR_MESSAGE)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body)
    }
}

private fun badRequest(message: String?): ErrorBody {
    val body = mapOf("status" to "400", "error" to "Bad Request", "message" to message)
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
}
