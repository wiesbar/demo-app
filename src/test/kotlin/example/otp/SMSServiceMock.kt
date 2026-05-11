package example.otp

import io.kotest.matchers.collections.shouldContainExactly

internal class SMSServiceMock : SMSService {
    private val capturedPasswords = mutableListOf<Pair<String, String>>()

    override fun sendOTP(
        userId: String,
        password: String,
    ) {
        capturedPasswords.add(userId to password)
    }

    fun assertSent(vararg expectedPasswords: Pair<String, String>) {
        capturedPasswords shouldContainExactly expectedPasswords.toList()
    }
}
