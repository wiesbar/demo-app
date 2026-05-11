package example.otp

import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal class OTPService(
    private val sms: SMSService,
    private val otpGenerator: OTPGenerator = DefaultOTPGenerator(),
    private val passwords: PasswordRepository = DefaultPasswordRepository(),
) {
    fun generate(userId: String) {
        val otp = otpGenerator.generate()
        passwords.store(userId, otp)
        sms.sendOTP(userId, otp)
    }

    fun verify(
        userId: String,
        otp: String,
    ): Boolean = passwords.consumeAttempt(userId, otp)
}
