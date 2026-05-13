package example.web

import example.otp.InvalidOtpRequestException
import example.otp.OTPService
import example.otp.RateLimitedOperation
import example.otp.RateLimiter
import example.otp.acquireOrThrow
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("one-time-password")
@RequestMapping("/one-time-password")
class OtpController internal constructor(
    private val service: OTPService,
    private val rateLimiter: RateLimiter,
) {
    @PostMapping("/generate")
    fun generate(
        @RequestBody body: GenerateOtpRequest,
    ): ResponseEntity<Unit> =
        with(body) {
            validateUserId(userId)
            rateLimiter.acquireOrThrow(userId, RateLimitedOperation.GENERATE)
            service.generate(userId)
            ResponseEntity.noContent().build()
        }

    @PostMapping("/verify")
    fun verify(
        @RequestBody body: VerifyOtpRequest,
    ): ResponseEntity<Unit> =
        with(body) {
            validate()
            rateLimiter.acquireOrThrow(userId, RateLimitedOperation.VERIFY)
            val status = if (service.verify(userId, otp)) HttpStatus.NO_CONTENT else HttpStatus.UNAUTHORIZED
            ResponseEntity.status(status).build()
        }
}

private fun validateUserId(userId: String) {
    if (userId.isBlank()) throw InvalidOtpRequestException("userId must not be blank")
}

private fun VerifyOtpRequest.validate() {
    validateUserId(userId)
    if (otp.isBlank()) throw InvalidOtpRequestException("otp must not be blank")
}
