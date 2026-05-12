package example.otp

internal class OTPService(
    private val sms: SMSService,
    private val otpGenerator: OTPGenerator,
    private val passwords: PasswordRepository,
) {
    fun generate(userId: String) {
        val otp = otpGenerator.generate()
        sms.sendOTP(userId, otp)
        passwords.store(userId, otp)
    }

    fun verify(
        userId: String,
        otp: String,
    ): Boolean = passwords.consumeAttempt(userId, otp)
}
