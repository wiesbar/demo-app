package example.otp

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

internal class LoggingSmsService : SMSService {
    override fun sendOTP(
        userId: String,
        password: String,
    ) {
        logger.info { "Sending one time password to user '$userId'" }
    }
}
