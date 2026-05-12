package example.otp

import io.kotest.matchers.collections.shouldContainExactly

internal class SMSServiceMock(
    private val onSend: (String, String) -> Unit = { _, _ -> },
) : SMSService {
    private val capturedPasswords = mutableListOf<Pair<String, String>>()

    override fun sendOTP(
        userId: String,
        password: String,
    ) {
        capturedPasswords.add(userId to password)
        onSend(userId, password)
    }

    fun assertSent(vararg expectedPasswords: Pair<String, String>) {
        capturedPasswords shouldContainExactly expectedPasswords.toList()
    }
}
