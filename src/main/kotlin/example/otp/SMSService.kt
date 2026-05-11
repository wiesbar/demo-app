package example.otp

internal interface SMSService {
    fun sendOTP(
        userId: String,
        password: String,
    )
}
