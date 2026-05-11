package example.otp

internal class InvalidOtpRequestException(
    message: String,
) : RuntimeException(message)
