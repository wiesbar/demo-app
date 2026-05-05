package example.web

import example.calculator.InvalidArithmeticExpressionException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(InvalidArithmeticExpressionException::class)
    internal fun handleBadRequest(ex: InvalidArithmeticExpressionException): ResponseEntity<Map<String, String?>> {
        val errorBody =
            mapOf(
                "status" to "400",
                "error" to "Bad Request",
                "message" to ex.message,
            )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneralError(ex: Exception): ResponseEntity<Map<String, String?>> {
        val errorBody =
            mapOf(
                "status" to "500",
                "error" to "Internal Server Error",
                "message" to ex.message,
            )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody)
    }
}
