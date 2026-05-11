package example.web

data class GenerateOtpRequest(
    val userId: String,
)

data class VerifyOtpRequest(
    val userId: String,
    val otp: String,
)
